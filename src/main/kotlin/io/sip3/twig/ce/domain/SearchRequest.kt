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

import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotNull

@Schema(title = "Search Request")
data class SearchRequest(

    @field:Schema(
        required = true,
        title = "From time",
        example = "1581494059704"
    )
    @NotNull
    val createdAt: Long,

    @field:Schema(
        required = true,
        title = "To time",
        example = "1581494069704"
    )
    @NotNull
    val terminatedAt: Long,

    @field:Schema(
        required = true,
        title = "Search query",
        example = "sip.method=INVITE sip.dst_addr=23.08.20.15 sip.state=answered"
    )
    @NotNull
    var query: String,

    @field:Schema(
        required = false,
        title = "Search limit",
        example = "50"
    )
    @NotNull
    val limit: Int?
)