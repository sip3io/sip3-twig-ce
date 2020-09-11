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

package io.sip3.twig.ce.service.media

import io.sip3.twig.ce.MockitoExtension.any
import io.sip3.twig.ce.domain.SessionRequest
import io.sip3.twig.ce.mongo.MongoClient
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.`when`
import org.mockito.BDDMockito.times
import org.mockito.BDDMockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest
class MediaSessionServiceTest {

    companion object {

        val RTPR_INDEX_OUT = Document.parse("""
            {
              "_id": "5f5979da8f5db2164b2b7721",
              "call_id": "NjBlMTZhOWQ1Y2JjMzk1NTY3MjFlZTc0MTU4OTA1NDA.",
              "codec_name": "PCMU",
              "created_at": 1599699418686,
              "dst_addr": "192.168.10.5",
              "dst_host": "PBX-2",
              "dst_port": 11958,
              "duration": 19779,
              "fraction_lost": 0,
              "jitter": {
                "last": 0.12251465022563934,
                "avg": 0.2594599425792694,
                "min": 0.005375981330871582,
                "max": 1.3704850673675537
              },
              "mos": 4.409228801727295,
              "packets": {
                "expected": 988,
                "received": 988,
                "lost": 0,
                "rejected": 0
              },
              "payload_type": 8,
              "r_factor": 93.19705963134766,
              "src_addr": "192.168.10.109",
              "src_port": 40042,
              "ssrc": 24767,
              "started_at": 1599699368433
            }
          """.trimIndent())

        val RTPR_INDEX_IN = Document.parse("""
            {
              "_id": "5f5979da8f5db2164b2b7724",
              "call_id": "NjBlMTZhOWQ1Y2JjMzk1NTY3MjFlZTc0MTU4OTA1NDA.",
              "codec_name": "PCMU",
              "created_at": 1599699418686,
              "dst_addr": "192.168.10.109",
              "dst_port": 40042,
              "duration": 19657,
              "fraction_lost": 0,
              "jitter": {
                "last": 1.3305953741073608,
                "avg": 0.7710570693016052,
                "min": 0.01731395721435547,
                "max": 1.870510220527649
              },
              "mos": 4.408666610717773,
              "packets": {
                "expected": 983,
                "received": 983,
                "lost": 0,
                "rejected": 0
              },
              "payload_type": 8,
              "r_factor": 93.16806030273438,
              "src_addr": "192.168.10.5",
              "src_port": 11958,
              "ssrc": 1675032981,
              "started_at": 1599699368452,
              "src_host": "PBX-2"
            }
          """.trimIndent())

        val RTPR_RAW_1 = Document.parse("""
        {
            "_id": "5f5979b28f5db2164b2b765e",
            "call_id": "NjBlMTZhOWQ1Y2JjMzk1NTY3MjFlZTc0MTU4OTA1NDA.",
            "codec_name": "PCMU",
            "created_at": 1599699378652,
            "dst_addr": "192.168.10.5",
            "dst_host": "PBX-2",
            "dst_port": 11958,
            "duration": 5120,
            "fraction_lost": 0,
            "jitter": {
            "last": 0.20461209118366241,
            "avg": 0.2345445305109024,
            "min": 0.005375981330871582,
            "max": 0.6464104652404785
        },
            "mos": 4.409190654754639,
            "packets": {
            "expected": 256,
            "received": 256,
            "lost": 0,
            "rejected": 0
        },
            "payload_type": 8,
            "r_factor": 93.19508361816406,
            "src_addr": "192.168.10.109",
            "src_port": 40042,
            "ssrc": 24767,
            "started_at": 1599699368433
        }
        """.trimIndent())
        val RTPR_RAW_2 = Document.parse("""
        {
            "_id": "5f5979b28f5db2164b2b7661",
            "call_id": "NjBlMTZhOWQ1Y2JjMzk1NTY3MjFlZTc0MTU4OTA1NDA.",
            "codec_name": "PCMU",
            "created_at": 1599699378668,
            "dst_addr": "192.168.10.109",
            "dst_port": 40042,
            "duration": 5116,
            "fraction_lost": 0,
            "jitter": {
            "last": 0.8373932838439941,
            "avg": 0.7710328698158264,
            "min": 0.01731395721435547,
            "max": 1.403550624847412
        },
            "mos": 4.408896446228027,
            "packets": {
            "expected": 256,
            "received": 256,
            "lost": 0,
            "rejected": 0
        },
            "payload_type": 8,
            "r_factor": 93.17990112304688,
            "src_addr": "192.168.10.5",
            "src_port": 11958,
            "ssrc": 1675032981,
            "started_at": 1599699368452,
            "src_host": "PBX-2"
        }""".trimIndent())
        val RTPR_RAW_3 = Document.parse("""
        {
            "_id": "5f5979b78f5db2164b2b7690",
            "call_id": "NjBlMTZhOWQ1Y2JjMzk1NTY3MjFlZTc0MTU4OTA1NDA.",
            "codec_name": "PCMU",
            "created_at": 1599699383772,
            "dst_addr": "192.168.10.5",
            "dst_host": "PBX-2",
            "dst_port": 11958,
            "duration": 5119,
            "fraction_lost": 0,
            "jitter": {
            "last": 0.16368630528450012,
            "avg": 0.25806379318237305,
            "min": 0.08053219318389893,
            "max": 0.9351742267608643
        },
            "mos": 4.409209728240967,
            "packets": {
            "expected": 256,
            "received": 256,
            "lost": 0,
            "rejected": 0
        },
            "payload_type": 8,
            "r_factor": 93.1960678100586,
            "src_addr": "192.168.10.109",
            "src_port": 40042,
            "ssrc": 24767,
            "started_at": 1599699373553
        }
        """.trimIndent())
        val RTPR_RAW_4 = Document.parse("""
        {
            "_id": "5f5979b78f5db2164b2b7693",
            "call_id": "NjBlMTZhOWQ1Y2JjMzk1NTY3MjFlZTc0MTU4OTA1NDA.",
            "codec_name": "PCMU",
            "created_at": 1599699383789,
            "dst_addr": "192.168.10.109",
            "dst_port": 40042,
            "duration": 5120,
            "fraction_lost": 0,
            "jitter": {
            "last": 1.1254804134368896,
            "avg": 0.7317721247673035,
            "min": 0.45853063464164734,
            "max": 1.4231394529342651
        },
            "mos": 4.408762454986572,
            "packets": {
            "expected": 256,
            "received": 256,
            "lost": 0,
            "rejected": 0
        },
            "payload_type": 8,
            "r_factor": 93.17298889160156,
            "src_addr": "192.168.10.5",
            "src_port": 11958,
            "ssrc": 1675032981,
            "started_at": 1599699373568,
            "src_host": "PBX-2"
        }""".trimIndent())

        val RTPR_RAW_5 = Document.parse("""
        {
            "_id": "5f5979da8f5db2164b2b7725",
            "call_id": "NjBlMTZhOWQ1Y2JjMzk1NTY3MjFlZTc0MTU4OTA1NDA.",
            "codec_name": "PCMU",
            "created_at": 1599699418686,
            "dst_addr": "192.168.10.5",
            "dst_host": "PBX-2",
            "dst_port": 11958,
            "duration": 9540,
            "fraction_lost": 0,
            "jitter": {
            "last": 0.12251465022563934,
            "avg": 0.28577154874801636,
            "min": 0.05159604549407959,
            "max": 1.3704850673675537
        },
            "mos": 4.409228801727295,
            "packets": {
            "expected": 477,
            "received": 477,
            "lost": 0,
            "rejected": 0
        },
            "payload_type": 8,
            "r_factor": 93.19705963134766,
            "src_addr": "192.168.10.109",
            "src_port": 40042,
            "ssrc": 24767,
            "started_at": 1599699378672
        }
        """.trimIndent())
        val RTPR_RAW_6 = Document.parse("""
        {
            "_id": "5f5979da8f5db2164b2b7728",
            "call_id": "NjBlMTZhOWQ1Y2JjMzk1NTY3MjFlZTc0MTU4OTA1NDA.",
            "codec_name": "PCMU",
            "created_at": 1599699418686,
            "dst_addr": "192.168.10.109",
            "dst_port": 40042,
            "duration": 9421,
            "fraction_lost": 0,
            "jitter": {
            "last": 1.3305953741073608,
            "avg": 0.8103660941123962,
            "min": 0.4656289517879486,
            "max": 1.870510220527649
        },
            "mos": 4.408666610717773,
            "packets": {
            "expected": 471,
            "received": 471,
            "lost": 0,
            "rejected": 0
        },
            "payload_type": 8,
            "r_factor": 93.16806030273438,
            "src_addr": "192.168.10.5",
            "src_port": 11958,
            "ssrc": 1675032981,
            "started_at": 1599699378688,
            "src_host": "PBX-2"
        }
        """.trimIndent())
    }

    @MockBean
    private lateinit var client: MongoClient

    @Autowired
    private lateinit var service: MediaSessionService

    @Test
    fun `Validate 'details()' method`() {
        // Init
        val rtprIndex = listOf(RTPR_INDEX_OUT, RTPR_INDEX_IN)
        val rtprRaw = listOf(RTPR_RAW_1, RTPR_RAW_2, RTPR_RAW_3, RTPR_RAW_4, RTPR_RAW_5, RTPR_RAW_6)
        `when`(client.find(any(), any(), any(), any(), any()))
                // RTP
                .thenReturn(rtprIndex.iterator())
                .thenReturn(rtprRaw.iterator())
                // RTCP
                .thenReturn(rtprIndex.iterator())
                .thenReturn(rtprRaw.iterator())

        val request = SessionRequest().apply {
            createdAt = 1581383715357
            terminatedAt = 1581383606830
            callId = listOf("838f2897-35cd-475b-8111-b50fc1984dc9")
        }

        // Execute
        val result = service.details(request)

        // Assert
        assertEquals(1, result.size)
        val media = result.first()
        val rtp = media["rtp"]
        assertNotNull(rtp)
        assertEquals(RTPR_INDEX_OUT.getLong("started_at"), rtp!!.createdAt)
        assertEquals(RTPR_INDEX_OUT.getString("call_id"), rtp.callId)
        assertEquals(RTPR_INDEX_OUT.getInteger("duration"), rtp.duration)

        assertEquals(RTPR_INDEX_OUT.getString("src_addr"), rtp.srcAddr)
        assertEquals(RTPR_INDEX_OUT.getString("src_host"), rtp.srcHost)
        assertEquals(RTPR_INDEX_OUT.getInteger("src_port"), rtp.srcPort)

        assertEquals(RTPR_INDEX_OUT.getString("dst_addr"), rtp.dstAddr)
        assertEquals(RTPR_INDEX_OUT.getString("dst_host"), rtp.dstHost)
        assertEquals(RTPR_INDEX_OUT.getInteger("dst_port"), rtp.dstPort)

        rtp.`in`.first().apply {
            assertEquals(RTPR_INDEX_IN.getLong("started_at"), createdAt)
            assertEquals(packets.expected, blocks.sumBy { it.packets.expected })
            assertEquals(RTPR_INDEX_IN.get("packets", Document::class.java).getInteger("expected"), packets.expected)
            assertEquals(RTPR_INDEX_IN.get("jitter", Document::class.java).getDouble("avg"), jitter.avg)
        }

        rtp.out.first().apply {
            assertEquals(RTPR_INDEX_OUT.getLong("started_at"), createdAt)
            assertEquals(packets.expected, blocks.sumBy { it.packets.expected })
            assertEquals(RTPR_INDEX_OUT.get("packets", Document::class.java).getInteger("expected"), packets.expected)
            assertEquals(RTPR_INDEX_OUT.get("jitter", Document::class.java).getDouble("avg"), jitter.avg)
        }

        val rtcp = media["rtcp"]
        assertNotNull(rtcp)

        verify(client, times(4)).find(any(), any(), any(), any(), any())
    }
}