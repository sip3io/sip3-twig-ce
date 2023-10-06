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

package io.sip3.twig.ce.controller

import io.sip3.twig.ce.domain.SearchRequest
import io.sip3.twig.ce.domain.SearchResponse
import io.sip3.twig.ce.service.ServiceLocator
import io.sip3.twig.ce.util.IteratorUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.apache.logging.log4j.util.Strings
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Search API", description = "Search Controller")
@RestController
@RequestMapping("/search")
class SearchController {

    companion object {

        val SIP_METHOD_REGEX = Regex("sip.method=(\\w*)")
    }

    @Value("\${session.default-limit:\${session.default_limit:50}}")
    private var defaultLimit: Int = 50

    @Autowired
    private lateinit var serviceLocator: ServiceLocator

    @Autowired
    private lateinit var searchRequestValidator: SearchRequestValidator

    @Operation(summary = "Search sessions")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns search results"),
        ApiResponse(responseCode = "400", description = "Bad request"),
        ApiResponse(responseCode = "500", description = "InternalServerError"),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError")
    )
    @PostMapping(
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun search(@Valid @RequestBody request: SearchRequest): List<SearchResponse> {
        searchRequestValidator.validate(request)

        val searches = SIP_METHOD_REGEX.findAll(request.query)
            .map { match -> match.groupValues[1] }
            .map { method -> serviceLocator.searchService(method) }
            .ifEmpty { serviceLocator.searchServices().asSequence() }
            .map { service -> service.search(request) }
            .toList()
            .toTypedArray()

        return IteratorUtil.merge(*searches)
            .asSequence()
            .take(request.limit ?: defaultLimit)
            .sortedBy(SearchResponse::createdAt)
            .toList()
    }
}

@Component
open class SearchRequestValidator {

    companion object {

        const val SIP_METHOD_INVITE = "sip.method=INVITE"

        val EXCLUSIVE_ATTRIBUTES = listOf("rtp.", "rtcp.")
    }

    open fun validate(request: SearchRequest) {
        val query = request.query
            .replace(SIP_METHOD_INVITE, Strings.EMPTY)

        if (EXCLUSIVE_ATTRIBUTES.count { query.contains(it) } > 1) {
            throw UnsupportedOperationException("Complex search by `rtp.`, `rtcp.`, filters is not supported.")
        }
    }
}
