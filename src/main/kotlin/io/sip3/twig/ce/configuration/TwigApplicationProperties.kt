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

package io.sip3.twig.ce.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties
open class TwigApplicationProperties {

    var spring: Map<String, Any?>? = null
    var name: String? = null
    var management: Map<String, Any?>? = null
    var server: Map<String, Any?>? = null
    var security: Map<String, Any?>? = null
    var mongo: Map<String, Any?>? = null
    var timeSuffix: String? = null
    var session: Map<String, Any?>? = null
    var cache: Map<String, Any?>? = null
    var springdoc: Map<String, Any?>? = null
}
