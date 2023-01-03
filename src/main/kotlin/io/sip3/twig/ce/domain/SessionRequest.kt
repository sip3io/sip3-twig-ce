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

package io.sip3.twig.ce.domain

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "Session Request")
data class SessionRequest(


    @field:Schema(
        title = "From time",
        example = "1581494059704"
    )
    var createdAt: Long? = null,

    @field:Schema(
        title = "To time",
        example = "1581494059704"
    )
    var terminatedAt: Long? = null,

    @field:Schema(
        title = "Source addresses",
        example = "[\"192.168.9.119\",\"192.168.10.5\"]"
    )
    var srcAddr: List<String>? = null,

    @field:Schema(
        title = "Destination addresses",
        example = "[\"192.168.10.5\",\"192.168.9.119\"]"

    )
    var dstAddr: List<String>? = null,

    @field:Schema(
        title = "Source hosts",
        example = "[\"PBX-1\",\"PBX-2\"]"
    )
    var srcHost: List<String>? = null,

    @field:Schema(
        title = "Destination hosts",
        example = "[\"PBX-2\",\"PBX-1\"]"
    )
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
    var errorCode: List<Int>? = null,

    @field:Schema(
        title = "Error type",
        example = "server",
        allowableValues = ["client", "server"]
    )
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
