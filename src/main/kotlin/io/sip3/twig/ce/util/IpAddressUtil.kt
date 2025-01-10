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

package io.sip3.twig.ce.util

import java.net.InetAddress

object IpAddressUtil {

    fun isValid(ip: String): Boolean {
        return when {
            ip.contains("/") -> validateIpAndRange(ip, "/", 0..32)
            ip.contains(":") -> validateIpAndRange(ip, ":", 1..65535)
            else -> validateIpString(ip)
        }
    }

    private fun validateIpAndRange(ip: String, delimiter: String, intRange: IntRange): Boolean {
        val splitted = ip.split(delimiter)
        return validateIpString(splitted[0]) && splitted[1].toIntOrNull() in intRange
    }

    private fun validateIpString(ip: String): Boolean {
        return try {
            InetAddress.getByName(ip) != null
        } catch (e: Exception) {
            false
        }
    }
}