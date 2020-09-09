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

package io.sip3.twig.ce.util

import io.sip3.twig.ce.util.IpAddressUtil.isValid
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IpAddressUtilTest {

    @Test
    fun `Valid addresses`() {
        assertTrue(isValid("192.168.10.10"))
        assertTrue(isValid("192.168.10.200/5"))
        assertTrue(isValid("192.158.90.90:5060"))

        assertTrue(isValid("0.0.0.0"))
        assertTrue(isValid("255.255.255.255"))
        assertTrue(isValid("192.168.10.200/0"))
        assertTrue(isValid("192.168.10.200/32"))
        assertTrue(isValid("192.158.90.90:1"))
        assertTrue(isValid("192.168.10.200:65535"))
    }

    @Test
    fun `Invalid addresses`() {
        assertFalse(isValid("10.10.10.10:0"))
        assertFalse(isValid("10.10.10.10:65536"))
        assertFalse(isValid("10.10.10.10/33"))
        // TODO: Fix that. No exception, result is 10.10.10.256/127.0.0.1
//        assertFalse(isValid("10.10.10.256"))
        assertFalse(isValid("10.10.10.10/"))
        assertFalse(isValid("10.10.10."))
        assertFalse(isValid("10.10..10"))
        // TODO: Fix that. No exception, result is is.not.ip.address/127.0.0.1
//        assertFalse(isValid("is.not.ip.address"))
    }
}