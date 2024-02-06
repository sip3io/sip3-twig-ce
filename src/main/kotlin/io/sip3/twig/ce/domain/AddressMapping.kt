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

@Schema(description = "Address Mapping")
data class AddressMapping(

    @field:Schema(
        required = true,
        title = "Source",
        example = "217.117.177.177"
    )
    val source: String,

    @field:Schema(
        required = true,
        title = "Target",
        example = "192.168.10.10"
    )
    val target: String
)
