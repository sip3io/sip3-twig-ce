/*
 * Copyright 2018-2024 SIP3.IO, Corp.
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

import io.sip3.twig.ce.service.media.util.ReportUtil.splitReport
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReportUtilTest {

    companion object {

        val RTPR_RAW = Document.parse(
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
    }

    @Test
    fun `Split report`() {
        val chunks = mutableListOf<Document>()
        splitReport(chunks, RTPR_RAW, 820, 1000, 28)
        assertEquals(6, chunks.size)
        assertEquals(820, chunks.first().getInteger("duration"))
        assertEquals(300, chunks.last().getInteger("duration"))
        assertEquals(RTPR_RAW.getInteger("duration"), chunks.sumOf { it.getInteger("duration") })

        chunks.forEach { chunk ->
            assertEquals(RTPR_RAW.get("jitter"), chunk.get("jitter"))
            assertEquals(RTPR_RAW.get("r_factor"), chunk.get("r_factor"))
            assertEquals(RTPR_RAW.get("mos"), chunk.get("mos"))
        }

        val chunkPackets = chunks.map { it.get("packets", Document::class.java) }
        RTPR_RAW.get("packets", Document::class.java).apply {
            assertEquals(getInteger("expected"), chunkPackets.sumOf { it.getInteger("expected") })
            assertEquals(getInteger("received"), chunkPackets.sumOf { it.getInteger("received") })
            assertEquals(getInteger("lost"), chunkPackets.sumOf { it.getInteger("lost") })
            assertEquals(getInteger("rejected"), chunkPackets.sumOf { it.getInteger("rejected") })
        }
    }
}