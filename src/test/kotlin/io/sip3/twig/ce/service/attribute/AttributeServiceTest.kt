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

package io.sip3.twig.ce.service.attribute

import com.mongodb.client.MongoClients
import io.sip3.commons.domain.Attribute
import io.sip3.twig.ce.MongoExtension
import io.sip3.twig.ce.mongo.MongoClient
import io.sip3.twig.ce.service.media.MediaSessionService
import org.bson.Document
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.verify
import org.mockito.Mockito.times
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(MongoExtension::class)
@SpringBootTest(classes = [MongoClient::class, AttributeService::class])
@ContextConfiguration(initializers = [MongoExtension.MongoDbInitializer::class])
class AttributeServiceTest {

    companion object {

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            MongoClients.create(MongoExtension.MONGO_URI).getDatabase("sip3-test").apply {
                this.javaClass.getResource("/json/attributes/AttributeServiceTest.json")?.let { file ->
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

    @Autowired
    private lateinit var attributeService: AttributeService

    @Test
    fun `Get list of all attributes`() {
        var attributes = attributeService.list()
        assertEquals(7, attributes.size)

        // Test `listAttributes` cache
        attributes = attributeService.list()
        assertEquals(7, attributes.size)

        assertTrue(attributes.any { it.name == "sip.number" })
        assertTrue(attributes.any { it.name == "sip.string" })
        assertTrue(attributes.any { it.name == "sip.src_host" })
        assertTrue(attributes.any { it.name == "sip.dst_host" })
        assertTrue(attributes.any { it.name == "rtp.dst_host" })

        // Validate virtual attributes
        attributes.firstOrNull { it.name == "sip.host" }.let { attr ->
            assertNotNull(attr)
            assertEquals("sip.host", attr!!.name)
            assertEquals(2, attr.options?.size)
            assertTrue(attr.options!!.contains("src_host_1"))
            assertTrue(attr.options!!.contains("dst_host_1"))
        }
        attributes.firstOrNull { it.name == "rtp.host" }.let { attr ->
            assertNotNull(attr)
            assertEquals("rtp.host", attr!!.name)
            assertEquals(1, attr.options?.size)
            assertTrue(attr.options!!.contains("rtp_host_1"))
        }
    }
}