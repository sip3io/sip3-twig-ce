/*
 * Copyright 2018-2021 SIP3.IO, Inc.
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
        val dstAddr = report.getString("dst_addr")

        return if (srcAddr > dstAddr) {
            "$callId:$srcAddr:$dstAddr"
        } else {
            "$callId:$dstAddr:$srcAddr"
        }
    }

    fun generatePartyId(report: Document): String {
        return "${report.getString("src_addr")}:${report.getString("dst_addr")}"
    }

    fun createLegSession(documents: List<Document>, blockCount: Int): LegSession {
        // Split to `out` and `in` reports
        val firstReport = documents.first()
        val outPartyId = generatePartyId(firstReport)
        val (out, `in`) = documents.partition { generatePartyId(it) == outPartyId }

        // Define timestamps
        val legCreatedAt = min(
            out.first().getLong("started_at"),
            `in`.firstOrNull()?.getLong("started_at") ?: Long.MAX_VALUE
        )

        val legTerminatedAt = max(out.last().getLong("started_at") + out.last().getInteger("duration"),
            `in`.lastOrNull()?.let { it.getLong("started_at") + it.getInteger("duration") } ?: 0)


        return LegSession().apply {
            createdAt = legCreatedAt
            terminatedAt = legTerminatedAt

            duration = (legTerminatedAt - legCreatedAt).toInt()
            callId = firstReport.getString("call_id")

            srcAddr = firstReport.getString("src_addr")
            srcPort = firstReport.getInteger("src_port")
            firstReport.getString("src_host")?.let { srcHost = it }
            dstAddr = firstReport.getString("dst_addr")
            dstPort = firstReport.getInteger("dst_port")
            firstReport.getString("dst_host")?.let { dstHost = it }

            documents.forEach { document ->
                val payloadType = document.getInteger("payload_type")
                val codecName = document.getString("codec_name") ?: "UNDEFINED($payloadType)"
                codecs.add(LegSession.Codec(codecName, payloadType))
            }

            this.out.add(createMediaSession(out, blockCount))

            if (`in`.isNotEmpty()) {
                this.`in`.add(createMediaSession(`in`, blockCount))
            }
        }
    }

    private fun createMediaSession(reports: List<Document>, blockCount: Int): MediaSession {
        return MediaSession(blockCount).apply {
            createdAt = reports.first().getLong("started_at")
            terminatedAt = reports.map { it.getLong("started_at") + it.getInteger("duration") }.maxOrNull()!!
            duration = (terminatedAt - createdAt).toInt()

            val reportPackets = reports.map { it.get("packets") as Document }
            packets.apply {
                expected = reportPackets.sumBy { it.getInteger("expected") }
                received = reportPackets.sumBy { it.getInteger("received") }
                rejected = reportPackets.sumBy { it.getInteger("rejected") }
                lost = expected - received
            }

            reports.filter { it.getDouble("r_factor") > 0.0 }
                .takeIf { it.isNotEmpty() }
                ?.let { validReports ->
                    mos = validReports.sumByDouble { it.getDouble("mos") } / validReports.size
                    rFactor = validReports.sumByDouble { it.getDouble("r_factor") } / validReports.size

                    val reportJitter = validReports.map { it.get("jitter") as Document }
                    jitter.apply {
                        reportJitter.map { it.getDouble("min") }.filter { it != 0.0 }.minOrNull()?.let { min = it }
                        max = reportJitter.map { it.getDouble("max") }.maxOrNull()!!
                        avg = reportJitter.sumByDouble { it.getDouble("avg") } / validReports.size
                    }
                }
        }
    }
}