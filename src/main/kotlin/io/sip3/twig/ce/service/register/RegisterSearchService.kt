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

package io.sip3.twig.ce.service.register

import com.mongodb.client.model.Filters.*
import io.sip3.twig.ce.domain.SearchRequest
import io.sip3.twig.ce.domain.SearchResponse
import io.sip3.twig.ce.service.SearchService
import mu.KotlinLogging
import org.bson.Document
import org.bson.conversions.Bson
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component
open class RegisterSearchService : SearchService() {

    private val logger = KotlinLogging.logger {}

    companion object {

        val CREATED_AT = compareBy<Document>(
            { d -> d.getLong("created_at") },
            { d -> d.getString("dst_addr") }
        )
    }

    @Value("\${session.register.max-legs:\${session.register.max_legs:10}}")
    private var maxLegs: Int = 10

    @Value("\${session.register.aggregation-timeout:\${session.register.aggregation_timeout:10000}}")
    private var aggregationTimeout: Long = 10000

    @Value("\${session.register.duration-timeout:\${session.register.duration_timeout:900000}}")
    private var durationTimeout: Long = 900000

    override fun search(request: SearchRequest): Iterator<SearchResponse> {
        // Return EmptyIterator for query with unsupported search attributes
        if (request.query.contains(Regex("rtp\\.|rtcp\\."))) return Collections.emptyIterator()

        val processed = mutableSetOf<Document>()
        return findInSipIndexBySearchRequest(request).asSequence()
            .filterNot { processed.contains(it) }
            .map { leg ->
                return@map CorrelatedRegistration().apply {
                    correlate(leg, processed)
                    processed.addAll(legs)
                }
            }
            .map { correlatedRegistration ->
                return@map SearchResponse().apply {
                    val firstLeg = correlatedRegistration.legs.firstOrNull {
                        it.getString("src_host") == null
                    } ?: correlatedRegistration.legs.first()

                    this.createdAt = firstLeg.getLong("created_at")
                    this.terminatedAt = firstLeg.getLong("terminated_at")

                    method = "REGISTER"
                    state = firstLeg.getString("state")
                    caller = firstLeg.getString("caller")
                    callee = firstLeg.getString("callee")
                    callId = correlatedRegistration.legs.map { leg -> leg.getString("call_id") }.toSet()

                    firstLeg.getString("error_code")?.let { errorCode = it }
                }
            }
            .iterator()
    }

    private fun findInSipIndexBySearchRequest(request: SearchRequest): Iterator<Document> {
        request.apply {
            val filters = mutableListOf<Bson>().apply {
                // Time filters
                add(lte("created_at", terminatedAt))
                add(gte("terminated_at", createdAt))

                // Main filters
                query.split(" ")
                    .asSequence()
                    .filterNot { it.isBlank() }
                    .filterNot { it.startsWith("sip.method") }
                    .map { filter(it) }
                    .toList()
                    .forEach { add(it) }
            }

            return mongoClient.find("sip_register_index", Pair(createdAt - durationTimeout, terminatedAt), and(filters), limit = limit)
        }
    }

    inner class CorrelatedRegistration {

        val legs = TreeSet(CREATED_AT)

        fun correlate(leg: Document, processed: MutableSet<Document>) {
            val matchedLegs = findInSipIndexByDocument(leg).asSequence()
                .filterNot { processed.contains(it) }
                .toList()

            correlate(leg, matchedLegs, processed)
        }

        private fun findInSipIndexByDocument(leg: Document): Iterator<Document> {
            val createdAt = leg.getLong("created_at")
            val terminatedAt = leg.getLong("terminated_at")

            val filters = mutableListOf<Bson>().apply {
                // Time filters
                add(gte("created_at", createdAt - aggregationTimeout))
                add(lte("created_at", createdAt + aggregationTimeout))

                // Main filters
                add(eq("state", leg.getString("state")))
                add(eq("caller", leg.getString("caller")))
                add(eq("callee", leg.getString("callee")))
                add(or(
                    leg.getString("src_host")?.let { eq("dst_host", it) } ?: eq("dst_addr", leg.getString("src_addr")),
                    leg.getString("dst_host")?.let { eq("src_host", it) } ?: eq("src_addr", leg.getString("dst_addr")),
                    eq("call_id", leg.getString("call_id"))
                ))
            }

            return mongoClient.find("sip_register_index", Pair(createdAt - aggregationTimeout, terminatedAt + aggregationTimeout), and(filters))
        }

        private fun correlate(leg: Document, matchedLegs: List<Document>, processed: MutableSet<Document>) {
            // Exclude leg correlation for `registered` state with time intersection
            if (leg.getString("state") == "registered") {
                if (legs.any { correlatedLeg ->
                        correlatedLeg.getString("state") == "registered"
                                && correlatedLeg.getString("src_addr") == leg.getString("src_addr")
                                && correlatedLeg.getString("dst_addr") == leg.getString("dst_addr")
                                && correlatedLeg.getLong("terminated_at") >= leg.getLong("created_at")
                                && correlatedLeg.getLong("created_at") <= leg.getLong("terminated_at")
                    }) {
                    processed.add(leg)
                    return
                }
            }

            if (legs.size >= maxLegs || !legs.add(leg)) return

            val createdAt = leg.getLong("created_at")
            val terminatedAt = leg.getLong("terminated_at")

            matchedLegs.filter { matchedLeg ->
                // Time filters
                val filterByTime = terminatedAt >= matchedLeg.getLong("created_at")
                        && createdAt <= matchedLeg.getLong("terminated_at")

                // Host filters
                val filterBySrcHost = leg.getString("src_host")?.let { it == matchedLeg.getString("dst_host") }
                    ?: (leg.getString("src_addr") == matchedLeg.getString("dst_addr"))

                val filterByDstHost = leg.getString("dst_host")?.let { it == matchedLeg.getString("src_host") }
                    ?: (leg.getString("dst_addr") == matchedLeg.getString("src_addr"))

                // Combine and apply all filters
                return@filter filterByTime && (filterBySrcHost || filterByDstHost)
            }.forEach { matchedLeg ->
                correlate(matchedLeg, matchedLegs, processed)
            }
        }
    }
}