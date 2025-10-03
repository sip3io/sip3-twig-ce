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
import io.sip3.twig.ce.service.media.util.LegSessionUtil.generateLegId
import io.sip3.twig.ce.service.media.util.LegSessionUtil.generatePartyId
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

    private fun findLegSessions(source: String, createdAt: Long, terminatedAt: Long, callId: List<String>): Map<String, LegSession> {
        val sessions = mutableMapOf<String, LegSession>()
        val reports = mutableListOf<Document>()

        // Create leg sessions from reports index
        find("rtpr_${source}_index", createdAt, terminatedAt, callId)
            .forEachRemaining { report ->
                val sessionId = generateLegId(report)
                sessions.getOrPut(sessionId) {
                    LegSession()
                }.add(report)
            }

        val createdAtFrom = sessions.values.minOfOrNull { it.createdAt } ?: createdAt
        val terminatedAtTo = sessions.values.maxOfOrNull { it.terminatedAt } ?: terminatedAt

        find("rtpr_${source}_raw", createdAtFrom, terminatedAtTo, callId)
            .forEachRemaining { document ->
                document.getList("reports", Document::class.java)?.forEach { report ->
                    report.put("src_addr", document.getString("src_addr"))
                    report.put("src_port", document.getInteger("src_port"))
                    report.put("dst_addr", document.getString("dst_addr"))
                    report.put("dst_port", document.getInteger("dst_port"))
                    report.put("call_id", document.getString("call_id"))
                    reports.add(report)
                } ?: reports.add(document)
            }

        // Add blocks to media sessions
        sessions.forEach { (legId, legSession) ->
            try {
                reports.filter { generateLegId(it) == legId }
                    .sortedBy { it.getLong("created_at") }
                    .let { legReports ->
                        legSession.`in`.forEach { mediaSessionReport ->
                            legReports.filter { generatePartyId(it, source) == mediaSessionReport.partyId }
                                .let { mediaSessionReport.add(it) }
                        }

                        legSession.`out`.forEach { mediaSessionReport ->
                            legReports.filter { generatePartyId(it, source) == mediaSessionReport.partyId }
                                .let { mediaSessionReport.add(it) }
                        }
                    }
            } catch (e: Exception) {
                legSession.invalid = true
                logger.error(e) { "MediaSessionService `updateSession()` failed. LegId: $legId" }
            }

            sessions.values.forEach { session ->
                session.`in`.iterator().merge(session.out.iterator(), null)
                    .firstOrNull { mediaSession ->
                        mediaSession.reports.any { it.jitter.max >= 10000 }
                    }
                    ?.let { session.invalid = true }
            }

        }

        sessions.values.forEach { it.updateTimestamps() }

        return sessions
    }

    open fun dtmf(req: SessionRequest): Iterator<Document> {
        requireNotNull(req.createdAt) { "created_at" }
        requireNotNull(req.terminatedAt) { "terminated_at" }
        requireNotNull(req.callId) { "call_id" }

        return find("dtmf_index", req.createdAt!!, req.terminatedAt!!, req.callId!!)
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