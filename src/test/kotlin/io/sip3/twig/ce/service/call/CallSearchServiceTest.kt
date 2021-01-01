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

package io.sip3.twig.ce.service.call

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
import java.util.*

@SpringBootTest
class CallSearchServiceTest {

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
            },
            Attribute().apply {
                name = "rtp.mos"
                type = Attribute.TYPE_NUMBER
            },
            Attribute().apply {
                name = "rtcp.mos"
                type = Attribute.TYPE_NUMBER
            }
        )

        val NOW = System.currentTimeMillis()

        // Call 1 (1/3)
        val LEG_1 = Document().apply {
            put("src_addr", "192.168.8.1")

            put("dst_addr", "192.168.8.254")
            put("dst_host", "pbx")

            put("created_at", NOW)
            put("terminated_at", NOW + 60000)
            put("answered_at", NOW + 10000)
            put("duration", 60000)
            put("state", "answered")

            put("caller", "101")
            put("callee", "2909090")
            put("call_id", "some-call-id-1")
        }

        // Call 1 (2/3)
        val LEG_2 = Document().apply {
            put("src_addr", "10.10.0.1")
            put("src_host", "pbx")

            put("dst_addr", "10.10.0.2")
            put("dst_host", "sbc")

            put("created_at", NOW + 20)
            put("terminated_at", NOW + 60000 - 600)
            put("answered_at", NOW + 10000 + 10)
            put("state", "answered")
            put("duration", 59380)

            put("caller", "2000000")
            put("callee", "2909090")
            put("call_id", "some-call-id-2")
            put("x_call_id", "some-call-id-1")
        }

        // Call 1 (3/3)
        val LEG_3 = Document().apply {
            put("src_addr", "5.5.5.5")

            put("dst_addr", "66.66.66.66")
            put("dst_host", "sip3-telecom")

            put("created_at", NOW + 20 + 2)
            put("terminated_at", NOW + 60000 - 610)
            put("answered_at", NOW + 10000 + 10)
            put("duration", 59368)
            put("state", "answered")

            put("caller", "2000000")
            put("callee", "2909090")
            put("call_id", "some-call-id-3")
            put("x_call_id", "some-call-id-1")
        }

        // Call 2 (1/1)
        val LEG_4 = Document().apply {
            put("src_addr", "5.5.6.6")
            put("src_host", "sip3-telecom")

            put("dst_addr", "66.66.66.66")

            put("created_at", NOW + 20 + 2)
            put("terminated_at", NOW + 60000 - 610)
            put("answered_at", NOW + 10000 + 10)
            put("duration", 59368)
            put("state", "answered")

            put("caller", "2000000")
            put("callee", "2909090")
            put("call_id", "some-call-id-4")
        }

        // Call 3 (1/2) in progress
        val LEG_5 = Document().apply {
            put("src_addr", "192.168.8.2")

            put("dst_addr", "192.168.8.254")
            put("dst_host", "pbx")

            put("created_at", NOW)
            put("answered_at", NOW + 10000)
            put("state", "answered")

            put("caller", "222")
            put("callee", "2999999")
            put("call_id", "some-call-id-5")
        }

        // Call 3 (2/2) in progress
        val LEG_6 = Document().apply {
            put("src_addr", "10.10.0.1")
            put("src_host", "pbx")

            put("dst_addr", "10.10.0.2")
            put("dst_host", "sbc")

            put("created_at", NOW + 20)
            put("answered_at", NOW + 10000 + 10)
            put("state", "answered")

            put("caller", "2111111")
            put("callee", "2999999")
            put("call_id", "some-call-id-6")
            put("x_call_id", "some-call-id-5")
        }

        val RTPR_1 = Document().apply {
            put("started_at", NOW + 20)
            put("call_id", "some-call-id-1")
        }
    }

    @MockBean
    lateinit var client: MongoClient

    @MockBean
    lateinit var attributeService: AttributeService

    @Autowired
    lateinit var service: CallSearchService

    @Test
    fun `Search by SIP attribute`() {
        // Init
        given(attributeService.list()).willReturn(ATTRIBUTES)
        `when`(client.find(any(), any(), any(), any(), any()))
            // Search by SearchRequest
            .thenReturn(sequenceOf(LEG_1).iterator())
            // Search by callee and caller for `LEG_2`
            .thenReturn(sequenceOf(LEG_2).iterator())
            // Search by x-call-id for `LEG_1`
            .thenReturn(sequenceOf(LEG_3).iterator())
            // Search by callee and caller for `LEG_3`
            .thenReturn(sequenceOf(LEG_1).iterator())
            // Search by x-call-id for `LEG_3`
            .thenReturn(Collections.emptyIterator())

        val request = SearchRequest(NOW, NOW + 80000, "sip.callee=222", 50)

        // Execute
        val iterator = service.search(request)

        // Assert
        assertTrue(iterator.hasNext())
        iterator.next().apply {
            assertEquals(NOW, createdAt)
            assertEquals(NOW + 60000, terminatedAt)
            assertEquals("INVITE", method)
            assertEquals("answered", state)
            assertEquals("101 - 2000000", caller)
            assertEquals("2909090", callee)
            assertTrue(callId.containsAll(listOf("some-call-id-1", "some-call-id-2", "some-call-id-3")))
            assertEquals(60000, duration)
            assertNull(errorCode)
        }
        assertFalse(iterator.hasNext())

        verify(client, times(5)).find(any(), any(), any(), any(), any())
    }

    @Test
    fun `Search by RTP attribute`() {
        // Init
        given(attributeService.list()).willReturn(ATTRIBUTES)
        `when`(client.find(any(), any(), any(), any(), any()))
            // Search by SearchRequest
            .thenReturn(sequenceOf(RTPR_1).iterator())
            // Search by callId SearchRequest
            .thenReturn(sequenceOf(LEG_1).iterator())
            // Search by callee and caller for `LEG_1`
            .thenReturn(Collections.emptyIterator())
            // Search by x-call-id for `LEG_1`
            .thenReturn(Collections.emptyIterator())

        val request = SearchRequest(NOW, NOW + 80000, "rtp.mos>3", 50)

        // Execute
        val iterator = service.search(request)

        // Assert
        assertTrue(iterator.hasNext())
        iterator.next().apply {
            assertEquals(NOW, createdAt)
            assertEquals(NOW + 60000, terminatedAt)
            assertEquals("101", caller)
            assertEquals("2909090", callee)
            assertEquals(1, callId.size)
            assertEquals(callId.first(), "some-call-id-1")
            assertEquals(60000, duration)
            assertNull(errorCode)
        }
        assertFalse(iterator.hasNext())

        verify(client, times(4)).find(any(), any(), any(), any(), any())
    }

    @Test
    fun `Search by RTCP attribute`() {
        // Init
        given(attributeService.list()).willReturn(ATTRIBUTES)
        `when`(client.find(any(), any(), any(), any(), any()))
            // Search by SearchRequest
            .thenReturn(sequenceOf(RTPR_1).iterator())
            // Search by callId SearchRequest
            .thenReturn(sequenceOf(LEG_1).iterator())
            // Search by callee and caller for `LEG_1`
            .thenReturn(Collections.emptyIterator())
            // Search by x-call-id for `LEG_1`
            .thenReturn(Collections.emptyIterator())

        val request = SearchRequest(NOW, NOW + 80000, "rtcp.mos>3", 50)

        // Execute
        val iterator = service.search(request)

        // Assert
        assertTrue(iterator.hasNext())
        iterator.next().apply {
            assertEquals(NOW, createdAt)
            assertEquals(NOW + 60000, terminatedAt)
            assertEquals("101", caller)
            assertEquals("2909090", callee)
            assertEquals(1, callId.size)
            assertEquals(callId.first(), "some-call-id-1")
            assertEquals(60000, duration)
            assertNull(errorCode)
        }
        assertFalse(iterator.hasNext())

        verify(client, times(4)).find(any(), any(), any(), any(), any())
    }

    @Nested
    inner class SearchIteratorTest {

        @Test
        fun `Correlate 2 calls`() {
            // Init
            `when`(client.find(any(), any(), any(), any(), any()))
                // Search by callee and caller for `LEG_1`
                .thenReturn(sequenceOf(LEG_1, LEG_2).iterator())
                // Search by x-call-id for `LEG_1`
                .thenReturn(sequenceOf(LEG_3).iterator())
                // Search by callee and caller for `LEG_3`
                .thenReturn(sequenceOf(LEG_1).iterator())
                // Search by x-call-id for `LEG_3`
                .thenReturn(Collections.emptyIterator())
                // Search by callee and caller for `LEG_5`
                .thenReturn(Collections.emptyIterator())
                // Search by x-call-id for `LEG_5`
                .thenReturn(sequenceOf(LEG_6).iterator())
                // Search by callee and caller for `LEG_6`
                .thenReturn(Collections.emptyIterator())
                // Search by x-call-id for `LEG_6`
                .thenReturn(Collections.emptyIterator())

            // Execute
            val searchIterator = service.SearchIterator(NOW, sequenceOf(LEG_1, LEG_5).iterator())

            //Assert
            assertTrue(searchIterator.hasNext())
            searchIterator.next().legs.apply {
                assertEquals(3, size)
                assertEquals(LEG_1, first())
                assertEquals(LEG_3, last())
            }
            assertTrue(searchIterator.hasNext())
            searchIterator.next().legs.apply {
                assertEquals(2, size)
                assertEquals(LEG_5, first())
                assertEquals(LEG_6, last())
            }

            assertFalse(searchIterator.hasNext())

            verify(client, times(8)).find(any(), any(), any(), any(), any())
        }
    }

    @Nested
    inner class CorrelatedCallTest {

        @Test
        fun `Correlate 2 legs by caller and callee`() {
            // Init
            given(client.find(any(), any(), any(), any(), any())).willReturn(sequenceOf(LEG_1, LEG_2).iterator())

            // Execute
            val correlatedCall = service.CorrelatedCall()
            correlatedCall.correlate(LEG_1)

            // Assert
            assertEquals(2, correlatedCall.legs.size)
            assertEquals(LEG_1, correlatedCall.legs.first())
            assertEquals(LEG_2, correlatedCall.legs.last())
        }

        @Test
        fun `Correlate 2 legs by x-call-id`() {
            // Init
            `when`(client.find(any(), any(), any(), any(), any()))
                // Search by callee and caller return empty iterator
                .thenReturn(Collections.emptyIterator())
                // Search by x-call-id for `LEG_1`
                .thenReturn(sequenceOf(LEG_1, LEG_3).iterator())

            // Execute
            val correlatedCall = service.CorrelatedCall()
            correlatedCall.correlate(LEG_1)

            // Assert
            assertEquals(2, correlatedCall.legs.size)
            assertEquals(LEG_1, correlatedCall.legs.first())
            assertEquals(LEG_3, correlatedCall.legs.last())
        }

        @Test
        fun `Correlate 3 legs`() {
            // Init
            `when`(client.find(any(), any(), any(), any(), any()))
                // Search by callee and caller for `LEG_1`
                .thenReturn(sequenceOf(LEG_1, LEG_2).iterator())
                // Search by x-call-id for `LEG_1`
                .thenReturn(sequenceOf(LEG_3).iterator())
                // Search by callee and caller for `LEG_3`
                .thenReturn(sequenceOf(LEG_3).iterator())
                // Search by x-call-id for `LEG_3`
                .thenReturn(Collections.emptyIterator())

            // Execute
            val correlatedCall = service.CorrelatedCall()
            correlatedCall.correlate(LEG_1)

            // Assert
            verify(client, times(4)).find(any(), any(), any(), any(), any())

            assertEquals(3, correlatedCall.legs.size)
            assertEquals(LEG_1, correlatedCall.legs.first())
            assertEquals(LEG_3, correlatedCall.legs.last())
        }
    }

    @AfterEach
    fun resetMocks() {
        reset(client)
    }
}