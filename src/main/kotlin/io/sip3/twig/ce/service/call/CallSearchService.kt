/*
 * Copyright 2018-2024 SIP3.IO, Corp.
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

@Component
open class CallSearchService : SearchService() {

    private val logger = KotlinLogging.logger {}

    companion object {

        val CREATED_AT = compareBy<Document>(
            { d -> d.getLong("created_at") },
            { d -> -1 * (d.getLong("terminated_at") ?: (d.getLong("created_at") + d.getInteger("establish_time"))) },
            { d -> d.getString("dst_addr") },
            { d -> d.getString("call_id") }
        )

        val MEDIA_PREFIX_REGEX = Regex("rtp.|rtcp.")
        val COMMON_ATTR_REGEX = Regex("sip.caller|sip.callee|sip.user|sip.call_id")
    }

    @Value("\${session.use-x-correlation-header:\${session.use_x_correlation_header:true}}")
    protected var useXCorrelationHeader: Boolean = true

    @Value("\${session.call.max-legs:\${session.call.max_legs:10}}")
    protected var maxLegs: Int = 10

    @Value("\${session.call.aggregation-timeout:\${session.call.aggregation_timeout:60000}}")
    protected var aggregationTimeout: Long = 60000

    @Value("\${session.call.termination-timeout:\${session.call.termination_timeout:10000}}")
    protected var terminationTimeout: Long = 10000

    override fun search(request: SearchRequest): Iterator<SearchResponse> {
        val (createdAt, terminatedAt, query) = request

        val hasMediaFilters = query.contains(MEDIA_PREFIX_REGEX)
        val startWithMediaIndex = hasMediaFilters && query.split(" ")
            .filterNot { it.startsWith("sip.method=") }
            .firstOrNull()?.contains(MEDIA_PREFIX_REGEX) ?: false

        val matchedDocuments = if (startWithMediaIndex) {
            // Filter documents in `rtpr_${prefix}_index` collection
            findInRtprIndexBySearchRequest(createdAt, terminatedAt, query).map { document ->
                // Map `rtpr_${prefix}_index` document to `sip_call_index` document
                document.getString("call_id")?.let { callId ->
                    val rtprCreatedAt = document.getLong("created_at")
                    val extendedQuery = "$query sip.call_id=$callId"
                    return@map findInSipIndexBySearchRequest(
                        rtprCreatedAt - aggregationTimeout,
                        rtprCreatedAt + aggregationTimeout,
                        extendedQuery
                    ).nextOrNull()
                }
            }
        } else {
            // Filter documents in `sip_call_index` collection
            findInSipIndexBySearchRequest(createdAt, terminatedAt, query).map { document ->
                if (!hasMediaFilters) return@map document

                // Map `sip_call_index` document to `rtpr_${prefix}_index` document
                document.getString("call_id")?.let { callId ->
                    val sipCreatedAt = document.getLong("created_at")
                    val extendedQuery = "$query sip.call_id=$callId"

                    val rtprSearchResult = findInRtprIndexBySearchRequest(
                        sipCreatedAt - aggregationTimeout,
                        sipCreatedAt + aggregationTimeout,
                        extendedQuery
                    )
                    return@map if (rtprSearchResult.hasNext()) {
                        document
                    } else {
                        null
                    }
                }
            }
        }

        // Search and correlate calls using filtered documents
        return SearchIterator(createdAt, matchedDocuments).map { correlatedCall ->
            return@map SearchResponse().apply {
                val firstLeg = correlatedCall.legs.first()

                this.createdAt = firstLeg.getLong("created_at")
                correlatedCall.legs
                    .mapNotNull { it.getLong("terminated_at") }
                    .maxOrNull()
                    ?.let { this.terminatedAt = it }

                method = "INVITE"
                state = firstLeg.getString("state")
                caller = correlatedCall.legs.map { it.getString("caller") }.toSet().joinToString(" - ")
                callee = correlatedCall.legs.map { it.getString("callee") }.toSet().joinToString(" - ")
                callId = correlatedCall.legs.map { it.getString("call_id") }.toSet()

                firstLeg.getInteger("duration")?.let { duration = it }
                firstLeg.getString("error_code")?.let { errorCode = it }

                if (request.fields.isNotEmpty()) {
                    // Add fields from SIP index
                    val responseFields = mutableMapOf<String, Any?>()
                    request.fields
                        .filter { it.startsWith("sip") }
                        .forEach { field ->
                            responseFields[field] = firstLeg.get(field.substringAfter("."))
                        }
                    fields = responseFields
                }
            }
        }
    }

    protected open fun findInRtprIndexBySearchRequest(createdAt: Long, terminatedAt: Long, query: String): Iterator<Document> {
        val filters = mutableListOf<Bson>().apply {
            // Time filters
            add(gte("created_at", createdAt))
            add(lte("created_at", terminatedAt))

            // Main filters
            query.split(" ")
                .filterNot { it.isBlank() }
                .filterNot { it.startsWith("sip.") && !it.contains(COMMON_ATTR_REGEX) }
                .map { filter(it) }
                .forEach { add(it) }
        }

        val prefixes = mutableListOf<String>().apply {
            if (query.contains("rtp.")) {
                add("rtpr_rtp_index")
            }
            if (query.contains("rtcp.")) {
                add("rtpr_rtcp_index")
            }
        }

        logger.debug { "findInRtprIndexBySearchRequest() filters: $filters" }
        return prefixes.map { mongoClient.find(it, Pair(createdAt, terminatedAt), and(filters)) }
            .toTypedArray()
            .let { IteratorUtil.merge(*it) }
    }

    protected open fun findInSipIndexBySearchRequest(createdAt: Long, terminatedAt: Long, query: String): Iterator<Document> {
        val filters = mutableListOf<Bson>().apply {
            // Time filters
            add(gte("created_at", createdAt))
            add(lte("created_at", terminatedAt))

            // Main filters
            query.split(" ")
                .asSequence()
                .filterNot { it.isBlank() }
                .filterNot { it.startsWith("sip.method") }
                .filter { it.startsWith("sip.") }
                .map { filter(it) }
                .forEach { add(it) }
        }

        logger.debug { "findInSipIndexBySearchRequest() filters: $filters" }
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

        val legs = TreeSet(CREATED_AT)

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
            } else if (legs.size < maxLegs && legs.add(leg)) {
                findInSipIndexByCallIdsAndXCallIds().forEach { correlate(it) }
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

            logger.debug { "findInSipIndexByCallerAndCallee() filters: $filters" }
            return mongoClient.find("sip_call_index", Pair(createdAt - aggregationTimeout, createdAt + aggregationTimeout), and(filters)).asSequence()
                .toList()
        }

        private fun findInSipIndexByCallIdsAndXCallIds(): List<Document> {
            val createdAt = legs.first().getLong("created_at")
            val terminatedAt = legs.first().getLong("terminated_at")
                ?: legs.first().getInteger("establish_time")?.let { createdAt + it }

            val callIds = legs.map { it.getString("call_id") }.toSet()
            val xCallIds = legs.mapNotNull { it.getString("x_call_id") }.toSet()

            val filters = mutableListOf<Bson>().apply {
                // Time filters
                add(gte("created_at", createdAt - aggregationTimeout))
                add(lte("created_at", (terminatedAt ?: createdAt) + aggregationTimeout))

                // Main filters
                if (xCallIds.isNotEmpty()) {
                    add(
                        or(
                            `in`("x_call_id", callIds),
                            `in`("call_id", xCallIds),
                            `in`("x_call_id", xCallIds)
                        )
                    )
                } else {
                    add(`in`("x_call_id", callIds))
                }
            }

            logger.debug { "findInSipIndexByCallIdsAndXCallIds() filters: $filters" }
            val timeRange = Pair(createdAt - aggregationTimeout, (terminatedAt ?: createdAt) + aggregationTimeout)
            return mongoClient.find("sip_call_index", timeRange, and(filters)).asSequence().toList()
        }


        private fun correlate(leg: Document, matchedLegs: List<Document>) {
            if (legs.size >= maxLegs || !legs.add(leg)) return

            val createdAt = leg.getLong("created_at")
            val terminatedAt = leg.getLong("terminated_at") ?: (createdAt + leg.getInteger("establish_time")!!)

            matchedLegs.filter { matchedLeg ->
                // Time filter
                val filterByTime = terminatedAt >= matchedLeg.getLong("created_at")
                        && createdAt <= (matchedLeg.getLong("terminated_at")
                    ?: (matchedLeg.getLong("created_at") + matchedLeg.getInteger("establish_time")))

                // Host filters
                val filterBySrcHost = leg.getString("src_host")?.let { it == matchedLeg.getString("dst_host") }
                    ?: (leg.getString("src_addr") == matchedLeg.getString("dst_addr"))

                val filterByDstHost = leg.getString("dst_host")?.let { it == matchedLeg.getString("src_host") }
                    ?: (leg.getString("dst_addr") == matchedLeg.getString("src_addr"))

                // Combine and apply all the filters
                return@filter filterByTime && (filterBySrcHost || filterByDstHost)
            }.forEach { matchedLeg ->
                correlate(matchedLeg, matchedLegs)
            }
        }
    }
}
