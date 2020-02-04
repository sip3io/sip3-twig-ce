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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpAddressListValidatorTest {

    val validator = IpAddressListValidator()

    @Test
    fun `Valid addresses`() {
        assertTrue("all must be valid",
                validator.isValid(listOf("192.168.10.10", "192.168.10.200/5", "192.158.90.90:5060"), null))
        assertTrue("all range values must be valid",
                validator.isValid(listOf(
                        "0.0.0.0", "255.255.255.255",
                        "192.168.10.200/0", "192.168.10.200/32",
                        "192.158.90.90:1", "192.168.10.200:65535"
                ), null))
        assertTrue(validator.isValid(null, null))
    }

    @Test
    fun `Invalid addresses`() {
        assertFalse("0 port is error", validator.isValid(listOf("10.10.10.10:0"), null))
        assertFalse("port number greater than 65535 is invalid", validator.isValid(listOf("10.10.10.10:65536"), null))
        assertFalse("subnet greater than 32 is invalid", validator.isValid(listOf("10.10.10.10/33"), null))
    }

    @Test
    fun `Out of range error`() {
        assertFalse("Octet Out of Range is error", validator.isValid(listOf("10.10.10.256"), null))
    }

    @Test
    fun `Empty subnet`() {
        assertFalse("EmptySubnet is error", validator.isValid(listOf("10.10.10.10/"), null))
    }
}