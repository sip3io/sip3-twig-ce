/*
 * Copyright 2018-2026 SIP3.IO, Corp.
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

package io.sip3.twig.ce.service

import com.mongodb.client.model.Filters.*
import gov.nist.javax.sip.message.SIPMessage
import gov.nist.javax.sip.parser.StringMsgParser
import io.github.oshai.kotlinlogging.KotlinLogging
import io.pkts.PcapOutputStream
import io.pkts.buffer.Buffers
import io.pkts.frame.PcapGlobalHeader
import io.pkts.packet.PacketFactory
import io.sip3.commons.ProtocolCodes
import io.sip3.twig.ce.domain.SessionRequest
import io.sip3.twig.ce.mongo.MongoClient
import io.sip3.twig.ce.service.host.HostService
import io.sip3.twig.ce.util.*
import org.bson.Document
import org.bson.conversions.Bson
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

abstract class SessionService {

    protected val logger = KotlinLogging.logger {}

    companion object {

        val CREATED_AT = compareBy<Document> { document -> document.getLong("created_at") }

        val CREATED_AT_WITH_NANOS = compareBy<Document>(
            { document -> document.getLong("created_at") },
            { document -> document.getInteger("nanos") ?: 0 }
        )

        // RFC 3551 static RTP payload types supported for WAV export
        private const val RTP_PAYLOAD_TYPE_PCMU = 0
        private const val RTP_PAYLOAD_TYPE_PCMA = 8
        private const val RTP_SAMPLE_RATE = 8000f
    }

    @Autowired
    protected lateinit var mongoClient: MongoClient

    @Autowired
    protected lateinit var hostService: HostService

    @Value("\${session.show-retransmits:\${session.show_retransmits:true}}")
    protected var showRetransmits: Boolean = true

    @Value("\${session.ignore-nanos:\${session.ignore_nanos:true}}")
    protected var ignoreNanos: Boolean = true

    @Value("\${session.media.termination-timeout:\${session.media.termination_timeout:60000}}")
    private var terminationTimeout: Long = 60000

    abstract fun findInRawBySessionRequest(req: SessionRequest): Iterator<Document>

    open fun details(req: SessionRequest): List<Document> {
        val legs = mutableListOf<Document>()

        findInRawBySessionRequest(req).asSequence()
            .filter { document ->
                document.getString("raw_data").startsWith(req.method?.first()!!)
            }
            .sortedWith(if (ignoreNanos) CREATED_AT else CREATED_AT_WITH_NANOS)
            .groupBy { document -> "${document.getString("src_addr")}:${document.getString("dst_addr")}" }
            .forEach { (_, documents) ->
                val document = documents.firstOrNull { it.getBoolean("parsed") != false } ?: return@forEach
                legs.add(Document().apply {
                    // created_at
                    put("created_at", document.getLong("created_at"))
                    // src_addr, src_port, src_host
                    put("src_addr", document.getString("src_addr"))
                    put("src_port", document.getInteger("src_port"))
                    document.getString("src_host")?.let { put("src_host", it) }
                    // dst_addr, dst_port, dst_host
                    put("dst_addr", document.getString("dst_addr"))
                    put("dst_port", document.getInteger("dst_port"))
                    document.getString("dst_host")?.let { put("dst_host", it) }
                    // raw_data
                    val rawData = document.getString("raw_data")
                    var message: SIPMessage? = null
                    try {
                        message = StringMsgParser().parseSIPMessage(rawData.toByteArray(Charsets.ISO_8859_1), true, false, null)
                    } catch (e: Exception) {
                        logger.error(e) { "StringMsgParser 'parseSIPMessage()' failed." }
                    }
                    message?.let {
                        put("call_id", message.callId())
                        put("request_uri", message.requestUri())
                        put("from_uri", message.fromUri())
                        put("to_uri", message.toUri())
                    }
                })
            }

        return legs
    }

    open fun content(req: SessionRequest): List<Document> {
        val messages = findInRawBySessionRequest(req).asSequence()
            .sortedWith(if (ignoreNanos) CREATED_AT else CREATED_AT_WITH_NANOS)
            .groupBy { document -> "${document.getString("src_addr")}:${document.getString("dst_addr")}:${document.getString("raw_data")}" }
            .flatMap { (_, documents) ->
                if (showRetransmits) {
                    documents
                } else {
                    listOf(documents.first())
                }
            }
            .map { document ->
                return@map Document().apply {
                    // created_at
                    put("created_at", document.getLong("created_at"))
                    // src_addr, src_port, src_host
                    put("src_addr", document.getString("src_addr"))
                    put("src_port", document.getInteger("src_port"))
                    document.getString("src_host")?.let { put("src_host", it) }
                    // dst_addr, dst_port, dst_host
                    put("dst_addr", document.getString("dst_addr"))
                    put("dst_port", document.getInteger("dst_port"))
                    document.getString("dst_host")?.let { put("dst_host", it) }

                    // raw_data
                    val rawData = document.getString("raw_data")
                    val parsed = document.getBoolean("parsed") != false
                    if (parsed) {
                        try {
                            StringMsgParser().parseSIPMessage(rawData.toByteArray(Charsets.ISO_8859_1), true, false, null)?.let { message ->
                                put("transaction_id", message.transactionId())
                                putAll(extendedParamsFrom(message))
                            }
                        } catch (e: Exception) {
                            logger.error(e) { "StringMsgParser 'parseSIPMessage()' failed." }
                        }
                    }

                    put("parsed", parsed)
                    putIfAbsent("raw_data", rawData)
                }
            }
            .toList()

        return messages
    }

    open fun pcap(req: SessionRequest): ByteArrayOutputStream {
        val os = ByteArrayOutputStream()

        PcapOutputStream.create(PcapGlobalHeader.createDefaultHeader(), os).use { pos ->
            IteratorUtil.merge(findInRawBySessionRequest(req), findRecInRawBySessionRequest(req))
                .asSequence()
                .sortedWith(if (ignoreNanos) CREATED_AT else CREATED_AT_WITH_NANOS)
                .forEach { document ->
                    val raw = document.getString("raw_data").toByteArray(Charsets.ISO_8859_1)

                    val packet = PacketFactory.getInstance().transportFactory
                        .createUDP(document.getLong("created_at"), Buffers.wrap(raw))

                    packet.destinationIP = document.getString("dst_addr")
                    packet.sourceIP = document.getString("src_addr")
                    packet.destinationPort = document.getInteger("dst_port")
                    packet.sourcePort = document.getInteger("src_port")
                    packet.reCalculateChecksum()

                    pos.write(packet)
                }
        }

        return os
    }

    open fun wav(req: SessionRequest): ByteArrayOutputStream {
        val allPackets = findRecInRawBySessionRequest(req).asSequence().toList()
        val rtpPackets = allPackets.filter { document -> document.getInteger("type") == ProtocolCodes.RTP.toInt() }
        val matchingPackets = rtpPackets.filter { document -> matchesRequestedAttributes(document, req) }
        val frames = matchingPackets.mapNotNull { document -> rtpFrameFrom(document) }
            // The same media relay is often recorded on both its ingress and egress hop, so the
            // same RTP packet (same SSRC/seq/timestamp) can appear under different address pairs.
            .distinctBy { frame -> Triple(frame.ssrc, frame.seqNumber, frame.timestamp) }

        // TEMP DIAGNOSTIC: remove once the "No RTP packets found" report is resolved.
        logger.info {
            "wav() diag: request=$req, total_packets=${allPackets.size}, rtp_packets=${rtpPackets.size}, " +
                "matching_attributes=${matchingPackets.size}, parsed_frames=${frames.size}, " +
                "distinct_src_addr=${allPackets.mapNotNull { it.getString("src_addr") }.distinct()}, " +
                "distinct_src_host=${allPackets.mapNotNull { it.getString("src_host") }.distinct()}, " +
                "distinct_dst_addr=${allPackets.mapNotNull { it.getString("dst_addr") }.distinct()}, " +
                "distinct_dst_host=${allPackets.mapNotNull { it.getString("dst_host") }.distinct()}"
        }

        if (frames.isEmpty()) {
            throw IllegalStateException("No RTP packets found for request: $req")
        }

        val payloadType = frames.groupingBy { it.payloadType }
            .eachCount()
            .maxByOrNull { it.value }!!
            .key

        val sourceEncoding = when (payloadType) {
            RTP_PAYLOAD_TYPE_PCMU -> AudioFormat.Encoding.ULAW
            RTP_PAYLOAD_TYPE_PCMA -> AudioFormat.Encoding.ALAW
            else -> throw UnsupportedOperationException("Unsupported RTP payload type for WAV export: $payloadType")
        }
        val sourceFormat = AudioFormat(sourceEncoding, RTP_SAMPLE_RATE, 8, 1, 1, RTP_SAMPLE_RATE, false)
        val targetFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, RTP_SAMPLE_RATE, 16, 1, 2, RTP_SAMPLE_RATE, false)

        // Each call leg (SSRC) becomes its own channel, so both parties stay intelligible and
        // time-aligned instead of being spliced together sequentially. Grouping by SSRC (rather
        // than by address pair) also merges an on-path relay's ingress/egress hops of the same
        // leg back into one stream instead of treating them as two separate legs.
        val callStartedAt = frames.minOf { it.createdAt }
        val channels = frames.filter { it.payloadType == payloadType }
            .groupBy { it.ssrc }
            .values
            .sortedByDescending { it.size }
            .take(2)
            .map { streamFrames -> pcmSamplesFor(streamFrames, callStartedAt, sourceFormat, targetFormat) }

        val sampleCount = channels.maxOf { it.size }
        val channelCount = channels.size

        val pcm = ByteArray(sampleCount * channelCount * 2)
        for (i in 0 until sampleCount) {
            for (c in channels.indices) {
                val sample = channels[c].getOrElse(i) { 0 }
                val pos = (i * channelCount + c) * 2
                pcm[pos] = (sample.toInt() and 0xFF).toByte()
                pcm[pos + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }
        }

        val format = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, RTP_SAMPLE_RATE, 16, channelCount, channelCount * 2, RTP_SAMPLE_RATE, false)

        val os = ByteArrayOutputStream()
        AudioInputStream(ByteArrayInputStream(pcm), format, sampleCount.toLong()).use { audioInputStream ->
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, os)
        }

        return os
    }

    // A rec_raw packet only makes it into the WAV if it matches every requested attribute (unset
    // attributes are not constrained). `srcAddr`/`dstAddr` accept either addresses or host names
    // (mirroring `legFilter`), while `srcHost`/`dstHost` match the host field directly.
    private fun matchesRequestedAttributes(document: Document, req: SessionRequest): Boolean {
        return matchesHostOrAddr(document, "src", req.srcAddr) &&
            matchesHost(document, "src", req.srcHost) &&
            matchesHostOrAddr(document, "dst", req.dstAddr) &&
            matchesHost(document, "dst", req.dstHost)
    }

    private fun matchesHostOrAddr(document: Document, prefix: String, values: List<String>?): Boolean {
        if (values.isNullOrEmpty()) return true

        val (hosts, ips) = values.partition { hostService.findByNameIgnoreCase(it) != null }
        return document.getString("${prefix}_host") in hosts || document.getString("${prefix}_addr") in ips
    }

    private fun matchesHost(document: Document, prefix: String, values: List<String>?): Boolean {
        if (values.isNullOrEmpty()) return true
        return document.getString("${prefix}_host") in values
    }

    // Decodes a single call leg into a silence-padded PCM16 timeline: each frame is placed at the
    // sample position implied by its RTP timestamp (relative to the leg's first frame), and the leg
    // itself is anchored to `callStartedAt` so both legs of the call stay time-aligned with each other.
    private fun pcmSamplesFor(
        frames: List<RtpFrame>,
        callStartedAt: Long,
        sourceFormat: AudioFormat,
        targetFormat: AudioFormat
    ): ShortArray {
        val ordered = frames.sortedBy { it.createdAt }
        val first = ordered.first()
        val streamOffset = (((first.createdAt - callStartedAt) * sourceFormat.sampleRate) / 1000).toInt()

        val decoded = ordered.map { frame -> (frame.timestamp - first.timestamp) to pcm16Of(frame.payload, sourceFormat, targetFormat) }
        val length = streamOffset + decoded.maxOf { (offset, samples) -> offset + samples.size }

        val buffer = ShortArray(length.coerceAtLeast(0))
        decoded.forEach { (offset, samples) ->
            val base = streamOffset + offset
            samples.forEachIndexed { i, sample ->
                val index = base + i
                if (index in buffer.indices) buffer[index] = sample
            }
        }

        return buffer
    }

    private fun pcm16Of(payload: ByteArray, sourceFormat: AudioFormat, targetFormat: AudioFormat): ShortArray {
        val pcmBytes = AudioSystem.getAudioInputStream(
            targetFormat,
            AudioInputStream(ByteArrayInputStream(payload), sourceFormat, payload.size.toLong())
        ).use { it.readAllBytes() }

        return ShortArray(pcmBytes.size / 2) { i ->
            (((pcmBytes[i * 2 + 1].toInt() and 0xFF) shl 8) or (pcmBytes[i * 2].toInt() and 0xFF)).toShort()
        }
    }

    private data class RtpFrame(
        val ssrc: Int,
        val createdAt: Long,
        val seqNumber: Int,
        val timestamp: Int,
        val payloadType: Int,
        val payload: ByteArray
    )

    // Parses a raw RTP packet per RFC 3550: skips the fixed header, CSRC list and extension header,
    // and strips padding, returning the frame's routing/ordering metadata along with its payload.
    private fun rtpFrameFrom(document: Document): RtpFrame? {
        val raw = document.getString("raw_data").toByteArray(Charsets.ISO_8859_1)
        if (raw.size < 12) return null

        val firstByte = raw[0].toInt()
        if ((firstByte ushr 6) and 0x03 != 2) return null

        val hasPadding = (firstByte ushr 5) and 0x01 == 1
        val hasExtension = (firstByte ushr 4) and 0x01 == 1
        val csrcCount = firstByte and 0x0F
        val payloadType = raw[1].toInt() and 0x7F
        val seqNumber = ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
        val timestamp = ((raw[4].toInt() and 0xFF) shl 24) or ((raw[5].toInt() and 0xFF) shl 16) or
            ((raw[6].toInt() and 0xFF) shl 8) or (raw[7].toInt() and 0xFF)
        val ssrc = ((raw[8].toInt() and 0xFF) shl 24) or ((raw[9].toInt() and 0xFF) shl 16) or
            ((raw[10].toInt() and 0xFF) shl 8) or (raw[11].toInt() and 0xFF)

        var offset = 12 + csrcCount * 4
        if (offset > raw.size) return null

        if (hasExtension) {
            if (offset + 4 > raw.size) return null
            val extLength = ((raw[offset + 2].toInt() and 0xFF) shl 8) or (raw[offset + 3].toInt() and 0xFF)
            offset += 4 + extLength * 4
            if (offset > raw.size) return null
        }

        var end = raw.size
        if (hasPadding) {
            end -= raw[raw.size - 1].toInt() and 0xFF
        }
        if (end <= offset) return null

        return RtpFrame(ssrc, document.getLong("created_at"), seqNumber, timestamp, payloadType, raw.copyOfRange(offset, end))
    }

    open fun stash(req: SessionRequest) {
        throw UnsupportedOperationException("Stash is not supported in CE version")
    }

    open fun findRecInRawBySessionRequest(req: SessionRequest): Iterator<Document> {
        requireNotNull(req.createdAt) { "created_at" }
        requireNotNull(req.terminatedAt) { "terminated_at" }
        requireNotNull(req.callId) { "call_id" }

        val filters = mutableListOf<Bson>().apply {
            add(gte("created_at", req.createdAt!!))
            add(lte("created_at", req.terminatedAt!! + terminationTimeout))
            add(`in`("call_id", req.callId!!))

            if (req.srcAddr != null && req.dstAddr != null) {
                add(legFilter(req.srcAddr!!, req.dstAddr!!))
            }
        }

        return mongoClient.find("rec_raw", Pair(req.createdAt!!, req.terminatedAt!! + terminationTimeout), and(filters))
            .asSequence()
            .flatMap { document ->
                document.getList("packets", Document::class.java)
                    .filter { it.getString("raw_data").length > 12 }
                    .map { packet ->
                        packet.put("src_addr", document.getString("src_addr"))
                        packet.put("src_port", document.getInteger("src_port"))
                        packet.put("dst_addr", document.getString("dst_addr"))
                        packet.put("dst_port", document.getInteger("dst_port"))

                        return@map packet
                    }
            }
            .iterator()
    }

    open fun legFilter(srcAddr: List<String>, dstAddr: List<String>): Bson {
        val (srcHosts, srcIps) = srcAddr.partition { hostService.findByNameIgnoreCase(it) != null }
        val (dstHosts, dstIps) = dstAddr.partition { hostService.findByNameIgnoreCase(it) != null }


        return or(
            and(
                hostOrAddrFilter("src", srcHosts, srcIps),
                hostOrAddrFilter("dst", dstHosts, dstIps)
            ),
            and(
                hostOrAddrFilter("src", dstHosts, dstIps),
                hostOrAddrFilter("dst", srcHosts, srcIps)
            )
        )
    }

    open fun hostOrAddrFilter(prefix: String, hosts: List<String>, ips: List<String>): Bson {
        val filters = mutableListOf<Bson>().apply {
            if (hosts.isNotEmpty()) add(`in`("${prefix}_host", hosts))
            if (ips.isNotEmpty()) add(`in`("${prefix}_addr", ips))
        }

        return if (filters.size == 1) {
            filters.first()
        } else {
            or(filters)
        }
    }

    open fun extendedParamsFrom(message: SIPMessage): Map<String, Any> {
        return emptyMap()
    }
}