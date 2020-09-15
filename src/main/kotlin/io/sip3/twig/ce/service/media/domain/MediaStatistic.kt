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

package io.sip3.twig.ce.service.media.domain

import com.fasterxml.jackson.annotation.JsonProperty

open class MediaStatistic {

    var duration = 0
    var mos: Double = 0.0

    @get:JsonProperty("r_factor")
    var rFactor: Double = 0.0

    val packets = Packets()
    val jitter = Jitter()

    class Packets {

        var expected: Int = 0
        var lost: Int = 0
        var received: Int = 0
        var rejected: Int = 0
    }

    class Jitter {

        var min: Double = 0.0
        var max: Double = 0.0
        var avg: Double = 0.0
    }
}