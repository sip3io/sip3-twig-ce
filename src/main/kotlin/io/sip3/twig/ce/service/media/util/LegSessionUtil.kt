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

package io.sip3.twig.ce.service.media.util

import org.bson.Document

@Suppress("UNCHECKED_CAST")
object LegSessionUtil {

    fun generateLegId(report: Document): String {
        val callId = report.getString("call_id")
        val srcAddr = report.getString("src_addr")
        val dstAddr = report.getString("dst_addr")

        return if (srcAddr > dstAddr) {
            "$callId:$srcAddr:$dstAddr"
        } else {
            "$callId:$dstAddr:$srcAddr"
        }
    }

    fun generatePartyId(report: Document, source: String): String {
        val srcAddr = report.getString("src_addr")
        val srcPort = report.getInteger("src_port")
        val dstAddr = report.getString("dst_addr")
        val dstPort = report.getInteger("dst_port")

        return when (source) {
            "rtcp" -> "$dstAddr:$dstPort:$srcAddr:$srcPort"
            "rtp" -> "$srcAddr:$srcPort:$dstAddr:$dstPort"
            else -> throw IllegalArgumentException("Unsupported RTP Report source: '$source'")
        }
    }
}