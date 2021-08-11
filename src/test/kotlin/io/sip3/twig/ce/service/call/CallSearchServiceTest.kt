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

import com.mongodb.client.MongoClients
import io.sip3.commons.domain.Attribute
import io.sip3.twig.ce.MongoExtension
import io.sip3.twig.ce.domain.SearchRequest
import io.sip3.twig.ce.mongo.MongoClient
import io.sip3.twig.ce.service.attribute.AttributeService
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ContextConfiguration

@ExtendWith(MongoExtension::class)
@SpringBootTest(classes = [MongoClient::class, CallSearchService::class])
@ContextConfiguration(initializers = [MongoExtension.MongoDbInitializer::class])
class CallSearchServiceTest {

    companion object {

        val ATTRIBUTES = listOf(
            "sip.caller" to Attribute.TYPE_STRING,
            "sip.call_id" to Attribute.TYPE_STRING,
            "sip.state" to Attribute.TYPE_STRING,
            "rtp.mos" to Attribute.TYPE_NUMBER,
            "rtcp.mos" to Attribute.TYPE_NUMBER,
            "media.mos" to Attribute.TYPE_NUMBER
        ).map { (name, type) ->
            Attribute().apply {
                this.name = name
                this.type = type
                if (type == Attribute.TYPE_STRING) this.options = mutableSetOf()
            }
        }

        val CREATED_AT = 1626775335000

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            MongoClients.create(MongoExtension.MONGO_URI).getDatabase("sip3-test").apply {
                this.javaClass.getResource("/json/calls/CallSearchServiceTest.json")?.let { file ->
                    val json = Document.parse(file.readText())
                    json.keys.forEach { collectionName ->
                        getCollection(collectionName).insertMany(
                            json.getList(collectionName, Document::class.java)
                        )
                    }
                }
            }
        }
    }

    @MockBean
    lateinit var attributeService: AttributeService

    @Autowired
    lateinit var service: CallSearchService

    @Test
    fun `Search by SIP attribute`() {
        // Init
        given(attributeService.list()).willReturn(ATTRIBUTES)

        val request = SearchRequest(CREATED_AT, CREATED_AT + 80000, "sip.caller=101", 50)

        // Execute
        val iterator = service.search(request)

        // Assert
        assertTrue(iterator.hasNext())
        iterator.next().apply {
            assertEquals(CREATED_AT, createdAt)
            assertEquals(CREATED_AT + 60000, terminatedAt)
            assertEquals("INVITE", method)
            assertEquals("answered", state)
            assertEquals("101 - 2000000", caller)
            assertEquals("2909090", callee)
            assertTrue(callId.containsAll(listOf("some-call-id-1", "some-call-id-2", "some-call-id-3")))
            assertEquals(60000, duration)
            assertNull(errorCode)
        }
        assertFalse(iterator.hasNext())
    }

    @Test
    fun `Search by RTP attribute`() {
        // Init
        given(attributeService.list()).willReturn(ATTRIBUTES)

        val request = SearchRequest(CREATED_AT, CREATED_AT + 80000, "rtp.mos>3", 50)

        // Execute
        val iterator = service.search(request)

        // Assert
        assertTrue(iterator.hasNext())
        iterator.next().apply {
            assertEquals(CREATED_AT, createdAt)
            assertEquals(CREATED_AT + 60000, terminatedAt)
            assertEquals("INVITE", method)
            assertEquals("answered", state)
            assertEquals("101 - 2000000", caller)
            assertEquals("2909090", callee)
            assertTrue(callId.containsAll(listOf("some-call-id-1", "some-call-id-2", "some-call-id-3")))
            assertEquals(60000, duration)
            assertNull(errorCode)
        }
        assertFalse(iterator.hasNext())
    }

    @Test
    fun `Search by RTCP attribute`() {
        // Init
        given(attributeService.list()).willReturn(ATTRIBUTES)

        val request = SearchRequest(CREATED_AT, CREATED_AT + 80000, "rtcp.mos>3", 50)

        // Execute
        val iterator = service.search(request)

        // Assert
        assertTrue(iterator.hasNext())
        iterator.next().apply {
            assertEquals(CREATED_AT, createdAt)
            assertEquals(CREATED_AT + 60000, terminatedAt)
            assertEquals("INVITE", method)
            assertEquals("answered", state)
            assertEquals("101 - 2000000", caller)
            assertEquals("2909090", callee)
            assertTrue(callId.containsAll(listOf("some-call-id-1", "some-call-id-2", "some-call-id-3")))
            assertEquals(60000, duration)
            assertNull(errorCode)
        }
        assertFalse(iterator.hasNext())
    }

    @Test
    fun `Search by MEDIA attribute`() {
        // Init
        given(attributeService.list()).willReturn(ATTRIBUTES)

        val request = SearchRequest(CREATED_AT, CREATED_AT + 80000, "media.mos>3", 50)

        // Execute
        val iterator = service.search(request)

        // Assert
        assertTrue(iterator.hasNext())
        iterator.next().apply {
            assertEquals(CREATED_AT, createdAt)
            assertEquals(CREATED_AT + 60000, terminatedAt)
            assertEquals("INVITE", method)
            assertEquals("answered", state)
            assertEquals("101 - 2000000", caller)
            assertEquals("2909090", callee)
            assertTrue(callId.containsAll(listOf("some-call-id-1", "some-call-id-2", "some-call-id-3")))
            assertEquals(60000, duration)
            assertNull(errorCode)
        }
        assertFalse(iterator.hasNext())
    }

    @Test
    fun `Validate correlation`() {
        // Init
        given(attributeService.list()).willReturn(ATTRIBUTES)

        val request = SearchRequest(CREATED_AT, CREATED_AT + 80000, "sip.state=answered", 50)

        // Execute
        val iterator = service.search(request)

        // Assert
        assertTrue(iterator.hasNext())
        iterator.next().apply {
            assertEquals(CREATED_AT, createdAt)
            assertEquals(CREATED_AT + 60000, terminatedAt)
            assertEquals("INVITE", method)
            assertEquals("answered", state)
            assertEquals("101 - 2000000", caller)
            assertEquals("2909090", callee)
            assertTrue(callId.containsAll(listOf("some-call-id-1", "some-call-id-2", "some-call-id-3")))
            assertEquals(60000, duration)
            assertNull(errorCode)
        }
        iterator.next().apply {
            assertEquals(CREATED_AT + 22, createdAt)
            assertEquals(CREATED_AT + 60000 - 610, terminatedAt)
            assertEquals("INVITE", method)
            assertEquals("answered", state)
            assertEquals("2000001", caller)
            assertEquals("2909091", callee)
            assertTrue(callId.contains("some-call-id-4"))
            assertEquals(59368, duration)
            assertNull(errorCode)
        }
        iterator.next().apply {
            assertEquals(CREATED_AT, createdAt)
            assertNull(terminatedAt)
            assertEquals("INVITE", method)
            assertEquals("answered", state)
            assertEquals("222 - 2111111", caller)
            assertEquals("2999999", callee)
            assertTrue(callId.containsAll(listOf("some-call-id-5", "some-call-id-6")))
            assertNull(duration)
            assertNull(errorCode)
        }
        assertFalse(iterator.hasNext())
    }
}