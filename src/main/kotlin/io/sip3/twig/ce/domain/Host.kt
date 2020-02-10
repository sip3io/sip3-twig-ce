/*
 * Copyright 2018-2020 SIP3.IO, Inc.
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
import io.sip3.twig.ce.validators.IpAddressList
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import javax.validation.constraints.NotNull

@ApiModel(
        value = "Host",
        description = "Object describes host"
)
@Document(collection = "hosts")
data class Host(

        @JsonIgnore
        @Id
        val id: String?,

        @ApiModelProperty(
                position = 0,
                dataType = "String",
                required = true,
                notes = "Host name",
                example = "sip.sbc.example.com"
        )
        @NotNull
        var name: String,

        @ApiModelProperty(
                position = 1,
                dataType = "String",
                required = false,
                notes = "SIP network IP addresses",
                example = "[\"192.168.10.10\", \"192.168.10.11:5061\", \"192.168.10.0/24\"]"
        )
        @IpAddressList
        var sip: List<String>?,

        @ApiModelProperty(
                position = 2,
                dataType = "String",
                required = false,
                notes = "RTP network IP addresses",
                example = "[\"192.168.10.10\", \"192.168.10.11:32766\", \"192.168.10.0/24\"]"
        )
        @IpAddressList
        var media: List<String>?
)