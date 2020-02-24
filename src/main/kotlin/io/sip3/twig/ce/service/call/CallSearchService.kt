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

    @Value("\${session.use-x-correlation-header}")
    private var useXCorrelationHeader: Boolean = true

    @Value("\${session.call.max-legs}")
    private var maxLegs: Int = 10

    @Value("\${session.call.aggregation-timeout}")
    private var aggregationTimeout: Long = 60000

    @Value("\${session.call.termination-timeout}")
    private var terminationTimeout: Long = 5000

    override fun search(request: SearchRequest): Iterator<SearchResponse> {
        var (createdAt, terminatedAt, query) = request

        val matchedDocuments = if (query.contains("rtp.")) {
            // Filter documents in `rtpr_rtp_index` and `rtpr_rtcp_index` collections
            findInRtprIndexBySearchRequest(createdAt, terminatedAt, query).map { document ->
                // Map `rtpr_rtp` or `rtpr_rtcp` document to `sip_call_index` document
                val startedAt = document.getLong("started_at")
                document.getString("call_id")?.let { callId ->
                    query = "sip.call_id=$callId"
                    return@map findInSipIndexBySearchRequest(startedAt - aggregationTimeout, startedAt, query).nextOrNull()
                }
            }
        } else {
            // Filter documents in `sip_call_index` collections
            findInSipIndexBySearchRequest(createdAt, terminatedAt, query)
        }

        // Search and correlate calls using filtered documents
        return SearchIterator(createdAt, matchedDocuments).map { correlatedCall ->
            return@map SearchResponse().apply {
                val firstLeg = correlatedCall.legs.first()

                this.createdAt = firstLeg.getLong("created_at")
                firstLeg.getLong("terminated_at")?.let { this.terminatedAt = it }

                method = "INVITE"
                state = firstLeg.getString("state")
                caller = correlatedCall.legs.joinToString(" - ") { leg -> leg.getString("caller") }
                callee = correlatedCall.legs.joinToString(" - ") { leg -> leg.getString("callee") }
                callId = correlatedCall.legs.map { leg -> leg.getString("call_id") }.toSet()

                firstLeg.getInteger("duration")?.let { duration = it }
                firstLeg.getString("error_code")?.let { errorCode = it }
            }
        }
    }

    private fun findInRtprIndexBySearchRequest(createdAt: Long, terminatedAt: Long, query: String): Iterator<Document> {
        val filters = mutableListOf<Bson>().apply {
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
                mongoClient.find("rtpr_rpt_index", Pair(createdAt, terminatedAt), and(filters)),
                mongoClient.find("rtpr_rtcp_index", Pair(createdAt, terminatedAt), and(filters))
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

    inner class SearchIterator(private val createdAt: Long, private val matchedDocuments: Iterator<Document?>) : Iterator<CorrelatedCall> {

        private var next: CorrelatedCall? = null
        private val processed = mutableSetOf<String>()

        override fun hasNext(): Boolean {
            if (next != null) return true

            while (matchedDocuments.hasNext()) {
                val matchedDocument: Document = matchedDocuments.next() ?: continue

                // Check if `call_id` is in `processed`
                if (!processed.contains(matchedDocument.getString("call_id"))) {
                    // Create and correlate call
                    val correlatedCall = CorrelatedCall().apply {
                        correlate(matchedDocument)
                    }

                    // Update `processed` with new `call_id`
                    correlatedCall.legs.forEach { leg -> processed.add(leg.getString("call_id")) }

                    // Check `created_at` condition and break the loop
                    val firstLeg = correlatedCall.legs.first()
                    if (createdAt <= firstLeg.getLong("created_at")) {
                        next = correlatedCall
                        break
                    }
                }
            }

            return next != null
        }

        override fun next(): CorrelatedCall {
            if (!hasNext()) throw NoSuchElementException()

            val correlatedCall = next!!
            next = null
            return correlatedCall
        }
    }

    inner class CorrelatedCall {

        val legs = TreeSet<Document>(CREATED_AT)

        private val callers = mutableSetOf<Pair<String, String>>()

        fun correlate(leg: Document) {
            val caller = leg.getString("caller")
            val callee = leg.getString("callee")

            if (callers.add(Pair(caller, callee))) {
                val matchedLegs = findInSipIndexByCallerAndCallee(leg)
                correlate(leg, matchedLegs)

                if (useXCorrelationHeader) {
                    findInSipIndexByCallIdsAndXCallIds().forEach { correlate(it) }
                }
            }
        }

        private fun findInSipIndexByCallerAndCallee(leg: Document): List<Document> {
            val createdAt = leg.getLong("created_at")

            val caller = leg.getString("caller")
            val callee = leg.getString("callee")

            val filters = mutableListOf<Bson>().apply {
                // Time filters
                add(gte("created_at", createdAt - aggregationTimeout))
                add(lte("created_at", createdAt + aggregationTimeout))

                // Main filters
                add(eq("caller", caller))
                add(eq("callee", callee))
            }

            return mongoClient.find("sip_call_index", Pair(createdAt - aggregationTimeout, createdAt + aggregationTimeout), and(filters)).asSequence().toList()
        }

        private fun findInSipIndexByCallIdsAndXCallIds(): List<Document> {
            val createdAt = legs.first().getLong("created_at")
            val terminatedAt = legs.first().getLong("terminated_at")

            val callIds = legs.map { it.getString("call_id") }
            val xCallIds = legs.mapNotNull { it.getString("x_call_id") }

            val filters = mutableListOf<Bson>().apply {
                // Time filters
                add(gte("created_at", createdAt - aggregationTimeout))
                add(lte("created_at", (terminatedAt ?: createdAt) + aggregationTimeout))

                // Main filters
                if (xCallIds.isNotEmpty()) {
                    add(or (
                            `in`("x_call_id", callIds),
                            `in`("call_id", xCallIds),
                            `in`("x_call_id", xCallIds)
                    ))
                } else {
                    add(`in`("x_call_id", callIds))
                }
            }

            return mongoClient.find("sip_call_index", Pair(createdAt - aggregationTimeout, (terminatedAt ?: createdAt) + aggregationTimeout), and(filters)).asSequence().toList()
        }


        private fun correlate(leg: Document, matchedLegs: List<Document>) {
            if (legs.size >= maxLegs || !legs.add(leg)) return

            matchedLegs.filter { matchedLeg ->
                val createdAt = leg.getLong("created_at")
                val terminatedAt = leg.getLong("terminated_at")

                // Time filters
                val filterByTime = if (terminatedAt == null) {
                    // Call is still `in progress`
                    createdAt - terminationTimeout >= matchedLeg.getLong("created_at")
                            && createdAt + terminationTimeout <= matchedLeg.getLong("created_at")
                } else {
                    terminatedAt >= matchedLeg.getLong("created_at")
                            && createdAt <= matchedLeg.getLong("terminated_at")
                }

                // Host filters
                val filterBySrcHost = leg.getString("src_host")?.let { it == matchedLeg.getString("dst_host") }
                        ?: leg.getString("src_addr") == matchedLeg.getString("dst_addr")

                val filterByDstHost = leg.getString("dst_host")?.let { it == matchedLeg.getString("src_host") }
                        ?: leg.getString("dst_addr") == matchedLeg.getString("src_addr")

                // Combine and apply all the filters
                return@filter filterByTime && (filterBySrcHost || filterByDstHost)
            }.forEach { matchedLeg ->
                correlate(matchedLeg, matchedLegs)
            }
        }
    }
}