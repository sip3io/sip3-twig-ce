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

package io.sip3.twig.ce.controller

import io.sip3.commons.domain.Attribute
import io.sip3.twig.ce.service.AttributeService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiResponse
import io.swagger.annotations.ApiResponses
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import springfox.documentation.swagger2.annotations.EnableSwagger2

@EnableSwagger2
@Api(
        tags = ["Attributes API"]
)
@RestController
@RequestMapping("/attributes")
class AttributeController(private val attributeService: AttributeService) {

    @ApiOperation(
            position = 1,
            value = "List attributes",
            produces = "application/json"
    )
    @ApiResponses(value = [
        ApiResponse(code = 200, message = "Returns attributes"),
        ApiResponse(code = 500, message = "InternalServerError"),
        ApiResponse(code = 504, message = "ConnectionTimeoutError")]
    )
    @GetMapping
    fun list(): Collection<Attribute> {
        return attributeService.list()
    }
}