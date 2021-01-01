/*
 * Copyright 2018-2021 SIP3.IO, Inc.
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

data class SessionRequest(

    var createdAt: Long? = null,
    var terminatedAt: Long? = null,
    var srcAddr: List<String>? = null,
    var dstAddr: List<String>? = null,
    var srcHost: List<String>? = null,
    var dstHost: List<String>? = null,
    var caller: String? = null,
    var callee: String? = null,
    var callId: List<String>? = null,
    var method: List<String>? = null,
    var state: List<String>? = null,
    var errorCode: List<Int>? = null,
    var errorType: String? = null,
    var query: String? = null,
    var limit: Int? = null
)
