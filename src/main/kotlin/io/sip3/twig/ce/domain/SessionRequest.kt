/*
 * Copyright 2018-2026 SIP3.IO, Corp.
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

@Schema(title = "Session Request")
data class SessionRequest(


    @field:Schema(
        title = "From time",
        example = "1581494059704"
    )
    @JsonProperty("created_at")
    var createdAt: Long? = null,

    @field:Schema(
        title = "To time",
        example = "1581494059704"
    )
    @JsonProperty("terminated_at")
    var terminatedAt: Long? = null,

    @field:Schema(
        title = "Source addresses",
        example = "[\"192.168.9.119\",\"192.168.10.5\"]"
    )
    @JsonProperty("scr_addr")
    var srcAddr: List<String>? = null,

    @field:Schema(
        title = "Destination addresses",
        example = "[\"192.168.10.5\",\"192.168.9.119\"]"

    )
    @JsonProperty("dst_addr")
    var dstAddr: List<String>? = null,

    @field:Schema(
        title = "Source hosts",
        example = "[\"PBX-1\",\"PBX-2\"]"
    )
    @JsonProperty("scr_host")
    var srcHost: List<String>? = null,

    @field:Schema(
        title = "Destination hosts",
        example = "[\"PBX-2\",\"PBX-1\"]"
    )
    @JsonProperty("dst_host")
    var dstHost: List<String>? = null,

    @field:Schema(
        title = "Caller",
        example = "1038883962974"
    )
    var caller: String? = null,

    @field:Schema(
        title = "Callee",
        example = "1038883962974"
    )
    var callee: String? = null,

    @field:Schema(
        title = "Call-ID",
        example = "[\"freesw-call40ikfm\", \"astr-call40rSk0ikfm\"]"
    )
    @JsonProperty("call_id")
    var callId: List<String>? = null,

    @field:Schema(
        title = "Method",
        example = "[\"INVITE\"]"
    )
    var method: List<String>? = null,

    @field:Schema(
        title = "State",
        example = "[\"answered\",\"failed\"]"
    )
    var state: List<String>? = null,

    @field:Schema(
        title = "Error code",
        example = "[401, 403, 407]"
    )
    @JsonProperty("error_code")
    var errorCode: List<Int>? = null,

    @field:Schema(
        title = "Error type",
        example = "server",
        allowableValues = ["client", "server"]
    )
    @JsonProperty("error_type")
    var errorType: String? = null,

    @field:Schema(
        title = "Search query",
        example = "sip.setup_time>1s"
    )
    var query: String? = null,

    @field:Schema(
        title = "Limit",
        example = "50"
    )
    var limit: Int? = null
)
