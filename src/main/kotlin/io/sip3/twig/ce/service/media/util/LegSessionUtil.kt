package io.sip3.twig.ce.service.media.util

import io.sip3.twig.ce.service.media.domain.LegSession
import io.sip3.twig.ce.service.media.domain.MediaSession
import org.bson.Document
import kotlin.math.max
import kotlin.math.min

object LegSessionUtil {

    fun generateLegId(report: Document): String {
        val callId = report.getString("call_id")

        val srcAddr = report.getString("src_addr")
        val srcPort = report.getInteger("src_port").let { port ->
            if (port % 2 == 0) { port } else { port - 1 }
        }

        val dstAddr = report.getString("dst_addr")
        val dstPort = report.getInteger("dst_port").let { port ->
            if (port % 2 == 0) { port } else { port - 1 }
        }

        return if (srcPort > dstPort) {
            "$callId:$srcAddr:$srcPort:$dstAddr:$dstPort"
        } else {
            "$callId:$dstAddr:$dstPort:$srcAddr:$srcPort"
        }
    }

    fun generatePartyId(report: Document): String {
        return "${report.getInteger("src_port")}:${report.getInteger("dst_port")}"
    }

    fun createLegSession(documents: List<Document>, blockCount: Int): LegSession {
        // Split to `out` and `in` reports
        val firstReport = documents.first()
        val outPartyId = generatePartyId(firstReport)
        val (out, `in`) = documents.partition { generatePartyId(it) == outPartyId }

        // Define timestamps
        val legCreatedAt =  min(out.first().getLong("started_at"),
                `in`.firstOrNull()?.getLong("started_at") ?: Long.MAX_VALUE)

        val legTerminatedAt = max(out.last().getLong("started_at") + out.last().getInteger("duration"),
                `in`.lastOrNull()?.let { it.getLong("started_at") + it.getInteger("duration") } ?: 0)


        return LegSession().apply {
            createdAt = legCreatedAt
            terminatedAt = legTerminatedAt

            duration = (legTerminatedAt - legCreatedAt).toInt()
            callId = firstReport.getString("call_id")

            srcAddr = firstReport.getString("src_addr")
            srcPort = firstReport.getInteger("src_port")
            firstReport.getString("src_host")?.let { srcHost = it }
            dstAddr = firstReport.getString("dst_addr")
            dstPort = firstReport.getInteger("dst_port")
            firstReport.getString("dst_host")?.let { dstHost = it }

            codecs.add(LegSession.Codec(firstReport.getString("codec_name"), firstReport.getInteger("payload_type")))

            this.out.add(createMediaSession(out, blockCount))

            if (`in`.isNotEmpty()) {
                this.`in`.add(createMediaSession(`in`, blockCount))
            }
        }
    }

    private fun createMediaSession(reports: List<Document>, blockCount: Int): MediaSession {
        return MediaSession(blockCount).apply {
            createdAt = reports.first().getLong("started_at")
            terminatedAt = reports.map { it.getLong("started_at") + it.getInteger("duration") }.max()!!
            duration = (terminatedAt - createdAt).toInt()

            mos = reports.sumByDouble { it.getDouble("mos") } / reports.size
            rFactor = reports.sumByDouble { it.getDouble("r_factor") } / reports.size

            val reportPackets = reports.map { it.get("packets") as Document }
            packets.apply {
                expected = reportPackets.sumBy { it.getInteger("expected") }
                received = reportPackets.sumBy { it.getInteger("received") }
                rejected = reportPackets.sumBy { it.getInteger("rejected") }
                lost = expected - received
            }

            val reportJitter = reports.map { it.get("jitter") as Document }
            jitter.apply {
                reportJitter.map { it.getDouble("min") }.filter { it != 0.0 }.min()?.let { min = it }
                max = reportJitter.map { it.getDouble("max") }.max()!!
                avg = reportJitter.sumByDouble { it.getDouble("avg") } / reports.size
            }
        }
    }
}