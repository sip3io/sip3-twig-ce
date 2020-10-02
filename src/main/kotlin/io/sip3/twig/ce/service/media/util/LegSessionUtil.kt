/*
 * Copyright 2018-2020 SIP3.IO, Inc.
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

package io.sip3.twig.ce.service.media.util

import io.sip3.twig.ce.service.media.domain.LegSession
import io.sip3.twig.ce.service.media.domain.MediaSession
import org.bson.Document
import kotlin.math.max
import kotlin.math.min

object LegSessionUtil {

    fun generateLegId(report: Document): String {
        val callId = report.getString("call_id")

        val srcAddr = report.getString("src_addr")
        val srcPort = report.getInteger("src_port").let { port ->
            if (port % 2 == 0) { port } else { port - 1 }
        }

        val dstAddr = report.getString("dst_addr")
        val dstPort = report.getInteger("dst_port").let { port ->
            if (port % 2 == 0) { port } else { port - 1 }
        }

        return if (srcPort > dstPort) {
            "$callId:$srcAddr:$srcPort:$dstAddr:$dstPort"
        } else {
            "$callId:$dstAddr:$dstPort:$srcAddr:$srcPort"
        }
    }

    fun generatePartyId(report: Document): String {
        return "${report.getInteger("src_port")}:${report.getInteger("dst_port")}"
    }

    fun createLegSession(documents: List<Document>, blockCount: Int, source: String): LegSession {
        // Split to `first` and `second` reports
        val firstReport = documents.first()
        val firstPartyId = generatePartyId(firstReport)
        val (first, second) = documents.partition { generatePartyId(it) == firstPartyId }

        // Define timestamps
        val legCreatedAt =  min(first.first().getLong("started_at"),
                second.firstOrNull()?.getLong("started_at") ?: Long.MAX_VALUE)

        val legTerminatedAt = max(first.last().getLong("started_at") + first.last().getInteger("duration"),
                second.lastOrNull()?.let { it.getLong("started_at") + it.getInteger("duration") } ?: 0)


        return LegSession().apply {
            createdAt = legCreatedAt
            terminatedAt = legTerminatedAt

            duration = (legTerminatedAt - legCreatedAt).toInt()
            callId = firstReport.getString("call_id")
            codecs.add(LegSession.Codec(firstReport.getString("codec_name"), firstReport.getInteger("payload_type")))

            when (source) {
                "rtp" -> {
                    srcAddr = firstReport.getString("src_addr")
                    srcPort = firstReport.getInteger("src_port")
                    firstReport.getString("src_host")?.let { srcHost = it }
                    dstAddr = firstReport.getString("dst_addr")
                    dstPort = firstReport.getInteger("dst_port")
                    firstReport.getString("dst_host")?.let { dstHost = it }

                    this.out.add(createMediaSession(first, blockCount))

                    if (second.isNotEmpty()) {
                        this.`in`.add(createMediaSession(second, blockCount))
                    }
                }

                "rtcp" -> {
                    srcAddr = firstReport.getString("dst_addr")
                    srcPort = firstReport.getInteger("dst_port")
                    firstReport.getString("src_host")?.let { dstHost = it }
                    dstAddr = firstReport.getString("src_addr")
                    dstPort = firstReport.getInteger("src_port")
                    firstReport.getString("dst_host")?.let { srcHost = it }

                    this.`in`.add(createMediaSession(first, blockCount))

                    if (second.isNotEmpty()) {
                        this.out.add(createMediaSession(second, blockCount))
                    }
                }
            }
        }
    }

    private fun createMediaSession(reports: List<Document>, blockCount: Int): MediaSession {
        return MediaSession(blockCount).apply {
            createdAt = reports.first().getLong("started_at")
            terminatedAt = reports.map { it.getLong("started_at") + it.getInteger("duration") }.max()!!
            duration = (terminatedAt - createdAt).toInt()

            mos = reports.sumByDouble { it.getDouble("mos") } / reports.size
            rFactor = reports.sumByDouble { it.getDouble("r_factor") } / reports.size

            val reportPackets = reports.map { it.get("packets") as Document }
            packets.apply {
                expected = reportPackets.sumBy { it.getInteger("expected") }
                received = reportPackets.sumBy { it.getInteger("received") }
                rejected = reportPackets.sumBy { it.getInteger("rejected") }
                lost = expected - received
            }

            val reportJitter = reports.map { it.get("jitter") as Document }
            jitter.apply {
                reportJitter.map { it.getDouble("min") }.filter { it != 0.0 }.min()?.let { min = it }
                max = reportJitter.map { it.getDouble("max") }.max()!!
                avg = reportJitter.sumByDouble { it.getDouble("avg") } / reports.size
            }
        }
    }
}