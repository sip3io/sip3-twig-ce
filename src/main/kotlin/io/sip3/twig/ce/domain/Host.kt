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

package io.sip3.twig.ce.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Schema(name = "Host", description = "Host")
@Document(collection = "hosts")
data class Host(

    @JsonIgnore
    @Id
    val id: String?,

    @field:Schema(
        required = true,
        title = "Host name",
        description = "Host name",
        type = "string",
        example = "sip.sbc.example.com"
    )
    @NotNull
    var name: String,

    @field:Schema(
        required = true,
        title = "IP addresses",
        description = "IP addresses",
        example = "[\"192.168.10.10\", \"192.168.10.11:5061\", \"192.168.10.0/24\"]"
    )
    var addr: List<String>,

    @field:Schema(
        required = false,
        title = "List of IP Address mapping",
        description = "List of IP Address mapping",
        example = "[{\"source\": \"217.117.177.177\", \"target\": \"192.168.10.10\"}]"
    )
    var mapping: List<AddressMapping> = emptyList(),

    @field:Schema(
        required = false,
        title = "Set of host features",
        description = "Set of host features",
        example = "[\"role:proxy\"]"
    )
    var feature: Set<String> = emptySet(),
)