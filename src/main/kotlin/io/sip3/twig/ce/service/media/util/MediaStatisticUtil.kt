package io.sip3.twig.ce.service.media.util

import io.sip3.twig.ce.service.media.domain.MediaStatistic
import org.bson.Document
import kotlin.math.max
import kotlin.math.min

object MediaStatisticUtil {

    fun createMediaStatistic(report: Document): MediaStatistic {
        return MediaStatistic().apply {
            duration = report.getInteger("duration")
            mos = report.getDouble("mos")
            rFactor = report.getDouble("r_factor")

            val reportPackets = report.get("packets") as Document
            packets.apply {
                expected = reportPackets.getInteger("expected")
                received = reportPackets.getInteger("received")
                rejected = reportPackets.getInteger("rejected")
                lost = expected - received
            }

            val reportJitter = report.get("jitter") as Document
            jitter.apply {
                min = reportJitter.getDouble("min")
                max = reportJitter.getDouble("max")
                avg = reportJitter.getDouble("avg")
            }
        }
    }

    fun updateMediaStatistic(block: MediaStatistic, report: Document) {
        block.apply {
            val reportDuration = report.getInteger("duration")

            val secondFraction = if (reportDuration + duration > 0) {
                reportDuration.toDouble() / (reportDuration + duration)
            } else {
                0.0
            }
            val firstFraction = 1 - secondFraction

            mos = mos * firstFraction + report.getDouble("mos") * secondFraction
            rFactor = rFactor * firstFraction + report.getDouble("r_factor") * secondFraction

            val reportPackets = report.get("packets") as Document
            packets.apply {
                expected += reportPackets.getInteger("expected")
                received += reportPackets.getInteger("received")
                rejected += reportPackets.getInteger("rejected")
                lost = expected - received
            }

            val reportJitter = report.get("jitter") as Document
            jitter.apply {
                min = if (min != 0.0) {
                    min(min, reportJitter.getDouble("min"))
                } else {
                    reportJitter.getDouble("min")
                }
                max = max(max, reportJitter.getDouble("max"))
                avg = avg * firstFraction  + reportJitter.getDouble("avg") * secondFraction
            }

            duration += reportDuration
        }
    }
}