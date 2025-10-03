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

package io.sip3.twig.ce.service.media.domain

import org.bson.Document

open class MediaSession() : MediaStatistic() {

    var createdAt: Long = Long.MAX_VALUE
    var terminatedAt: Long = Long.MIN_VALUE

    val partyId: String
        get() = "$srcAddr:$srcPort:$dstAddr:$dstPort"

    lateinit var srcAddr: String
    var srcPort: Int = -1
    lateinit var dstAddr: String
    var dstPort: Int = -1
    val reports = sortedSetOf<Report>(compareBy { it.createdAt })

    fun add(documents: List<Document>) {
        documents.map { document ->
            Report().apply {
                createdAt = document.getLong("created_at")
                duration = document.getInteger("duration")
                terminatedAt = document.getLong("created_at") + duration

                mos = document.getDouble("mos")
                rFactor = document.getDouble("r_factor")

                packets.apply {
                    val reportPackets = document.get("packets") as Document
                    expected = reportPackets.getInteger("expected")
                    lost = reportPackets.getInteger("lost")
                    received = reportPackets.getInteger("received")
                    rejected = reportPackets.getInteger("rejected")
                }
                jitter.apply {
                    val reportJitter = document.get("jitter") as Document
                    min = reportJitter.getDouble("min")
                    max = reportJitter.getDouble("max")
                    avg = reportJitter.getDouble("avg")
                }
                ssrc = document.getLong("ssrc")
            }
        }.let {
            reports.addAll(it)
        }

        reports.firstOrNull()?.createdAt?.takeIf { it < createdAt }?.let {
            createdAt = it
        }
        reports.lastOrNull()?.terminatedAt?.takeIf { it > terminatedAt}?.let {
            terminatedAt = it
        }

        duration = (terminatedAt - createdAt).toInt()
    }

    override fun toString(): String {
        return "MediaSession(srcAddr=$srcAddr, srcPort=$srcPort, dst=$dstAddr, dstPort=$dstPort, createdAt=$createdAt, terminatedAt=$terminatedAt)"
    }

    class Report : MediaStatistic() {

        var createdAt: Long = Long.MAX_VALUE
        var terminatedAt: Long = Long.MAX_VALUE

        var ssrc: Long = -1L

        override fun toString(): String {
            return "Report(createdAt=$createdAt, terminatedAt=$terminatedAt, ssrc=$ssrc, mos=$mos, rFactor=$rFactor)"
        }
    }
}