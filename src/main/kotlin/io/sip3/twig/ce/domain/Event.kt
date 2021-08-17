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

@ApiModel(value = "Event")
data class Event(

    @ApiModelProperty(
        position = 0,
        required = true,
        notes = "Event timestamp",
        example = "1581383715357"
    )
    val timestamp: Long,

    @ApiModelProperty(
        position = 1,
        required = true,
        notes = "Source of event",
        example = "192.168.10.5"
    )
    val src: String,

    @ApiModelProperty(
        position = 2,
        required = true,
        notes = "Destination for event",
        example = "PBX"
    )
    val dst: String,

    @ApiModelProperty(
        position = 3,
        required = true,
        notes = "Type of Event",
        example = "SIP",
        allowableValues = "SIP, RTPR"
    )
    val type: String,

    @ApiModelProperty(
        position = 4,
        required = false,
        notes = "Group of Event",
        example = "some:group:id",
    )
    val group: String?,

    @ApiModelProperty(
        position = 5,
        required = true,
        notes = "Event details"
    )
    val details: Any
)
