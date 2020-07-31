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

package io.sip3.twig.ce.service.register

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gte
import com.mongodb.client.model.Filters.lte
import com.mongodb.client.model.Filters.or
import io.sip3.twig.ce.domain.SearchRequest
import io.sip3.twig.ce.domain.SearchResponse
import io.sip3.twig.ce.service.SearchService
import mu.KotlinLogging
import org.bson.Document
import org.bson.conversions.Bson
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component("REGISTER")
open class RegisterSearchService : SearchService() {

    private val logger = KotlinLogging.logger {}

    companion object {

        val CREATED_AT = compareBy<Document>(
                { d -> d.getLong("created_at") },
                { d -> d.getString("dst_addr") }
        )
    }

    @Value("\${session.register.max-legs}")
    private var maxLegs: Int = 10

    @Value("\${session.register.aggregation-timeout}")
    private var aggregationTimeout: Long = 10000

    override fun search(request: SearchRequest): Iterator<SearchResponse> {
        val processed = mutableSetOf<Document>()

        return findInSipIndexBySearchRequest(request).asSequence()
                .filterNot { processed.contains(it) }
                .map { leg ->
                    return@map CorrelatedRegistration().apply {
                        correlate(leg)
                        processed.addAll(legs)
                    }
                }
                .map { correlatedRegistration ->
                    return@map SearchResponse().apply {
                        val firstLeg = correlatedRegistration.legs.first()

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
                add(gte("created_at", createdAt))
                add(lte("created_at", terminatedAt))

                // Main filters
                query.split(" ")
                        .asSequence()
                        .filterNot { it.isBlank() }
                        .filterNot { it.startsWith("rtp.") }
                        .filterNot { it.startsWith("sip.method") }
                        .map { filter(it) }
                        .toList()
                        .forEach { add(it) }
            }

            return mongoClient.find("sip_register_index", Pair(createdAt, terminatedAt), and(filters), limit = limit)
        }
    }

    inner class CorrelatedRegistration {

        val legs = TreeSet(CREATED_AT)

        fun correlate(leg: Document) {
            if (legs.add(leg) && legs.size < maxLegs) {
                findInSipIndexByDocument(leg).forEach { correlate(it) }
            }
        }

        private fun findInSipIndexByDocument(leg: Document): Iterator<Document> {
            val createdAt = leg.getLong("created_at")
            val terminatedAt = leg.getLong("terminated_at")

            val filters = mutableListOf<Bson>().apply {
                // Time filters
                add(lte("created_at", leg.getLong("terminated_at")))
                add(gte("terminated_at", leg.getLong("created_at")))

                // Main filters
                add(eq("state", leg.getString("state")))
                add(eq("caller", leg.getString("caller")))
                add(eq("callee", leg.getString("callee")))
                add(or(
                        leg.getString("src_host")?.let { eq("dst_host", it) } ?: eq("dst_addr", leg.getString("src_addr")),
                        leg.getString("dst_host")?.let { eq("src_host", it) } ?: eq("src_addr", leg.getString("dst_addr"))
                ))
            }

            return mongoClient.find("sip_register_index", Pair(createdAt - aggregationTimeout, terminatedAt + aggregationTimeout), and(filters))
        }
    }
}