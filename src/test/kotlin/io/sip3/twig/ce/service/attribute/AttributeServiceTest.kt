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

package io.sip3.twig.ce.service.attribute

import io.sip3.commons.domain.Attribute
import io.sip3.twig.ce.mongo.MongoClient
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.verify
import org.mockito.Mockito.times
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest
class AttributeServiceTest {

    @MockBean
    private lateinit var mongoClient: MongoClient

    @Autowired
    private lateinit var attributeService: AttributeService

    @Test
    fun `Get list of all attributes`() {
        val collections = listOf("attributes")

        val attribute1 = Document().apply {
            put("name", "number")
            put("type", Attribute.TYPE_NUMBER)
        }
        val attribute2 = Document().apply {
            put("name", "string")
            put("type", Attribute.TYPE_STRING)
            put("options", listOf("option1", "option2"))
        }

        given(mongoClient.listCollectionNames("attributes"))
            .willReturn(collections)
        given(mongoClient.find(collections))
            .willReturn(listOf(attribute1, attribute2).listIterator())

        var attributes = attributeService.list()
        assertEquals(2, attributes.size)

        // Test `listAttributes` cache
        attributes = attributeService.list()
        assertEquals(2, attributes.size)
        verify(mongoClient, times(1)).find(collections)
    }
}