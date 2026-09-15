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

import gov.nist.javax.sip.message.MessageFactoryImpl
import gov.nist.javax.sip.parser.StringMsgParser
import io.pkts.Pcap
import io.pkts.packet.Packet
import io.pkts.packet.sip.impl.PreConditions.assertNotNull
import io.sip3.twig.ce.domain.Host
import io.sip3.twig.ce.domain.SessionRequest
import io.sip3.twig.ce.mongo.MongoClient
import io.sip3.twig.ce.service.host.HostService
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.stereotype.Component
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.io.ByteArrayInputStream
import java.util.*

@SpringBootTest(classes = [TestSessionService::class, MongoClient::class])
class SessionServiceTest {

    init {
        StringMsgParser.setComputeContentLengthFromMessage(true)
        MessageFactoryImpl().setDefaultContentEncodingCharset(Charsets.ISO_8859_1.name())
    }


    companion object {

        val NOW = System.currentTimeMillis()

        // Register 1 (1/2)
        val REGISTER_1 = Document().apply {
            put("created_at", NOW)
            put("src_addr", "192.168.10.123")
            put("src_port", 55399)
            put("dst_addr", "192.168.10.5")
            put("dst_port", 5060)
            put("dst_host", "pbx")
            put(
                "raw_data", """
                        REGISTER sip:192.168.10.5:5060 SIP/2.0
                        Via: SIP/2.0/UDP 192.168.10.123:55399;branch=z9hG4bK-d8754z-240d73239a6da57b-1---d8754z-;rport
                        Max-Forwards: 70
                        Contact: <sip:1010@192.168.10.123:55399;rinstance=13bf343a521442b5>
                        To: "1010"<sip:1010@192.168.10.5:5060>
                        From: "1010"<sip:1010@192.168.10.5:5060>;tag=bd285f07
                        Call-ID: call-id-1
                        CSeq: 143 REGISTER
                        Expires: 120
                        Allow: INVITE, ACK, CANCEL, OPTIONS, BYE, REGISTER, SUBSCRIBE, NOTIFY, REFER, INFO, MESSAGE
                        Supported: replaces
                        User-Agent: 3CXPhone 6.0.26523.0
                        Authorization: Digest username="1010",realm="asterisk",nonce="1589932693/afdc97bf8bb891c6c3c8072baeb2d3d6",uri="sip:192.168.10.5:5060",response="1337bca977875e158b7e1b96094521ee",cnonce="557bd23e12dc3db950db3c7e77aa91ad",nc=00000002,qop=auth,algorithm=md5,opaque="5b7b35877f215628"
                        Content-Length: 0

                    """.trimIndent()
            )
        }

        // Register 1 (2/2)
        val REGISTER_2 = Document().apply {
            put("created_at", NOW + 1)
            put("src_addr", "192.168.10.5")
            put("src_port", 5060)
            put("src_host", "pbx")
            put("dst_addr", "192.168.10.123")
            put("dst_port", 55399)
            put(
                "raw_data", """
                        SIP/2.0 200 OK
                        Via: SIP/2.0/UDP 192.168.10.123:55399;rport=55399;received=192.168.10.123;branch=z9hG4bK-d8754z-ef77c05e05556d61-1---d8754z-
                        Call-ID: call-id-1
                        From: "1010" <sip:1010@192.168.10.5>;tag=bd285f07
                        To: "1010" <sip:1010@192.168.10.5>;tag=z9hG4bK-d8754z-ef77c05e05556d61-1---d8754z-
                        CSeq: 144 REGISTER
                        Date: Wed, 20 May 2020 00:00:01 GMT
                        Contact: <sip:1010@192.168.10.123:55399;rinstance=13bf343a521442b5>;expires=119
                        Expires: 120
                        Server: FPBX-14.0.13.23(13.29.2)
                        Content-Length:  0
                        
                        
                    """.trimIndent()
            )
        }

        // Register 2 (1/2)
        val REGISTER_3 = Document().apply {
            put("created_at", NOW + 20)
            put("src_addr", "192.168.10.234")
            put("src_port", 33456)
            put("dst_addr", "192.168.10.5")
            put("dst_port", 5060)
            put("dst_host", "pbx")
            put(
                "raw_data", """
                        REGISTER sip:192.168.10.5:5060 SIP/2.0
                        Via: SIP/2.0/UDP 192.168.10.234:33456;branch=z9hG4bK-d8754z-240d73239a6da57b-1---d8754z-;rport
                        Max-Forwards: 70
                        Contact: <sip:2020@192.168.10.234:33456;rinstance=13bf343a521442b5>
                        To: "2020"<sip:2020@192.168.10.5:5060>
                        From: "2020"<sip:2020@192.168.10.5:5060>;tag=bd285f07
                        Call-ID: call-id-2
                        CSeq: 143 REGISTER
                        Expires: 120
                        Allow: INVITE, ACK, CANCEL, OPTIONS, BYE, REGISTER, SUBSCRIBE, NOTIFY, REFER, INFO, MESSAGE
                        Supported: replaces
                        User-Agent: 3CXPhone 6.0.26523.0
                        Authorization: Digest username="2020",realm="asterisk",nonce="1589932693/afdc97bf8bb891c6c3c8072baeb2d3d6",uri="sip:192.168.10.5:5060",response="1337bca977875e158b7e1b96094521ee",cnonce="557bd23e12dc3db950db3c7e77aa91ad",nc=00000002,qop=auth,algorithm=md5,opaque="5b7b35877f215628"
                        Content-Length: 0
                        
                    """.trimIndent().trimIndent()
            )
        }

        // Register 2 (2/2)
        val REGISTER_4 = Document().apply {
            put("created_at", NOW + 40)
            put("src_addr", "192.168.10.5")
            put("src_port", 5060)
            put("src_host", "pbx")
            put("dst_addr", "192.168.10.234")
            put("dst_port", 33456)
            put(
                "raw_data", """
                        SIP/2.0 200 OK
                        Via: SIP/2.0/UDP 192.168.10.234:33456;rport=33456;received=192.168.10.234;branch=z9hG4bK-d8754z-ef77c05e05556d61-1---d8754z-
                        Call-ID: call-id-2
                        From: "2020" <sip:2020@192.168.10.5>;tag=bd285f07
                        To: "2020" <sip:2020@192.168.10.5>;tag=z9hG4bK-d8754z-ef77c05e05556d61-1---d8754z-
                        CSeq: 144 REGISTER
                        Date: Wed, 20 May 2020 00:00:01 GMT
                        Contact: <sip:1010@192.168.10.234:33456;rinstance=13bf343a521442b5>;expires=119
                        Expires: 120
                        Server: FPBX-14.0.13.23(13.29.2)
                        Content-Length: 0
                        
                    """.trimIndent()
            )
        }

        val DOCUMENTS = listOf(REGISTER_1, REGISTER_1, REGISTER_1, REGISTER_2, REGISTER_3, REGISTER_4)
    }

    @MockitoBean
    private lateinit var hostService: HostService

    @Autowired
    private lateinit var service: TestSessionService

    @Test
    fun `Validate 'details()' method`() {
        val request = SessionRequest(
            createdAt = NOW,
            terminatedAt = NOW + 900000,
            method = listOf("REGISTER"),
            callId = listOf("call-id-1", "call-id-2")
        )

        // Execute
        val result = service.details(request)

        // Assert
        assertEquals(2, result.size)

        // Assert Registration 1
        val leg1 = result.first()
        assertEquals(REGISTER_1.getString("src_addr"), leg1.getString("src_addr"))
        assertEquals(REGISTER_1.getInteger("src_port"), leg1.getInteger("src_port"))
        assertEquals(REGISTER_1.getString("dst_addr"), leg1.getString("dst_addr"))
        assertEquals(REGISTER_1.getInteger("dst_port"), leg1.getInteger("dst_port"))
        assertEquals(REGISTER_1.getString("dst_host"), leg1.getString("dst_host"))

        assertEquals("call-id-1", leg1.getString("call_id"))
        assertNotNull(leg1.getString("request_uri"))
        assertNotNull(leg1.getString("from_uri"))
        assertNotNull(leg1.getString("to_uri"))

        // Assert Registration 2
        val leg2 = result.last()
        assertEquals(REGISTER_3.getString("src_addr"), leg2.getString("src_addr"))
        assertEquals(REGISTER_3.getInteger("src_port"), leg2.getInteger("src_port"))
        assertEquals(REGISTER_3.getString("dst_addr"), leg2.getString("dst_addr"))
        assertEquals(REGISTER_3.getInteger("dst_port"), leg2.getInteger("dst_port"))
        assertEquals(REGISTER_3.getString("dst_host"), leg2.getString("dst_host"))

        assertEquals("call-id-2", leg2.getString("call_id"))
        assertNotNull(leg2.getString("request_uri"))
        assertNotNull(leg2.getString("from_uri"))
        assertNotNull(leg2.getString("to_uri"))
    }

    @Test
    fun `Validate 'content()' method with retransmits`() {
        // Init
        val request = SessionRequest(
            createdAt = NOW,
            terminatedAt = NOW + 900000,
            method = listOf("REGISTER"),
            callId = listOf("call-id-1")
        )

        // Execute
        val messages = service.content(request)

        // Assert
        assertEquals(DOCUMENTS.size, messages.size)

        DOCUMENTS.forEachIndexed { index, document ->
            messages[index].apply {
                assertEquals(document.getString("src_addr"), getString("src_addr"))
                assertEquals(document.getInteger("src_port"), getInteger("src_port"))
                assertEquals(document.getString("dst_addr"), getString("dst_addr"))
                assertEquals(document.getInteger("dst_port"), getInteger("dst_port"))
                assertEquals(document.getString("dst_host"), getString("dst_host"))

                assertEquals(document.getString("raw_data"), getString("raw_data"))

            }
        }
    }

    @Test
    fun `Validate 'content()' method with retransmits2`() {
        `when`(hostService.findByNameIgnoreCase("host_1")).thenReturn(Host(null, "host_1", emptyList()))
        `when`(hostService.findByNameIgnoreCase("host_2")).thenReturn(Host(null, "host_2", emptyList()))
        println(service.legFilter(listOf("host_1"), listOf("host_2")))
    }

    @Test
    fun `Validate 'pcap()' method with retransmits`() {
        // Init
        val request = SessionRequest(
            createdAt = NOW,
            terminatedAt = NOW + 900000,
            method = listOf("REGISTER"),
            callId = listOf("call-id-1", "call-id-2")
        )

        // Execute
        val pcapOutputStream = service.pcap(request)

        // Assert
        assertTrue(pcapOutputStream.size() > 0)
        val pcapInputStream = ByteArrayInputStream(pcapOutputStream.toByteArray())
        val packets = mutableListOf<Packet>()
        Pcap.openStream(pcapInputStream).loop { packets.add(it) }

        assertEquals(6, packets.size)
        DOCUMENTS.forEachIndexed { index, document ->
            assertEquals(document.getLong("created_at"), packets[index].arrivalTime / 1000)
        }
    }

    @Test
    fun `Validate 'wav()' method`() {
        // Init
        val request = SessionRequest(
            createdAt = NOW,
            terminatedAt = NOW + 900000,
            method = listOf("INVITE"),
            callId = listOf("wav-test")
        )

        // Execute
        val wavOutputStream = service.wav(request)

        // Assert
        assertTrue(wavOutputStream.size() > 0)
        val bytes = wavOutputStream.toByteArray()
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))

        val audioInputStream = javax.sound.sampled.AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))
        // Two call legs (A and B) were recorded, so both must end up on their own channel,
        // time-aligned, rather than spliced into one longer mono stream.
        assertEquals(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED, audioInputStream.format.encoding)
        assertEquals(2, audioInputStream.format.channels)

        val alaw = javax.sound.sampled.AudioFormat(javax.sound.sampled.AudioFormat.Encoding.ALAW, 8000f, 8, 1, 1, 8000f, false)
        val pcm = javax.sound.sampled.AudioFormat(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED, 8000f, 16, 1, 2, 8000f, false)
        fun decodeAlaw(vararg payload: Byte): List<Short> {
            val input = javax.sound.sampled.AudioInputStream(ByteArrayInputStream(payload), alaw, payload.size.toLong())
            val decoded = javax.sound.sampled.AudioSystem.getAudioInputStream(pcm, input).use { it.readAllBytes() }
            return decoded.toList().chunked(2).map { (lo, hi) -> (((hi.toInt() and 0xFF) shl 8) or (lo.toInt() and 0xFF)).toShort() }
        }

        val legA = decodeAlaw(1, 2, 3, 4) + decodeAlaw(5, 6, 7, 8)
        val legB = decodeAlaw(9, 10, 11, 12) + List(4) { 0.toShort() }

        val samples = audioInputStream.readAllBytes().toList().chunked(4) // 2 channels x 16 bit = 4 bytes/frame
        assertEquals(8, samples.size)
        samples.forEachIndexed { i, frame ->
            val left = (((frame[1].toInt() and 0xFF) shl 8) or (frame[0].toInt() and 0xFF)).toShort()
            val right = (((frame[3].toInt() and 0xFF) shl 8) or (frame[2].toInt() and 0xFF)).toShort()
            assertEquals(legA[i], left, "sample $i (leg A / left channel)")
            assertEquals(legB[i], right, "sample $i (leg B / right channel)")
        }
    }

    @Test
    fun `Validate 'wav()' method filters by src_addr resolving a host name`() {
        // Init: leg A's packets carry src_host="SP01"; "SP01" resolves to a known host while
        // "Silknet" does not, so this must match leg A via its host field, not a literal src_addr.
        `when`(hostService.findByNameIgnoreCase("SP01")).thenReturn(Host(null, "SP01", emptyList()))
        val request = SessionRequest(
            createdAt = NOW,
            terminatedAt = NOW + 900000,
            method = listOf("INVITE"),
            callId = listOf("wav-test"),
            srcAddr = listOf("SP01", "Silknet")
        )

        // Execute
        val audioInputStream = javax.sound.sampled.AudioSystem.getAudioInputStream(ByteArrayInputStream(service.wav(request).toByteArray()))

        // Assert: mono, leg A only (8 samples)
        assertEquals(1, audioInputStream.format.channels)
        assertEquals(8, audioInputStream.readAllBytes().size / 2)
    }

    @Test
    fun `Validate 'wav()' method filters by src_addr as a literal address`() {
        // Init: leg B's packets have src_addr="192.168.10.5" with no registered host resolution,
        // so this must match leg B via a plain address comparison.
        val request = SessionRequest(
            createdAt = NOW,
            terminatedAt = NOW + 900000,
            method = listOf("INVITE"),
            callId = listOf("wav-test"),
            srcAddr = listOf("192.168.10.5")
        )

        // Execute
        val audioInputStream = javax.sound.sampled.AudioSystem.getAudioInputStream(ByteArrayInputStream(service.wav(request).toByteArray()))

        // Assert: mono, leg B only (4 samples)
        assertEquals(1, audioInputStream.format.channels)
        assertEquals(4, audioInputStream.readAllBytes().size / 2)
    }

    @Test
    fun `Validate 'wav()' method filters by dst_host`() {
        // Init: leg A's packets carry dst_host="SR-IN"; filtering on it must keep leg A and drop leg B.
        val request = SessionRequest(
            createdAt = NOW,
            terminatedAt = NOW + 900000,
            method = listOf("INVITE"),
            callId = listOf("wav-test"),
            dstHost = listOf("SR-IN")
        )

        // Execute
        val audioInputStream = javax.sound.sampled.AudioSystem.getAudioInputStream(ByteArrayInputStream(service.wav(request).toByteArray()))

        // Assert: mono, leg A only (8 samples)
        assertEquals(1, audioInputStream.format.channels)
        assertEquals(8, audioInputStream.readAllBytes().size / 2)
    }
}

@Component
open class TestSessionService : SessionService() {

    override fun findInRawBySessionRequest(req: SessionRequest): Iterator<Document> {
        return SessionServiceTest.DOCUMENTS.iterator()
    }

    override fun findRecInRawBySessionRequest(req: SessionRequest): Iterator<Document> {
        if (req.callId?.contains("wav-test") != true) {
            return Collections.emptyIterator()
        }

        // Leg A (caller -> callee): two consecutive RTP packets (PT=8, PCMA), timestamps
        // 20ms apart (160 samples @ 8kHz), payloads [1,2,3,4] and [5,6,7,8].
        val legAPacket1 = byteArrayOf(
            0x80.toByte(), 0x08, 0x00, 0x01,
            0x00, 0x00, 0x00, 0xA0.toByte(), // timestamp = 160
            0x11, 0x22, 0x33, 0x44,
            1, 2, 3, 4
        )
        val legAPacket2 = byteArrayOf(
            0x80.toByte(), 0x08, 0x00, 0x02,
            0x00, 0x00, 0x00, 0xA4.toByte(), // timestamp = 164 (immediately follows packet 1's 4 samples)
            0x11, 0x22, 0x33, 0x44,
            5, 6, 7, 8
        )
        // Leg B (callee -> caller): a single RTP packet (PT=8, PCMA), payload [9,10,11,12],
        // starting at the same wall-clock time as leg A.
        val legBPacket1 = byteArrayOf(
            0x80.toByte(), 0x08, 0x00, 0x01,
            0x00, 0x00, 0x01, 0xF4.toByte(), // timestamp = 500
            0x55, 0x66, 0x77, 0x88.toByte(),
            9, 10, 11, 12
        )
        // An RTCP packet interleaved with leg A's RTP traffic, which `wav()` must ignore.
        val rtcp = byteArrayOf(
            0x80.toByte(), 0xC8.toByte(), 0x00, 0x06,
            0x11, 0x22, 0x33, 0x44,
            0, 0, 0, 0
        )

        fun packet(
            createdAt: Long,
            raw: ByteArray,
            type: Int,
            srcAddr: String,
            dstAddr: String,
            srcHost: String? = null,
            dstHost: String? = null
        ) = Document().apply {
            put("created_at", createdAt)
            put("src_addr", srcAddr)
            put("src_port", 40000)
            srcHost?.let { put("src_host", it) }
            put("dst_addr", dstAddr)
            put("dst_port", 40002)
            dstHost?.let { put("dst_host", it) }
            put("type", type)
            put("raw_data", String(raw, Charsets.ISO_8859_1))
        }

        return listOf(
            packet(SessionServiceTest.NOW, legAPacket1, 2, "192.168.10.123", "192.168.10.5", srcHost = "SP01", dstHost = "SR-IN"),
            // Same SSRC/seq/timestamp as legAPacket1 but a different (unrelated relay) address
            // pair, simulating an on-path relay recorded on both its ingress and egress hop. This
            // must be deduplicated rather than treated as a third, separate call leg.
            packet(SessionServiceTest.NOW, legAPacket1, 2, "10.0.0.1", "10.0.0.2"),
            packet(SessionServiceTest.NOW, legBPacket1, 2, "192.168.10.5", "192.168.10.123", srcHost = "SR-IN", dstHost = "SP01"),
            packet(SessionServiceTest.NOW + 20, rtcp, 1, "192.168.10.123", "192.168.10.5"),
            packet(SessionServiceTest.NOW + 40, legAPacket2, 2, "192.168.10.123", "192.168.10.5", srcHost = "SP01", dstHost = "SR-IN")
        ).iterator()
    }
}
