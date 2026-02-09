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

package io.sip3.twig.ce.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
open class ServiceLocator {

    @Autowired
    lateinit var searchServices: Map<String, SearchService>

    @Autowired
    lateinit var sessionServices: Map<String, SessionService>

    open fun searchServices(): Collection<SearchService> {
        return searchServices.values
    }

    open fun searchService(method: String): SearchService {
        return when (method) {
            "INVITE" -> searchServices["callSearchService"]
            "REGISTER" -> searchServices["registerSearchService"]
            else -> null
        } ?: throw IllegalArgumentException("SearchService for `$method` not found.")
    }

    open fun sessionService(method: String): SessionService {
        return when (method) {
            "INVITE" -> sessionServices["callSessionService"]
            "REGISTER" -> sessionServices["registerSessionService"]
            else -> null
        } ?: throw IllegalArgumentException("SessionService for `$method` not found.")
    }
}