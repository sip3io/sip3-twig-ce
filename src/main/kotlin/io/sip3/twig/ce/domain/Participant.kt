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

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "Participant")
data class Participant(

    @field:Schema(
        required = true,
        title = "Name",
        example = "192.168.10.5"
    )
    val name: String,

    @field:Schema(
        required = true,
        title = "Type of Participant",
        example = "HOST",
        allowableValues = ["HOST"]
    )
    val type: String,

    @field:Schema(
        required = false,
        title = "Participant details"
    )
    val details: Any?
)