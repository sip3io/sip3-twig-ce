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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Schema(name = "Component Host", description = "Component")
@Document(collection = "components")
data class Component(

    @JsonIgnore
    @Id
    val id: String?,

    @field:Schema(
        required = true,
        title = "Component name",
        type = "string",
        example = "pbx1.captain.sip3.io"
    )
    @field:NotNull
    var name: String,

    @field:Schema(
        required = true,
        title = "Deployment ID",
        type = "string",
        example = "b8991970-21f4-4c1a-9e57-25de01d835ba"
    )
    @field:NotNull
    @field:JsonProperty("deployment_id")
    var deploymentId: String,

    @field:Schema(
        required = true,
        title = "Component type",
        type = "string",
        example = "captain"
    )
    @field:NotNull
    var type: String,

    @field:Schema(
        required = true,
        title = "Component URI",
        example = "[\"udp://127.0.0.1:34567\"]"
    )
    @field:NotNull
    var uri: Set<String>,

    @field:Schema(
        required = true,
        title = "Connected to",
        description = "List of SIP3 components or URI",
        example = "[\"sip3-salto-01\"]"
    )
    @field:NotNull
    @field:JsonProperty("connected_to")
    var connectedTo: Set<String>,

    @field:Schema(
        required = true,
        title = "Registered at",
        description = "Registered at",
        type = "long",
        example = "1676031280000"
    )
    @field:NotNull
    @field:JsonProperty("registered_at")
    var registeredAt: Long,

    @field:Schema(
        required = true,
        title = "Updated at",
        description = "Updated at",
        type = "long",
        example = "1676031280000"
    )
    @field:NotNull
    @field:JsonProperty("updated_at")
    var updatedAt: Long,

    @field:Schema(
        required = true,
        title = "Remote updated at",
        description = "Remote updated at",
        type = "long",
        example = "1676031270000"
    )
    @field:NotNull
    @field:JsonProperty("remote_updated_at")
    var remoteUpdatedAt: Long,

    @field:Schema(
        required = true,
        title = "Configuration",
        description = "Configuration",
        type = "object",
        example = "{\"name\":\"remote-captain\"}"
    )
    var config: org.bson.Document
)