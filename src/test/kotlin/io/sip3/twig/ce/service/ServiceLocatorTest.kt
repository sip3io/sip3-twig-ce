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

package io.sip3.twig.ce.service

import io.sip3.twig.ce.service.call.CallSearchService
import io.sip3.twig.ce.service.call.CallSessionService
import io.sip3.twig.ce.service.register.RegisterSearchService
import io.sip3.twig.ce.service.register.RegisterSessionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest(
    classes = [
        CallSearchService::class,
        RegisterSearchService::class,
        CallSessionService::class,
        RegisterSessionService::class,
        ServiceLocator::class
    ]
)
class ServiceLocatorTest {

    @MockBean
    private lateinit var callSearchService: CallSearchService

    @MockBean
    private lateinit var registerSearchService: RegisterSearchService

    @MockBean
    private lateinit var callSessionService: CallSessionService

    @MockBean
    private lateinit var registerSessionService: RegisterSessionService

    @Autowired
    private lateinit var serviceLocator: ServiceLocator

    @Test
    fun `Validate 'searchServices()' method`() {
        assertEquals(2, serviceLocator.searchServices().size)
    }

    @Test
    fun `Validate 'searchService()' method`() {
        assertEquals(callSearchService, serviceLocator.searchService("INVITE"))
        assertEquals(registerSearchService, serviceLocator.searchService("REGISTER"))
        assertThrows<IllegalArgumentException> { serviceLocator.searchService("MESSAGE") }
    }

    @Test
    fun `Validate 'sessionService()' method`() {
        assertEquals(callSessionService, serviceLocator.sessionService("INVITE"))
        assertEquals(registerSessionService, serviceLocator.sessionService("REGISTER"))
        assertThrows<IllegalArgumentException> { serviceLocator.sessionService("MESSAGE") }
    }
}
