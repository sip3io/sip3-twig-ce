/*
 * Copyright 2018-2022 SIP3.IO, Corp.
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

import org.bson.Document
import kotlin.math.roundToInt

object ReportUtil {

    fun splitReport(
        chunks: MutableList<Document>,
        report: Document,
        remainingDuration: Int,
        blockDuration: Int,
        blockCount: Int
    ) {
        val reportDuration = report.getInteger("duration")

        val firstFraction = remainingDuration.toDouble() / reportDuration
        val secondFraction = 1 - firstFraction

        val firstReport = reduceReport(report, firstFraction)
        val remainingReport = reduceReport(report, secondFraction)

        chunks.add(firstReport)
        when {
            // Remaining report fits to block
            remainingReport.getInteger("duration") <= blockDuration -> chunks.add(remainingReport)
            // Chunks size become greater than total available blocks
            chunks.size >= blockCount -> chunks.add(remainingReport)
            // Otherwise split report again
            else -> splitReport(chunks, remainingReport, blockDuration, blockDuration, blockCount)
        }
    }

    private fun reduceReport(report: Document, fraction: Double): Document {
        return Document().apply {
            put("mos", report.getDouble("mos"))
            put("r_factor", report.getDouble("r_factor"))
            put("duration", (report.getInteger("duration") * fraction).roundToInt())

            val packets = report.get("packets") as Document
            val packetsExpected = (packets.getInteger("expected") * fraction).roundToInt()
            val packetsReceived = (packets.getInteger("received") * fraction).roundToInt()
            val packetsRejected = (packets.getInteger("rejected") * fraction).roundToInt()

            put("packets", Document().apply {
                put("expected", packetsExpected)
                put("received", packetsReceived)
                put("rejected", packetsRejected)
                put("lost", packetsExpected - packetsReceived - packetsRejected)
            })

            val jitter = report.get("jitter") as Document
            put("jitter", Document().apply {
                put("min", jitter.getDouble("min"))
                put("max", jitter.getDouble("max"))
                put("avg", jitter.getDouble("avg"))
            })
        }
    }

}