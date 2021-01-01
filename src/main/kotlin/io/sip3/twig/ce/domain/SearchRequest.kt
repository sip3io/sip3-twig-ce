/*
 * Copyright 2018-2021 SIP3.IO, Inc.
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
import javax.validation.constraints.NotNull

@ApiModel(value = "Search Request")
data class SearchRequest(

    @ApiModelProperty(
        position = 0,
        required = true,
        notes = "From time",
        example = "1581494059704"
    )
    @NotNull
    val createdAt: Long,

    @ApiModelProperty(
        position = 1,
        required = true,
        notes = "To time",
        example = "1581494069704"
    )
    @NotNull
    val terminatedAt: Long,

    @ApiModelProperty(
        position = 2,
        required = true,
        notes = "Search query",
        example = "sip.method=INVITE ip.dst_addr=23.08.20.15 sip.state=answered"
    )
    @NotNull
    var query: String,

    @ApiModelProperty(
        position = 3,
        required = false,
        notes = "Search limit",
        example = "50"
    )
    @NotNull
    val limit: Int?
)