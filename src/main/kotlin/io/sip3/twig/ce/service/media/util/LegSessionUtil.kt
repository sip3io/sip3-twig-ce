/*
 * Copyright 2018-2021 SIP3.IO, Corp.
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

    fun createLegSession(document: Document, blockCount: Int): LegSession {
        // Define timestamps
        val legCreatedAt = document.getLong("created_at")
        val legTerminatedAt = document.getLong("terminated_at")

        return LegSession().apply {
            createdAt = legCreatedAt
            terminatedAt = legTerminatedAt

            duration = (legTerminatedAt - legCreatedAt).toInt()
            callId = document.getString("call_id")

            srcAddr = document.getString("src_addr")
            srcPort = document.getInteger("src_port")
            document.getString("src_host")?.let { srcHost = it }
            dstAddr = document.getString("dst_addr")
            dstPort = document.getInteger("dst_port")
            document.getString("dst_host")?.let { dstHost = it }

            val payloadType = document.getInteger("payload_type")
            val codecName = document.getString("codec") ?: "UNDEFINED($payloadType)"
            codecs.add(LegSession.Codec(codecName, payloadType))

            fillMediaSession(document, blockCount)
        }
    }

    private fun LegSession.fillMediaSession(document: Document, blockCount: Int) {
        // Get lists of values
        val directions = document.getList("direction", String::class.java)
        val reportPackets = document.get("packets", Document::class.java)

        val mos = document.getList("mos", Double::class.java)
        val rFactor = document.getList("mos", Double::class.java)
        val jitters = document.get("jitter", Document::class.java)

        directions.forEachIndexed { index, direction ->
            val mediaSession = MediaSession(blockCount)

            mediaSession.packets.apply {
                expected = reportPackets.getList("expected", Int::class.java)[index]
                received = reportPackets.getList("received", Int::class.java)[index]
                rejected = reportPackets.getList("rejected", Int::class.java)[index]
                lost = expected - received
            }

            mediaSession.mos = mos[index]
            mediaSession.rFactor = rFactor[index]

            mediaSession.jitter.apply {
                min = jitters.getList("min", Double::class.java)[index]
                max = jitters.getList("max", Double::class.java)[index]
                avg = jitters.getList("avg", Double::class.java)[index]
            }

            if (direction == "out") {
                out = mediaSession
            } else {
                `in` = mediaSession
            }
        }
    }
}