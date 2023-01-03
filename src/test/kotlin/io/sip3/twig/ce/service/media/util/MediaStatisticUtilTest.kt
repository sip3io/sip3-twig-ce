/*
 * Copyright 2018-2023 SIP3.IO, Corp.
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

import io.sip3.twig.ce.service.media.util.MediaStatisticUtil.createMediaStatistic
import io.sip3.twig.ce.service.media.util.MediaStatisticUtil.updateMediaStatistic
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaStatisticUtilTest {

    companion object {

        val RTPR_RAW_1 = Document.parse(
            """
        {
            "duration" : 5120,
            "packets" : {
                "expected" : 256,
                "received" : 254,
                "lost" : 2,
                "rejected" : 0
            },
            "jitter" : {
                "avg" : 11.0454301834106,
                "min" : 5.96987581253052,
                "max" : 26.3100929260254
            },
            "r_factor" : 77.9435958862305,
            "mos" : 3.94396066665649,
            "fraction_lost" : 0.0078125
        }
        """.trimIndent()
        )
        val RTPR_RAW_2 = Document.parse(
            """
        {
            "duration" : 5120,
            "packets" : {
                "expected" : 256,
                "received" : 256,
                "lost" : 0,
                "rejected" : 0
            },
            "jitter" : {
                "avg" : 0.0404036156833172,
                "min" : 0.0284971818327904,
                "max" : 0.0864984318614006
            },
            "r_factor" : 92.5499954223633,
            "mos" : 4.39635181427002,
            "fraction_lost" : 0.0
        }
        """.trimIndent()
        )
    }

    @Test
    fun `Create MediaStatistic`() {
        createMediaStatistic(RTPR_RAW_1).apply {
            assertEquals(RTPR_RAW_1.getInteger("duration"), duration)
            assertEquals(0, RTPR_RAW_1.getDouble("mos").compareTo(mos))
            assertEquals(0, RTPR_RAW_1.getDouble("r_factor").compareTo(rFactor))

            RTPR_RAW_1.get("packets", Document::class.java).apply {
                assertEquals(getInteger("expected"), packets.expected)
                assertEquals(getInteger("received"), packets.received)
                assertEquals(getInteger("lost"), packets.lost)
                assertEquals(getInteger("rejected"), packets.rejected)
            }

            RTPR_RAW_1.get("jitter", Document::class.java).apply {
                assertEquals(0, getDouble("min").compareTo(jitter.min))
                assertEquals(0, getDouble("max").compareTo(jitter.max))
                assertEquals(0, getDouble("avg").compareTo(jitter.avg))
            }
        }
    }

    @Test
    fun `Update MediaStatistic`() {
        val mediaStatistic = createMediaStatistic(RTPR_RAW_1)
        updateMediaStatistic(mediaStatistic, RTPR_RAW_2)

        val packetsRtpr1 = RTPR_RAW_1.get("packets", Document::class.java)
        val jitterRtpr1 = RTPR_RAW_1.get("jitter", Document::class.java)

        val packetsRtpr2 = RTPR_RAW_2.get("packets", Document::class.java)
        val jitterRtpr2 = RTPR_RAW_2.get("jitter", Document::class.java)

        assertEquals(RTPR_RAW_1.getInteger("duration") + RTPR_RAW_2.getInteger("duration"), mediaStatistic.duration)
        assertEquals((RTPR_RAW_1.getDouble("mos") + RTPR_RAW_2.getDouble("mos")) / 2, mediaStatistic.mos, 0.0001)
        assertEquals((RTPR_RAW_1.getDouble("r_factor") + RTPR_RAW_2.getDouble("r_factor")) / 2, mediaStatistic.rFactor, 0.0001)

        mediaStatistic.packets.apply {
            assertEquals(packetsRtpr1.getInteger("expected") + packetsRtpr2.getInteger("expected"), expected)
            assertEquals(packetsRtpr1.getInteger("received") + packetsRtpr2.getInteger("received"), received)
            assertEquals(packetsRtpr1.getInteger("lost") + packetsRtpr2.getInteger("lost"), lost)
            assertEquals(packetsRtpr1.getInteger("rejected") + packetsRtpr2.getInteger("rejected"), rejected)
        }

        mediaStatistic.jitter.apply {
            assertEquals(jitterRtpr2.getDouble("min"), min, 0.0001)
            assertEquals(jitterRtpr1.getDouble("max"), max, 0.0001)
            assertEquals((jitterRtpr1.getDouble("avg") + jitterRtpr2.getDouble("avg")) / 2, avg, 0.0001)
        }
    }
}