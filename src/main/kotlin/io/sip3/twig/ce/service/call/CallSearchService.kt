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
import io.sip3.twig.ce.mongo.MongoClient
import io.sip3.twig.ce.service.SearchService
import io.sip3.twig.ce.service.attribute.AttributeService
import mu.KotlinLogging
import org.bson.Document
import org.bson.conversions.Bson
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component("INVITE")
class CallSearchService : SearchService {

    private val logger = KotlinLogging.logger {}

    companion object {

        val CREATED_AT = compareBy<Document>(
                { d -> d.getLong("created_at") },
                { d -> d.getString("dst_addr") }
        )
    }

    @Value("\${session.call.max-legs}")
    private var maxLegs: Int = 10

    @Value("\${session.call.termination-timeout}")
    private var terminationTimeout: Long = 10000

    @Autowired
    private lateinit var attributeService: AttributeService

    @Autowired
    private lateinit var mongoClient: MongoClient

    override fun search(request: SearchRequest): Iterator<SearchResponse> {
        // TODO...
        return Collections.emptyIterator<SearchResponse>()
    }

    private fun findInRtprIndexBySearchRequest(request: SearchRequest): Iterator<Document> {
        // TODO...
        return Collections.emptyIterator<Document>()
    }

    private fun findInSipIndexBySearchRequest(request: SearchRequest): Iterator<Document> {
        // TODO...
        return Collections.emptyIterator<Document>()
    }

    inner class SearchIterator(private val request: SearchRequest, private val documents: Iterator<Document>) : Iterator<SearchResponse> {

        private val processed = mutableSetOf<Document>()
        private var next: Set<Document>? = null

        override fun hasNext(): Boolean {
            if (next == null) {
                while (documents.hasNext()) {
                    val document = documents.next()

                    // Check if document is in `processed`
                    if (!processed.contains(document)) {
                        // Create and aggregate `call`
                        val call = TreeSet(CREATED_AT)
                        aggregateCall(document, call)

                        // Add all documents to `processed`
                        processed.addAll(call)

                        // Check `created_at` condition
                        if (request.createdAt <= call.first().getLong("created_at")) {
                            next = call
                            break
                        }
                    }
                }
            }

            return next != null
        }

        override fun next(): SearchResponse {
            // `NoSuchElementException` must be thrown accordingly to the `Iterator` class contract
            if (!hasNext()) {
                throw NoSuchElementException()
            }

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