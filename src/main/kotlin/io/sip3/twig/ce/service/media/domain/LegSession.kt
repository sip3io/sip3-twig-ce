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
import kotlin.math.max
import kotlin.math.min

open class LegSession {

    var createdAt: Long = Long.MAX_VALUE
    var terminatedAt: Long = Long.MAX_VALUE
    var duration: Int = 0

    lateinit var callId: String

    lateinit var srcAddr: String
    var srcPort: Int = 0
    var srcHost: String? = null
    lateinit var dstAddr: String
    var dstPort: Int = 0
    var dstHost: String? = null

    val codecs = mutableSetOf<Codec>()

    val out = mutableListOf<MediaSession>()
    val `in` = mutableListOf<MediaSession>()

    var invalid: Boolean = false

    fun add(document: Document) {
        val sessionCreatedAt = document.getLong("created_at")
        val sessionTerminatedAt = document.getLong("terminated_at")

        if (createdAt == Long.MAX_VALUE) {
            createdAt = sessionCreatedAt
            terminatedAt = sessionTerminatedAt

            duration = (sessionTerminatedAt - sessionCreatedAt).toInt()
            callId = document.getString("call_id")

            srcAddr = document.getString("src_addr")
            srcPort = document.getInteger("src_port")
            document.getString("src_host")?.let { srcHost = it }

            dstAddr = document.getString("dst_addr")
            dstPort = document.getInteger("dst_port")
            document.getString("dst_host")?.let { dstHost = it }

            val payloadTypes = document.get("payload_type") as List<Int>
            val codecNames = document.getList("codec", String::class.java)
            payloadTypes.forEachIndexed { index, payloadType ->
                codecs.add(Codec(codecNames.getOrElse(index) { "UNDEFINED($payloadType)" }, payloadType))
            }
        }

        // Get lists of values
        val directions = document.getList("direction", String::class.java)
        val reportPackets = document.get("packets", Document::class.java)

        val mos = document.get("mos") as List<*>
        val rFactor = document.get("r_factor") as List<*>
        val jitters = document.get("jitter", Document::class.java)


        directions.forEachIndexed { index, direction ->
            val mediaSession = MediaSession()

            mediaSession.packets.apply {
                expected = (reportPackets.get("expected") as List<*>)[index] as Int
                received = (reportPackets.get("received") as List<*>)[index] as Int
                rejected = (reportPackets.get("rejected") as List<*>)[index] as Int
                lost = expected - received
            }

            mediaSession.mos = mos[index] as Double
            mediaSession.rFactor = rFactor[index] as Double

            mediaSession.jitter.apply {
                min = (jitters.get("min") as List<*>)[index] as Double
                max = (jitters.get("max") as List<*>)[index] as Double
                avg = (jitters.get("avg") as List<*>)[index] as Double
            }

            if (direction == "out") {
                mediaSession.srcAddr = srcAddr
                mediaSession.srcPort = srcPort
                mediaSession.dstAddr = dstAddr
                mediaSession.dstPort = dstPort

                out.add(mediaSession)
            } else {
                mediaSession.srcAddr = dstAddr
                mediaSession.srcPort = dstPort
                mediaSession.dstAddr = srcAddr
                mediaSession.dstPort = srcPort

                `in`.add(mediaSession)
            }
        }
    }

    fun updateTimestamps() {
        createdAt = min(
            out.minOfOrNull { it.createdAt } ?: Long.MAX_VALUE,
            `in`.minOfOrNull { it.createdAt } ?: Long.MAX_VALUE
        )
        terminatedAt = max(
            out.maxOfOrNull { it.terminatedAt } ?: Long.MIN_VALUE,
            `in`.maxOfOrNull { it.terminatedAt } ?: Long.MIN_VALUE
        )
    }

    override fun toString(): String {
        return "LegSession(createdAt=$createdAt, terminatedAt=$terminatedAt, srcAddr=$srcAddr, srcPort=$srcPort, dstAddr=$dstAddr, dstPort=$dstPort, in=$`in`, out=$out)"
    }

    data class Codec(val name: String, val payloadType: Int) {

        override fun equals(other: Any?): Boolean {
            return this.name  == (other as? Codec)?.name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }
    }
}