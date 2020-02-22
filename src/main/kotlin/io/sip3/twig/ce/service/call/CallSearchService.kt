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

package io.sip3.twig.ce.service.call

import com.mongodb.client.model.Filters.*
import io.sip3.twig.ce.domain.SearchRequest
import io.sip3.twig.ce.domain.SearchResponse
import io.sip3.twig.ce.service.SearchService
import io.sip3.twig.ce.util.IteratorUtil
import io.sip3.twig.ce.util.map
import io.sip3.twig.ce.util.nextOrNull
import mu.KotlinLogging
import org.bson.Document
import org.bson.conversions.Bson
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component("INVITE")
class CallSearchService : SearchService() {

    private val logger = KotlinLogging.logger {}

    companion object {

        val CREATED_AT = compareBy<Document>(
                { d -> d.getLong("created_at") },
                { d -> d.getString("dst_addr") }
        )
    }

    @Value("\${session.call.max-legs}")
    private var maxLegs: Int = 10

    @Value("\${session.call.aggregation-timeout}")
    private var aggregationTimeout: Long = 60000

    @Value("\${session.call.termination-timeout}")
    private var terminationTimeout: Long = 5000

    override fun search(request: SearchRequest): Iterator<SearchResponse> {
        var (createdAt, terminatedAt, query) = request

        val documents = if (query.contains("rtp.")) {
            // Filter documents in `rtpr_rtp` and `rtpr_rtcp` collections
            findInRtprIndexBySearchRequest(createdAt, terminatedAt, query).map { document ->
                // Map `rtpr_rtp` or `rtpr_rtcp` document to `sip_call` document
                val startedAt = document.getLong("started_at")
                document.getString("call_id")?.let { callId ->
                    query = "sip.call_id=$callId"
                    return@map findInSipIndexBySearchRequest(startedAt - aggregationTimeout, startedAt, query).nextOrNull()
                }
            }
        } else {
            // Filter documents in `sip_call` collections
            findInSipIndexBySearchRequest(createdAt, terminatedAt, query)
        }

        // Search and aggregate calls using filtered documents
        return SearchIterator(createdAt, documents)
    }

    private fun findInRtprIndexBySearchRequest(createdAt: Long, terminatedAt: Long, query: String): Iterator<Document> {
        var filters = mutableListOf<Bson>().apply {
            // Time filters
            add(gte("started_at", createdAt))
            add(lte("started_at", terminatedAt))

            // Main filters
            query.split(" ")
                    .filterNot { it.isBlank() }
                    .filterNot { it.startsWith("sip.") }
                    .map { filter(it) }
                    .forEach { add(it) }
        }

        return IteratorUtil.merge(
                mongoClient.find("rtpr_rpt", Pair(createdAt, terminatedAt), and(filters)),
                mongoClient.find("rtpr_rtcp", Pair(createdAt, terminatedAt), and(filters))
        )
    }

    private fun findInSipIndexBySearchRequest(createdAt: Long, terminatedAt: Long, query: String): Iterator<Document> {
        val filters = mutableListOf<Bson>().apply {
            // Time filters
            add(gte("created_at", createdAt))
            add(lte("created_at", terminatedAt))

            // Main filters
            query.split(" ")
                    .filterNot { it.isBlank() }
                    .filterNot { it.startsWith("rtp.") }
                    .filterNot { it.startsWith("sip.method") }
                    .map { filter(it) }
                    .forEach { add(it) }
        }

        return mongoClient.find("sip_call_index", Pair(createdAt, terminatedAt), and(filters))
    }

    inner class SearchIterator(private val createdAt: Long, private val documents: Iterator<Document?>) : Iterator<SearchResponse> {

        private var next: Set<Document>? = null
        private val processed = mutableSetOf<String>()

        override fun hasNext(): Boolean {
            if (next != null) return true

            while (documents.hasNext()) {
                var document: Document = documents.next() ?: continue

                // Check if `call_id` is in `processed`
                if (!processed.contains(document.getString("call_id"))) {
                    // Create and aggregate `call`
                    val call = TreeSet(CREATED_AT)
                    aggregateCall(document, call)

                    // Add all the documents `call_id` to `processed`
                    call.forEach { processed.add(it.getString("call_id")) }

                    // Check `created_at` condition
                    if (createdAt <= call.first().getLong("created_at")) {
                        next = call
                        break
                    }
                }
            }

            return next != null
        }

        override fun next(): SearchResponse {
            if (!hasNext()) throw NoSuchElementException()

            val result = SearchResponse().apply {
                // TODO...
            }

            next = null
            return result
        }

        private fun aggregateCall(document: Document, call: TreeSet<Document>) {
            if (call.add(document) && call.size < maxLegs) {
                findInSipIndexByDocument(document).forEach { d -> aggregateCall(d, call) }
            }
        }

        private fun findInSipIndexByDocument(document: Document): Iterator<Document> {
            val createdAt = document.getLong("created_at")
            val terminatedAt = document.getLong("terminated_at")

            val filters = mutableListOf<Bson>().apply {
                // Time filters
                if (terminatedAt == null) {
                    // Session is still in progress
                    add(or(
                            gt("created_at", createdAt - terminationTimeout),
                            lt("created_at", createdAt + terminationTimeout)
                    ))
                } else {
                    // Session is terminated
                    add(lt("created_at", terminatedAt))
                    add(gt("terminated_at", createdAt))
                }

                // Host filters
                add(or(
                        document.getString("dst_host")?.let { eq("src_host", it) } ?: eq("dst_addr", document.getString("src_addr")),
                        document.getString("src_host")?.let { eq("dst_host", it) } ?: eq("src_addr", document.getString("dst_addr"))
                ))

                // Main filters
                val xCallId = document.getString("x_call_id")
                add(or(
                        and(
                                eq("caller", document.getString("caller")),
                                eq("callee", document.getString("callee"))
                        ),
                        if (xCallId == null) {
                            eq("x_call_id", document.getString("call_id"))
                        } else {
                            or(
                                    eq("call_id", document.getString("x_call_id")),
                                    eq("x_call_id", document.getString("call_id")),
                                    eq("x_call_id", document.getString("x_call_id"))
                            )
                        }
                ))
            }

            val timeRange = if (terminatedAt == null) {
                Pair(createdAt - terminationTimeout, createdAt + terminationTimeout)
            } else {
                Pair(createdAt, terminatedAt)
            }

            return mongoClient.find("sip_call_index", timeRange, and(filters))
        }
    }
}