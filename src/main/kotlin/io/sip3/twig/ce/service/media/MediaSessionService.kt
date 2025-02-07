/*
 * Copyright 2018-2025 SIP3.IO, Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sip3.twig.ce.service.media

import com.mongodb.client.model.Filters
import io.sip3.twig.ce.domain.SessionRequest
import io.sip3.twig.ce.mongo.MongoClient
import io.sip3.twig.ce.service.media.domain.LegSession
import io.sip3.twig.ce.service.media.domain.MediaStatistic
import io.sip3.twig.ce.service.media.util.LegSessionUtil.createLegSession
import io.sip3.twig.ce.service.media.util.LegSessionUtil.generateLegId
import io.sip3.twig.ce.service.media.util.LegSessionUtil.generatePartyId
import io.sip3.twig.ce.service.media.util.MediaStatisticUtil.createMediaStatistic
import io.sip3.twig.ce.service.media.util.MediaStatisticUtil.updateMediaStatistic
import io.sip3.twig.ce.service.media.util.ReportUtil.splitReport
import io.sip3.twig.ce.util.firstOrNull
import io.sip3.twig.ce.util.merge
import mu.KotlinLogging
import org.bson.Document
import org.bson.conversions.Bson
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
open class MediaSessionService {

    private val logger = KotlinLogging.logger {}

    @Value("\${session.media.block-count:\${session.media.block_count:28}}")
    private var blockCount: Int = 28

    @Value("\${session.media.termination-timeout:\${session.media.termination_timeout:60000}}")
    private var terminationTimeout: Long = 60000

    @Autowired
    private lateinit var mongoClient: MongoClient

    open fun details(req: SessionRequest): List<Map<String, LegSession?>> {
        requireNotNull(req.createdAt) { "created_at" }
        requireNotNull(req.terminatedAt) { "terminated_at" }
        requireNotNull(req.callId) { "call_id" }

        val rtp = findLegSessions("rtp", req.createdAt!!, req.terminatedAt!!, req.callId!!)
        val rtcp = findLegSessions("rtcp", req.createdAt!!, req.terminatedAt!!, req.callId!!)

        return rtp.keys.plus(rtcp.keys)
            .map { mapOf("rtp" to rtp[it], "rtcp" to rtcp[it]) }
    }

    open fun dtmf(req: SessionRequest): Iterator<Document> {
        requireNotNull(req.createdAt) { "created_at" }
        requireNotNull(req.terminatedAt) { "terminated_at" }
        requireNotNull(req.callId) { "call_id" }

        return find("dtmf_index", req.createdAt!!, req.terminatedAt!!, req.callId!!)
    }

    open fun findLegSessions(
        source: String,
        createdAt: Long,
        terminatedAt: Long,
        callId: List<String>,
        withBlocks: Boolean = true,
    ): Map<String, LegSession> {
        val sessions = mutableMapOf<String, LegSession>()
        val reports = mutableListOf<Document>()

        // Create leg sessions from reports index
        find("rtpr_${source}_index", createdAt, terminatedAt, callId)
            .asSequence()
            .groupBy { generateLegId(it) }
            .forEach { (legId, documents) ->
                try {
                    sessions[legId] = createLegSession(documents.first(), blockCount)
                } catch (e: Exception) {
                    logger.error(e) { "MediaSessionService `createLegSession()` failed. LegId: $legId" }
                    logger.trace { "Documents: $documents" }
                }
            }

        // Add blocks to media sessions
        sessions.forEach { (legId, legSession) ->
            // Find Raw reports
            var legReports = reports.filter { generateLegId(it) == legId }

            if (legReports.isEmpty()) {
                find("rtpr_${source}_raw", legSession.createdAt, legSession.terminatedAt, listOf(legSession.callId))
                    .asSequence()
                    .forEach { document ->
                        document.getList("reports", Document::class.java)?.forEach { report ->
                            report.put("src_addr", document.getString("src_addr"))
                            report.put("src_port", document.getInteger("src_port"))
                            report.put("dst_addr", document.getString("dst_addr"))
                            report.put("dst_port", document.getInteger("dst_port"))
                            report.put("call_id", document.getString("call_id"))
                            reports.add(report)
                        } ?: reports.add(document)
                    }

                legReports = reports.filter { generateLegId(it) == legId }
            }

            if (withBlocks) {
                // Update Media Session leg session party
                legReports
                    .groupBy { generatePartyId(it) }
                    .forEach { (_, reports) ->
                        try {
                            updateMediaSession(source, legSession, reports.sortedBy { it.getLong("created_at") })
                        } catch (e: Exception) {
                            legSession.invalid = true
                            logger.error(e) { "MediaSessionService `updateMediaSession()` failed. LegId: $legId" }
                            logger.trace { "Reports: $reports" }
                        }
                    }

                sessions.values.forEach { session ->
                    session.`in`.iterator().merge(session.out.iterator(), null)
                        .firstOrNull { mediaSession ->
                            mediaSession.blocks.size > blockCount || mediaSession.blocks.any { it.jitter.max >= 10000 }
                        }
                        ?.let { session.invalid = true }
                }
            }
        }

        if (withBlocks) sessions.values.forEach { it.updateTimestamps() }

        return sessions
    }

    // TODO: Hardcoded mediaSession selection.
    open fun updateMediaSession(source: String, legSession: LegSession, reports: List<Document>) {
        val firstReport = reports.first()

        val isForward = if (source == "rtp") {
            firstReport.getString("src_addr") == legSession.srcAddr
        } else {
            firstReport.getString("src_addr") == legSession.dstAddr
        }

        val mediaSession = (if (isForward) legSession.out else legSession.`in`).firstOrNull() ?: return

        val blocks = ArrayList<MediaStatistic>(blockCount)
        val blockDuration = if (legSession.duration / blockCount > 0) {
            legSession.duration / blockCount
        } else {
            legSession.duration
        }

        var remainingDuration: Int
        var currentBlock = MediaStatistic()

        if (reports.first().getLong("created_at") == legSession.createdAt) {
            remainingDuration = blockDuration
        } else {
            val startDiff = (reports.first().getLong("created_at") - legSession.createdAt).toInt()
            repeat(startDiff / blockDuration) {
                blocks.add(MediaStatistic())
            }
            remainingDuration = blockDuration - (startDiff % blockDuration)
        }

        reports.forEach { report ->
            val reportDuration = report.getInteger("duration")
            when {
                reportDuration < remainingDuration -> {
                    updateMediaStatistic(currentBlock, report)
                    remainingDuration -= reportDuration
                }

                reportDuration > remainingDuration -> {
                    val chunks = mutableListOf<Document>()
                    splitReport(chunks, report, remainingDuration, blockDuration, blockCount)

                    val iterator = chunks.iterator()

                    updateMediaStatistic(currentBlock, iterator.next())
                    while (iterator.hasNext()) {
                        blocks.add(currentBlock)
                        currentBlock = createMediaStatistic(iterator.next())
                    }

                    remainingDuration = blockDuration - chunks.last().getInteger("duration")
                }

                reportDuration == remainingDuration -> {
                    updateMediaStatistic(currentBlock, report)
                    blocks.add(currentBlock)
                    currentBlock = MediaStatistic()
                    remainingDuration = blockDuration
                }
            }
        }

        if (currentBlock.packets.expected != 0 && blocks.size < blockCount) {
            blocks.add(currentBlock)
        }

        while (blocks.size < blockCount) {
            blocks.add(MediaStatistic())
        }

        mediaSession.blocks.addAll(blocks)

        // Calculate media session duration
        if (mediaSession.duration == 0) mediaSession.duration = blocks.sumOf { it.duration }

        // Set timestamps
        mediaSession.createdAt = firstReport.getLong("created_at")
        mediaSession.terminatedAt = mediaSession.createdAt + mediaSession.duration

        // Update codec list
        legSession.codecs.addAll(reports.map { LegSession.Codec(it.getString("codec"), it.getInteger("payload_type")) })
    }

    open fun find(prefix: String, createdAt: Long, terminatedAt: Long, callId: List<String>): Iterator<Document> {
        val filters = mutableListOf<Bson>().apply {
            add(Filters.gte("created_at", createdAt))
            add(Filters.lte("created_at", terminatedAt + terminationTimeout))
            add(Filters.`in`("call_id", callId))
        }

        return mongoClient.find(prefix, Pair(createdAt, terminatedAt + terminationTimeout), Filters.and(filters))
    }
}