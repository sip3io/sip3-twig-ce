/*
 * Copyright 2018-2022 SIP3.IO, Corp.
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

package io.sip3.twig.ce.domain

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel(value = "Search Response")
open class SearchResponse {

    @ApiModelProperty(
        position = 0,
        required = true,
        notes = "Created at",
        example = "1581494059704"
    )
    var createdAt: Long = 0

    @ApiModelProperty(
        position = 1,
        required = false,
        notes = "Terminated at",
        example = "1581494069704"
    )
    var terminatedAt: Long? = null

    @ApiModelProperty(
        position = 2,
        required = true,
        notes = "Method",
        example = "INVITE"
    )
    lateinit var method: String

    @ApiModelProperty(
        position = 3,
        required = true,
        notes = "State",
        example = "Answered"
    )
    lateinit var state: String

    @ApiModelProperty(
        position = 4,
        required = true,
        notes = "Caller",
        example = "Alice"
    )
    lateinit var caller: String

    @ApiModelProperty(
        position = 5,
        required = true,
        notes = "Callee",
        example = "Bob"
    )
    lateinit var callee: String

    @ApiModelProperty(
        position = 6,
        required = true,
        notes = "Call IDs",
        example = "[73d68dd8548211eab1d1047d7bbbc100, 73d68dd854821147d7bbbc100eab1d10]"
    )
    lateinit var callId: Set<String>

    @ApiModelProperty(
        position = 7,
        required = false,
        notes = "Call duration",
        example = "60000"
    )
    var duration: Int? = null

    @ApiModelProperty(
        position = 8,
        required = false,
        notes = "Error code",
        example = "404"
    )
    var errorCode: String? = null
}