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

import io.sip3.twig.ce.service.media.util.LegSessionUtil.createLegSession
import io.sip3.twig.ce.service.media.util.LegSessionUtil.generateLegId
import io.sip3.twig.ce.service.media.util.LegSessionUtil.generatePartyId
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class LegSessionUtilTest {

    companion object {

        val RTPR_INDEX_A = Document.parse(
            """
        {
            "_id" : "5e42002336501e14550cf962",
            "created_at" : 1581383606801,
            "terminated_at" : 1581383683483,
            "src_addr" : "192.168.10.113",
            "src_port" : 40030,
            "dst_addr" : "192.168.10.5",
            "dst_port" : 10244,
            "dst_host" : "PBX-2",
            "payload_type" : [ 8 ],
            "ssrc" : NumberLong(9961),
            "call_id" : "838f2897-35cd-475b-8111-b50fc1984dc9",
            "codec" : ["PCMA"],
            "duration" : 76682,
            "direction" : ["in"]
            "packets" : {
                "expected" : [3834],
                "received" : [3834],
                "lost" : [0],
                "rejected" : [0]
            },
            "jitter" : {
                "last" : [0.151603862643242],
                "avg" : [0.266511917114258],
                "min" : [0.0156879425048828],
                "max" : [1.36416816711426]
            },
            "r_factor" : [92.5499954223633],
            "mos" : [4.39635181427002],
            "fraction_lost" : [0.0]
        }
        """.trimIndent()
        )

        val RTPR_INDEX_B = Document.parse(
            """
        {
            "_id" : "5e42002336501e14550cf964",
            "created_at" : 1581383606830,
            "terminated_at" : 1581383683464,
            "src_addr" : "192.168.10.5",
            "src_port" : 10244,
            "src_host" : "PBX-2",
            "dst_addr" : "192.168.10.113",
            "dst_port" : 40030,
            "payload_type" : 8,
            "ssrc" : 1459443922,
            "call_id" : "838f2897-35cd-475b-8111-b50fc1984dc9",
            "codec" : "PCMA",
            "duration" : 76634,
            "packets" : {
                "expected" : 3834,
                "received" : 3834,
                "lost" : 0,
                "rejected" : 0
            },
            "jitter" : {
                "last" : 0.831299126148224,
                "avg" : 0.671812176704407,
                "min" : 0.38690584897995,
                "max" : 2.54890537261963
            },
            "r_factor" : 92.5499954223633,
            "mos" : 4.39635181427002,
            "fraction_lost" : 0.0
        }
        """.trimIndent()
        )
    }

    @Test
    fun `Generate leg id`() {
        assertEquals(generateLegId(RTPR_INDEX_A), generateLegId(RTPR_INDEX_B))
    }

    @Test
    fun `Generate party id`() {
        assertNotEquals(generatePartyId(RTPR_INDEX_A), generatePartyId(RTPR_INDEX_B))
    }

    @Test
    fun `Create Leg Session`() {
        val legSession = createLegSession(RTPR_INDEX_A, 24)

        assertEquals(RTPR_INDEX_A.getLong("created_at"), legSession.createdAt)
        assertEquals(RTPR_INDEX_A.getLong("created_at") + RTPR_INDEX_A.getInteger("duration"), legSession.terminatedAt)
        assertEquals(RTPR_INDEX_A.getInteger("duration"), legSession.duration)

        assertEquals(RTPR_INDEX_A.getString("call_id"), legSession.callId)

        assertEquals(RTPR_INDEX_A.getInteger("duration"), legSession.duration)
        assertEquals(RTPR_INDEX_A.getString("src_addr"), legSession.srcAddr)
        assertEquals(RTPR_INDEX_A.getInteger("src_port"), legSession.srcPort)

        assertEquals(RTPR_INDEX_A.getString("dst_addr"), legSession.dstAddr)
        assertEquals(RTPR_INDEX_A.getInteger("dst_port"), legSession.dstPort)
        assertEquals(RTPR_INDEX_A.getString("dst_host"), legSession.dstHost)

        assertEquals(1, legSession.codecs.size)
        assertEquals(RTPR_INDEX_A.getString("codec"), legSession.codecs.first().name)
        assertEquals(RTPR_INDEX_A.getInteger("payload_type"), legSession.codecs.first().payloadType)

        legSession.out.first().apply {
            assertEquals(RTPR_INDEX_A.getInteger("duration"), duration)
            assertEquals(RTPR_INDEX_A.getLong("created_at"), createdAt)
            assertEquals(RTPR_INDEX_A.getLong("created_at") + RTPR_INDEX_A.getInteger("duration"), terminatedAt)
            assertEquals(0, RTPR_INDEX_A.getDouble("mos").compareTo(mos))
            assertEquals(0, RTPR_INDEX_A.getDouble("r_factor").compareTo(rFactor))

            RTPR_INDEX_A.get("packets", Document::class.java).apply {
                assertEquals(getInteger("expected"), packets.expected)
                assertEquals(getInteger("received"), packets.received)
                assertEquals(getInteger("lost"), packets.lost)
                assertEquals(getInteger("rejected"), packets.rejected)
            }

            RTPR_INDEX_A.get("jitter", Document::class.java).apply {
                assertEquals(0, getDouble("min").compareTo(jitter.min))
                assertEquals(0, getDouble("max").compareTo(jitter.max))
                assertEquals(0, getDouble("avg").compareTo(jitter.avg))
            }
        }

        legSession.`in`.first().apply {
            assertEquals(RTPR_INDEX_B.getInteger("duration"), duration)
            assertEquals(RTPR_INDEX_B.getLong("created_at"), createdAt)
            assertEquals(RTPR_INDEX_B.getLong("created_at") + RTPR_INDEX_B.getInteger("duration"), terminatedAt)
            assertEquals(0, RTPR_INDEX_B.getDouble("mos").compareTo(mos))
            assertEquals(0, RTPR_INDEX_B.getDouble("r_factor").compareTo(rFactor))

            RTPR_INDEX_B.get("packets", Document::class.java).apply {
                assertEquals(getInteger("expected"), packets.expected)
                assertEquals(getInteger("received"), packets.received)
                assertEquals(getInteger("lost"), packets.lost)
                assertEquals(getInteger("rejected"), packets.rejected)
            }

            RTPR_INDEX_B.get("jitter", Document::class.java).apply {
                assertEquals(0, getDouble("min").compareTo(jitter.min))
                assertEquals(0, getDouble("max").compareTo(jitter.max))
                assertEquals(0, getDouble("avg").compareTo(jitter.avg))
            }
        }
    }
}