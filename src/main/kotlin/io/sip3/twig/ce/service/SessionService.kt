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

package io.sip3.twig.ce.service

import com.mongodb.client.model.Filters.*
import gov.nist.javax.sip.message.SIPMessage
import gov.nist.javax.sip.parser.StringMsgParser
import io.pkts.PcapOutputStream
import io.pkts.buffer.Buffers
import io.pkts.frame.PcapGlobalHeader
import io.pkts.packet.PacketFactory
import io.sip3.twig.ce.domain.SessionRequest
import io.sip3.twig.ce.mongo.MongoClient
import io.sip3.twig.ce.service.host.HostService
import io.sip3.twig.ce.util.*
import mu.KotlinLogging
import org.bson.Document
import org.bson.conversions.Bson
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import java.io.ByteArrayOutputStream

abstract class SessionService {

    protected val logger = KotlinLogging.logger {}

    companion object {

        val CREATED_AT = compareBy<Document> { document -> document.getLong("created_at") }

        val CREATED_AT_WITH_NANOS = compareBy<Document>(
            { document -> document.getLong("created_at") },
            { document -> document.getInteger("nanos") ?: 0 }
        )
    }

    @Autowired
    protected lateinit var mongoClient: MongoClient

    @Autowired
    protected lateinit var hostService: HostService

    @Value("\${session.show-retransmits:\${session.show_retransmits:true}}")
    protected var showRetransmits: Boolean = true

    @Value("\${session.ignore-nanos:\${session.ignore_nanos:true}}")
    protected var ignoreNanos: Boolean = true

    @Value("\${session.media.termination-timeout:\${session.media.termination_timeout:60000}}")
    private var terminationTimeout: Long = 60000

    abstract fun findInRawBySessionRequest(req: SessionRequest): Iterator<Document>

    open fun details(req: SessionRequest): List<Document> {
        val legs = mutableListOf<Document>()

        findInRawBySessionRequest(req).asSequence()
            .filter { document ->
                document.getString("raw_data").startsWith(req.method?.first()!!)
            }
            .sortedWith(if (ignoreNanos) CREATED_AT else CREATED_AT_WITH_NANOS)
            .groupBy { document -> "${document.getString("src_addr")}:${document.getString("dst_addr")}" }
            .forEach { (_, documents) ->
                val document = documents.first { it.getBoolean("parsed") != false }
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

    open fun content(req: SessionRequest): List<Document> {
        val messages = findInRawBySessionRequest(req).asSequence()
            .sortedWith(if (ignoreNanos) CREATED_AT else CREATED_AT_WITH_NANOS)
            .groupBy { document -> "${document.getString("src_addr")}:${document.getString("dst_addr")}:${document.getString("raw_data")}" }
            .flatMap { (_, documents) ->
                if (showRetransmits) {
                    documents
                } else {
                    listOf(documents.first())
                }
            }
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
                    val rawData = document.getString("raw_data")
                    try {
                        StringMsgParser().parseSIPMessage(rawData.toByteArray(Charsets.ISO_8859_1), true, false, null)?.let { message ->
                            put("transaction_id", message.transactionId())
                            putAll(extendedParamsFrom(message))
                        }
                    } catch (e: Exception) {
                        logger.error("StringMsgParser 'parseSIPMessage()' failed.", e)
                    }

                    put("parsed", document.getBoolean("parsed") == true)
                    putIfAbsent("raw_data", rawData)
                }
            }
            .toList()

        return messages
    }

    open fun pcap(req: SessionRequest): ByteArrayOutputStream {
        val os = ByteArrayOutputStream()

        PcapOutputStream.create(PcapGlobalHeader.createDefaultHeader(), os).use { pos ->
            IteratorUtil.merge(findInRawBySessionRequest(req), findRecInRawBySessionRequest(req))
                .asSequence()
                .sortedWith(if (ignoreNanos) CREATED_AT else CREATED_AT_WITH_NANOS)
                .forEach { document ->
                    val raw = document.getString("raw_data").toByteArray(Charsets.ISO_8859_1)

                    val packet = PacketFactory.getInstance().transportFactory
                        .createUDP(document.getLong("created_at"), Buffers.wrap(raw))

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

    open fun stash(req: SessionRequest) {
        throw UnsupportedOperationException("Stash is not supported in CE version")
    }

    open fun findRecInRawBySessionRequest(req: SessionRequest): Iterator<Document> {
        requireNotNull(req.createdAt) { "created_at" }
        requireNotNull(req.terminatedAt) { "terminated_at" }
        requireNotNull(req.callId) { "call_id" }

        val filters = mutableListOf<Bson>().apply {
            add(gte("created_at", req.createdAt!!))
            add(lte("created_at", req.terminatedAt!! + terminationTimeout))
            add(`in`("call_id", req.callId!!))

            if (req.srcAddr != null && req.dstAddr != null) {
                add(legFilter(req.srcAddr!!, req.dstAddr!!))
            }
        }

        return mongoClient.find("rec_raw", Pair(req.createdAt!!, req.terminatedAt!! + terminationTimeout), and(filters))
            .asSequence()
            .flatMap { document ->
                document.getList("packets", Document::class.java)
                    .filter { it.getString("raw_data").length > 12 }
                    .map { packet ->
                        packet.put("src_addr", document.getString("src_addr"))
                        packet.put("src_port", document.getInteger("src_port"))
                        packet.put("dst_addr", document.getString("dst_addr"))
                        packet.put("dst_port", document.getInteger("dst_port"))

                        return@map packet
                    }
            }
            .iterator()
    }

    open fun legFilter(srcAddr: List<String>, dstAddr: List<String>): Bson {
        val (srcHosts, srcIps) = srcAddr.partition { hostService.findByNameIgnoreCase(it) != null }
        val (dstHosts, dstIps) = dstAddr.partition { hostService.findByNameIgnoreCase(it) != null }


        return or(
            and(
                hostOrAddrFilter("src", srcHosts, srcIps),
                hostOrAddrFilter("dst", dstHosts, dstIps)
            ),
            and(
                hostOrAddrFilter("src", dstHosts, dstIps),
                hostOrAddrFilter("dst", srcHosts, srcIps)
            )
        )
    }

    open fun hostOrAddrFilter(prefix: String, hosts: List<String>, ips: List<String>): Bson {
        val filters = mutableListOf<Bson>().apply {
            if (hosts.isNotEmpty()) add(`in`("${prefix}_host", hosts))
            if (ips.isNotEmpty()) add(`in`("${prefix}_addr", ips))
        }

        return if (filters.size == 1) {
            filters.first()
        } else {
            or(filters)
        }
    }

    open fun extendedParamsFrom(message: SIPMessage): Map<String, Any> {
        return emptyMap()
    }
}