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

package io.sip3.twig.ce.validators

import java.net.InetAddress
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class IpAddressListValidator : ConstraintValidator<IpAddressList, List<String>> {

    override fun isValid(ips: List<String>?, context: ConstraintValidatorContext?): Boolean {
        return ips?.all { validateIp(it) } ?: true
    }

    private fun validateIp(ip: String): Boolean {
        return when {
            ip.contains("/") -> validateIpAndRange(ip, "/", 0..32)
            ip.contains(":") -> validateIpAndRange(ip, ":", 1..65535)
            else -> validateIpString(ip)
        }
    }

    private fun validateIpAndRange(ip: String, delimiter: String, intRange: IntRange): Boolean {
        val splited = ip.split(delimiter)
        return validateIpString(splited[0]) && splited[1].toIntOrNull() in intRange
    }

    private fun validateIpString(ip: String): Boolean {
        return try {
            InetAddress.getByName(ip) != null
        } catch (e: Exception) {
            false
        }
    }
}