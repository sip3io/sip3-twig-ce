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

package io.sip3.twig.ce.service

import gov.nist.javax.sip.message.SIPMessage
import gov.nist.javax.sip.parser.StringMsgParser
import io.pkts.PcapOutputStream
import io.pkts.buffer.Buffers
import io.pkts.frame.PcapGlobalHeader
import io.pkts.packet.PacketFactory
import io.sip3.twig.ce.domain.SessionRequest
import io.sip3.twig.ce.mongo.MongoClient
import io.sip3.twig.ce.util.callId
import io.sip3.twig.ce.util.fromUri
import io.sip3.twig.ce.util.requestUri
import io.sip3.twig.ce.util.toUri
import mu.KotlinLogging
import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import java.io.ByteArrayOutputStream

abstract class SessionService {

    protected val logger = KotlinLogging.logger {}

    companion object {

        val CREATED_AT = compareBy<Document>(
                { document -> document.getLong("created_at") },
                { document -> document.getInteger("nanos") ?: 0 }
        )
    }

    @Autowired
    internal lateinit var mongoClient: MongoClient

    @Value("\${session.show-retransmits}")
    private var showRetransmits: Boolean = true

    abstract fun findInRawBySessionRequest(req: SessionRequest): Iterator<Document>

    open fun details(req: SessionRequest): Any? {
        val legs = mutableListOf<Document>()

        findInRawBySessionRequest(req).asSequence()
                .filter { document ->
                    document.getString("raw_data").startsWith("REGISTER")
                }
                .sortedWith(CREATED_AT)
                .groupBy { document -> "${document.getString("src_addr")}:${document.getString("dst_addr")}" }
                .forEach { (_, documents) ->
                    val document = documents.first()
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
                            logger.error("StringMsgParser 'parseSIPMessage()' failed.", e)
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

    open fun content(req: SessionRequest): Any? {
        val messages = findInRawBySessionRequest(req).asSequence()
                .sortedWith(CREATED_AT)
                .groupBy { document -> "${document.getString("src_addr")}:${document.getString("dst_addr")}:${document.getString("raw_data")}" }
                .flatMap { (_, documents) -> if (showRetransmits) { documents } else { listOf(documents.first()) } }
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
                        put("raw_data", document.getString("raw_data"))
                    }
                }
                .toList()

        val hosts = mutableSetOf<String>().apply {
            messages.forEach { message ->
                add(message.getString("src_host") ?: message.getString("src_addr"))
                add(message.getString("dst_host") ?: message.getString("dst_addr"))
            }
        }

        return mutableMapOf<String, Any>().apply {
            put("hosts", hosts)
            put("messages", messages)
        }
    }

    open fun pcap(req: SessionRequest): ByteArrayOutputStream {
        val os = ByteArrayOutputStream()

        PcapOutputStream.create(PcapGlobalHeader.createDefaultHeader(), os).use { pos ->
            findInRawBySessionRequest(req).asSequence()
                    .sortedWith(CREATED_AT)
                    .groupBy { document -> "${document.getString("src_addr")}:${document.getString("dst_addr")}:${document.getString("raw_data")}" }
                    .flatMap { (_, documents) -> if (showRetransmits) { documents } else { listOf(documents.first()) } }
                    .forEach { document ->
                        val packet = PacketFactory.getInstance().transportFactory
                                .createUDP(document.getLong("created_at"), Buffers.wrap(document.getString("raw_data")))

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
}
