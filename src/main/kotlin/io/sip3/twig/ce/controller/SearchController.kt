/*
 * Copyright 2018-2021 SIP3.IO, Corp.
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
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiResponse
import io.swagger.annotations.ApiResponses
import org.apache.logging.log4j.util.Strings
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

@Api(
    tags = ["Search API"]
)
@RestController
@RequestMapping("/search")
class SearchController {

    companion object {

        val SIP_METHOD_REGEX = Regex("sip.method=(\\w*)")
    }

    @Value("\${session.default-limit}")
    private var defaultLimit: Int = 50

    @Autowired
    private lateinit var serviceLocator: ServiceLocator

    @Autowired
    private lateinit var searchRequestValidator: SearchRequestValidator

    @ApiOperation(
        position = 0,
        value = "Search sessions",
        produces = "application/json"
    )
    @ApiResponses(
        value = [
            ApiResponse(code = 200, message = "Returns search results"),
            ApiResponse(code = 400, message = "Bad request"),
            ApiResponse(code = 500, message = "InternalServerError"),
            ApiResponse(code = 504, message = "ConnectionTimeoutError")
        ]
    )
    @PostMapping
    fun search(@Valid @RequestBody request: SearchRequest): List<SearchResponse> {
        searchRequestValidator.validate(request)

        val searches = SIP_METHOD_REGEX.findAll(request.query)
            .map { match -> match.groupValues[1] }
            .mapNotNull { method -> serviceLocator.searchService(method) }
            .ifEmpty { serviceLocator.searchServices().asSequence() }
            .map { service -> service.search(request) }
            .toList()
            .toTypedArray()

        return IteratorUtil.merge(*searches)
            .asSequence()
            .take(request.limit ?: defaultLimit)
            .toList()
    }
}

@Component
open class SearchRequestValidator {

    companion object {

        const val SIP_METHOD_INVITE = "sip.method=INVITE"

        val EXCLUSIVE_ATTRIBUTES = listOf("sip.", "rtp.", "rtcp.")
    }

    open fun validate(request: SearchRequest) {
        val query = request.query
            .replace(SIP_METHOD_INVITE, Strings.EMPTY)

        if (EXCLUSIVE_ATTRIBUTES.count { query.contains(it) } > 1) {
            throw UnsupportedOperationException("Complex search by `sip.`, `rtp.`, `rtcp.`, filters is not supported.")
        }
    }
}
