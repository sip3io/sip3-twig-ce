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

package io.sip3.twig.ce.domain

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "Search Response")
open class SearchResponse {

    @Schema(
        required = true,
        title = "Created at",
        example = "1581494059704"
    )
    @JsonProperty("created_at")
    var createdAt: Long = 0

    @Schema(
        required = false,
        title = "Terminated at",
        example = "1581494069704"
    )
    @JsonProperty("terminated_at")
    var terminatedAt: Long? = null

    @Schema(
        required = true,
        title = "Method",
        example = "INVITE"
    )
    lateinit var method: String

    @Schema(
        required = true,
        title = "State",
        example = "Answered"
    )
    lateinit var state: String

    @Schema(
        required = true,
        title = "Caller",
        example = "Alice"
    )
    lateinit var caller: String

    @Schema(
        required = true,
        title = "Callee",
        example = "Bob"
    )
    lateinit var callee: String

    @Schema(
        required = true,
        title = "Call IDs",
        example = "[\"73d68dd8548211eab1d1047d7bbbc100\", \"73d68dd854821147d7bbbc100eab1d10\"]"
    )
    @JsonProperty("call_id")
    lateinit var callId: Set<String>

    @Schema(
        required = false,
        title = "Call duration",
        example = "60000"
    )
    var duration: Int? = null

    @Schema(
        required = false,
        title = "Error code",
        example = "404"
    )
    @JsonProperty("error_code")
    var errorCode: String? = null

    @Schema(
        required = false,
        title = "Fields",
        example = "{\"field_name\": \"field_value\"}"
    )
    var fields: Map<String, Any?>? = null
}
