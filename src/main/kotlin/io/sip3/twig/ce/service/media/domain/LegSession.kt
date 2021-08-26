/*
 * Copyright 2018-2021 SIP3.IO, Corp.
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

package io.sip3.twig.ce.service.media.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import io.sip3.commons.domain.payload.RtpReportPayload
import kotlin.math.max
import kotlin.math.min

class LegSession {

    var createdAt: Long = Long.MAX_VALUE
    var terminatedAt: Long = Long.MAX_VALUE
    var duration: Int = 0

    lateinit var callId: String

    lateinit var srcAddr: String
    var srcPort: Int = 0
    var srcHost: String? = null
    lateinit var dstAddr: String
    var dstPort: Int = 0
    var dstHost: String? = null

    val codecs = mutableSetOf<Codec>()

    val out = mutableListOf<MediaSession>()
    val `in` = mutableListOf<MediaSession>()

    fun updateTimestamps() {
        createdAt = min(
            out.minOfOrNull { it.createdAt } ?: Long.MAX_VALUE,
            `in`.minOfOrNull { it.createdAt } ?: Long.MAX_VALUE
        )
        terminatedAt = max(
            out.maxOfOrNull { it.terminatedAt } ?: Long.MIN_VALUE,
            `in`.maxOfOrNull { it.terminatedAt } ?: Long.MIN_VALUE
        )
    }

    data class Codec(val name: String, val payloadType: Int) {

        override fun equals(other: Any?): Boolean {
            return this.name  == (other as? Codec)?.name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }
    }
}