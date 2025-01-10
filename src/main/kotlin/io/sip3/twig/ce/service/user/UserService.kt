/*
 * Copyright 2018-2025 SIP3.IO, Corp.
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

package io.sip3.twig.ce.service.user

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
open class UserService {

    fun getProfile(): Map<String, Any> {
        val authentication = SecurityContextHolder.getContext().authentication
        var authorities = authentication.authorities?.map { it.authority }
        if (authorities.isNullOrEmpty() || authorities.contains("ROLE_ANONYMOUS")) {
            authorities = listOf("ROLE_SIP3_ADMIN")
        }
        return mutableMapOf<String, Any>().apply {
            put("is_authenticated", authentication.isAuthenticated)
            put("name", authentication.name)
            put("authorities", authorities )
        }
    }
}