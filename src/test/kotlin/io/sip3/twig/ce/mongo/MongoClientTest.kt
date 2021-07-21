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

package io.sip3.twig.ce.mongo

import com.mongodb.client.MongoClients
import com.mongodb.client.model.Sorts
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(classes = [MongoClient::class])
@ContextConfiguration(initializers = [MongoClientTest.MongoDbInitializer::class])
class MongoClientTest {

    companion object {

        @JvmField
        @Container
        val MONGODB_CONTAINER = MongoDBContainer("mongo:4.4").apply {
            start()
        }

        const val CREATED_AT = 1596326400000    // 2020-08-02 00:00:00 UTC
        const val TERMINATED_AT = 1596499199000 // 2020-08-03 23:59:59 UTC

        val DOCUMENT_1: Document = Document().apply {
            put("name", 1)
            put("key", "key1")
        }
        val DOCUMENT_2: Document = Document().apply {
            put("name", 2)
            put("key", "key2")
        }
        val DOCUMENT_3: Document = Document().apply {
            put("name", 3)
            put("key", "key1")
        }
        val DOCUMENT_4: Document = Document().apply {
            put("name", 4)
            put("key", "key2")
        }
        val DOCUMENT_5: Document = Document().apply {
            put("name", 5)
            put("key", "key1")
        }

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            MongoClients.create(MONGODB_CONTAINER.getReplicaSetUrl("sip3-test")).getDatabase("sip3-test").apply {
                getCollection("test_20200802").insertMany(
                    mutableListOf(
                        DOCUMENT_1, DOCUMENT_2
                    )
                )
                getCollection("test_20200803").insertMany(
                    mutableListOf(
                        DOCUMENT_3, DOCUMENT_4, DOCUMENT_5
                    )
                )
            }
        }
    }

    @Autowired
    lateinit var client: MongoClient

    @Test
    fun `Validate listCollectionNames() by prefix`() {
        // Execute
        val names = client.listCollectionNames("test", Pair(CREATED_AT, TERMINATED_AT))

        // Assert
        assertEquals(2, names.size)
        assertEquals("test_20200802", names.first())
        assertEquals("test_20200803", names.last())
    }

    @Test
    fun `Find Documents by filter with descending sort`() {
        // Init
        val filter = Document().apply {
            put("key", "key1")
        }
        val sort = Sorts.descending("name")

        // Execute
        val iterator = client.find("test", Pair(CREATED_AT, TERMINATED_AT), filter = filter, sort = sort)

        // Assert
        val documents = iterator.asSequence().toList()
        assertEquals(3, documents.size)
        // Assert sort order
        assertEquals(DOCUMENT_1, documents[0])
        assertEquals(DOCUMENT_5, documents[1])
        assertEquals(DOCUMENT_3, documents[2])
    }

    class MongoDbInitializer : ApplicationContextInitializer<ConfigurableApplicationContext?> {
        override fun initialize(configurableApplicationContext: ConfigurableApplicationContext?) {
            TestPropertyValues.of(
                "time-suffix=yyyyMMdd",
                "mongo.uri=${MONGODB_CONTAINER.getReplicaSetUrl("sip3-test")}",
                "mongo.db=sip3-test",
                "mongo.max-execution-time=1000",
                "mongo.batch-size=128"
            ).applyTo(configurableApplicationContext?.environment)
        }
    }
}