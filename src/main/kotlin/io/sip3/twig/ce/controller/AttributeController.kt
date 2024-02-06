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

package io.sip3.twig.ce.controller

import io.sip3.commons.domain.Attribute
import io.sip3.twig.ce.service.attribute.AttributeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Attributes API", description = "Attribute Controller")
@RestController
@RequestMapping("/attributes")
class AttributeController {

    @Autowired
    private lateinit var attributeService: AttributeService

    @Operation(summary = "List attributes")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns attributes"),
        ApiResponse(responseCode = "500", description = "InternalServerError"),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError")
    )
    @GetMapping(
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun list(): Collection<Attribute> {
        return attributeService.list()
    }
}