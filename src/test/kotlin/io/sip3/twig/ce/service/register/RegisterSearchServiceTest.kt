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

package io.sip3.twig.ce.service.register

import io.sip3.commons.domain.Attribute
import io.sip3.twig.ce.MockitoExtension.any
import io.sip3.twig.ce.domain.SearchRequest
import io.sip3.twig.ce.mongo.MongoClient
import io.sip3.twig.ce.service.attribute.AttributeService
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest
class RegisterSearchServiceTest {

    companion object {

        val ATTRIBUTES = listOf(
            Attribute().apply {
                name = "sip.callee"
                type = Attribute.TYPE_STRING
                options = mutableSetOf()
            },
            Attribute().apply {
                name = "sip.call_id"
                type = Attribute.TYPE_STRING
                options = mutableSetOf()
            }
        )

        val NOW = System.currentTimeMillis()

        // Registration 1 (1/3)
        val LEG_1 = Document().apply {
            put("src_addr", "192.168.8.1")

            put("dst_addr", "192.168.8.254")
            put("dst_host", "pbx")

            put("created_at", NOW)
            put("terminated_at", NOW + 900000 + 60000)
            put("state", "registered")

            put("caller", "101")
            put("callee", "101")
            put("call_id", "some-call-id-1")
        }

        // Registration 1 (2/3)
        val LEG_2 = Document().apply {
            put("src_addr", "10.10.0.1")
            put("src_host", "pbx")

            put("dst_addr", "10.10.0.2")
            put("dst_host", "sbc")

            put("created_at", NOW + 20)
            put("terminated_at", NOW + 900000 + 61000)
            put("state", "registered")

            put("caller", "101")
            put("callee", "101")
            put("call_id", "some-call-id-2")
        }

        // Registration 1 (3/3)
        val LEG_3 = Document().apply {
            put("src_addr", "5.5.5.5")
            put("src_host", "sbc")

            put("dst_addr", "66.66.66.66")
            put("dst_host", "sip3-telecom")

            put("created_at", NOW + 20 + 1)
            put("terminated_at", NOW + 120000 + 6000)
            put("state", "registered")

            put("caller", "101")
            put("callee", "101")
            put("call_id", "some-call-id-3")
        }

        // Registration 2 (1/2)
        val LEG_4 = Document().apply {
            put("src_addr", "192.168.8.2")

            put("dst_addr", "192.168.8.254")
            put("dst_host", "pbx")

            put("created_at", NOW + 20)
            put("terminated_at", NOW + 60000)
            put("state", "registered")

            put("caller", "102")
            put("callee", "102")
            put("call_id", "some-call-id-4")
        }

        // Registration 2 (2/2)
        val LEG_5 = Document().apply {
            put("src_addr", "192.168.8.2")

            put("dst_addr", "192.168.8.254")
            put("dst_host", "pbx")

            put("created_at", NOW + 60000 - 5000)
            put("terminated_at", NOW + 120000)
            put("state", "registered")

            put("caller", "102")
            put("callee", "102")
            put("call_id", "some-call-id-4")
        }

        // Registration 3 (1/2) active
        val LEG_6 = Document().apply {
            put("src_addr", "192.168.8.1")

            put("dst_addr", "192.168.8.254")
            put("dst_host", "pbx")

            put("created_at", NOW)
            put("terminated_at", NOW + 120000)
            put("state", "registered")

            put("caller", "103")
            put("callee", "103")
            put("call_id", "some-call-id-6")
        }

        // Registration 3 (2/2) active
        val LEG_7 = Document().apply {
            put("src_addr", "10.10.0.1")
            put("src_host", "pbx")

            put("dst_addr", "10.10.0.2")
            put("dst_host", "sbc")

            put("created_at", NOW + 20)
            put("terminated_at", NOW + 128000)
            put("state", "registered")

            put("caller", "103")
            put("callee", "103")
            put("call_id", "some-call-id-7")
        }

    }

    @MockBean
    lateinit var client: MongoClient

    @MockBean
    lateinit var attributeService: AttributeService

    @Autowired
    lateinit var service: RegisterSearchService

    @Test
    fun `Search with 1 result`() {
        // Init
        given(attributeService.list()).willReturn(ATTRIBUTES)
        `when`(client.find(any(), any(), any(), any(), any()))
            // Search by SearchRequest
            .thenReturn(sequenceOf(LEG_1, LEG_2, LEG_3).iterator())
            // Search by correlated Registrations for `LEG_1`
            .thenReturn(sequenceOf(LEG_2, LEG_3).iterator())

        val request = SearchRequest(NOW, NOW + 80000, "sip.callee=101", 50)

        // Execute
        val iterator = service.search(request)

        // Assert
        assertTrue(iterator.hasNext())
        iterator.next().apply {
            assertEquals(NOW, createdAt)
            assertEquals(NOW + 900000 + 60000, terminatedAt)
            assertEquals("REGISTER", method)
            assertEquals("registered", state)
            assertEquals("101", caller)
            assertEquals("101", callee)
            assertTrue(callId.containsAll(listOf("some-call-id-1", "some-call-id-2", "some-call-id-3")))
            assertNull(errorCode)
        }
        assertFalse(iterator.hasNext())

        verify(client, times(2)).find(any(), any(), any(), any(), any())
    }

    @Test
    fun `Search with multiply results`() {
        // Init
        given(attributeService.list()).willReturn(ATTRIBUTES)
        `when`(client.find(any(), any(), any(), any(), any()))
            // Search by SearchRequest
            .thenReturn(sequenceOf(LEG_1, LEG_4, LEG_6, LEG_7).iterator())
            // Search by correlated Registrations for `LEG_1`
            .thenReturn(sequenceOf(LEG_2, LEG_3).iterator())
            // Search by correlated Registrations for `LEG_4`
            .thenReturn(sequenceOf(LEG_4).iterator())
            // Search by correlated Registrations for `LEG_6`
            .thenReturn(sequenceOf(LEG_6, LEG_7).iterator())
            // Search by correlated Registrations for `LEG_7`
            .thenReturn(sequenceOf(LEG_6).iterator())

        val request = SearchRequest(NOW, NOW + 80000, "", 50)

        // Execute
        val iterator = service.search(request)

        // Assert
        assertTrue(iterator.hasNext())
        iterator.next().apply {
            assertEquals(NOW, createdAt)
            assertEquals(NOW + 900000 + 60000, terminatedAt)
            assertEquals("REGISTER", method)
            assertEquals("registered", state)
            assertEquals("101", caller)
            assertEquals("101", callee)
            assertTrue(callId.containsAll(listOf("some-call-id-1", "some-call-id-2", "some-call-id-3")))
            assertNull(errorCode)
        }

        assertTrue(iterator.hasNext())
        iterator.next().apply {
            assertEquals(NOW + 20, createdAt)
            assertEquals(NOW + 60000, terminatedAt)
            assertEquals("REGISTER", method)
            assertEquals("registered", state)
            assertEquals("102", caller)
            assertEquals("102", callee)
            assertTrue(callId.contains("some-call-id-4"))
            assertNull(errorCode)
        }

        assertTrue(iterator.hasNext())
        iterator.next().apply {
            assertEquals(NOW, createdAt)
            assertEquals(NOW + 120000, terminatedAt)
            assertEquals("REGISTER", method)
            assertEquals("registered", state)
            assertEquals("103", caller)
            assertEquals("103", callee)
            assertTrue(callId.containsAll(listOf("some-call-id-6", "some-call-id-7")))
            assertNull(errorCode)
        }

        assertFalse(iterator.hasNext())

        verify(client, times(4)).find(any(), any(), any(), any(), any())
    }


    @Nested
    inner class CorrelatedRegistrationTest {

        @Test
        fun `Correlate 2 legs`() {
            // Init
            given(client.find(any(), any(), any(), any(), any())).willReturn(sequenceOf(LEG_1, LEG_2, LEG_3).iterator())
            val processed = mutableSetOf<Document>()

            // Execute
            val correlatedRegistration = service.CorrelatedRegistration()
            correlatedRegistration.correlate(LEG_1, processed)

            // Assert
            verify(client, times(1)).find(any(), any(), any(), any(), any())

            assertEquals(3, correlatedRegistration.legs.size)
            assertEquals(LEG_1, correlatedRegistration.legs.first())
            assertEquals(LEG_3, correlatedRegistration.legs.last())
        }

        @Test
        fun `Correlate 2 sequential registrations`() {
            // Init
            `when`(client.find(any(), any(), any(), any(), any())).thenReturn(sequenceOf(LEG_4, LEG_5).iterator())
            val processed = mutableSetOf<Document>()

            // Execute
            val correlatedRegistration = service.CorrelatedRegistration()
            correlatedRegistration.correlate(LEG_4, processed)

            // Assert
            verify(client, times(1)).find(any(), any(), any(), any(), any())

            assertEquals(1, correlatedRegistration.legs.size)
            assertEquals(LEG_4, correlatedRegistration.legs.first())
        }
    }

    @AfterEach
    fun resetMocks() {
        reset(client)
    }
}
