/*
 * Copyright 2018-2024 SIP3.IO, Corp.
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

package io.sip3.twig.ce.service.media

import com.mongodb.client.MongoClients
import io.sip3.twig.ce.MongoExtension
import io.sip3.twig.ce.domain.SessionRequest
import io.sip3.twig.ce.mongo.MongoClient
import org.bson.Document
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration

@ExtendWith(MongoExtension::class)
@SpringBootTest(classes = [MongoClient::class, MediaSessionService::class])
@ContextConfiguration(initializers = [MongoExtension.MongoDbInitializer::class])
class MediaSessionServiceTest {

    companion object {

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            MongoClients.create(MongoExtension.MONGO_URI).getDatabase("sip3-test").apply {
                this.javaClass.getResource("/json/rtpr/MediaSessionServiceTest.json")?.let { file ->
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
    private lateinit var service: MediaSessionService

    @Test
    fun `Validate 'details()' method`() {
        // Init
        val request = SessionRequest().apply {
            createdAt = 1599699360433
            terminatedAt = 1599699468433
            callId = listOf("NjBlMTZhOWQ1Y2JjMzk1NTY3MjFlZTc0MTU4OTA1NDA.")
        }

        // Execute
        val result = service.details(request)

        // Assert
        assertEquals(1, result.size)
        assertNull(result[0]["rtcp"])

        val legSession = result[0]["rtp"]
        assertNotNull(legSession)
        assertEquals(1, legSession!!.codecs.size)
        legSession.codecs.first().apply {
            assertEquals("PCMU", name)
            assertEquals(8, payloadType)
        }

        assertEquals(1599699368433L, legSession.createdAt)
        assertEquals(1599699388201L, legSession.terminatedAt)
        assertEquals(19779, legSession.duration)
        assertEquals(request.callId!!.first(), legSession.callId)
        assertEquals(1, legSession.`out`.size)
        assertEquals(1, legSession.`in`.size)

        legSession.`out`.first().apply {
            assertEquals(legSession.createdAt, createdAt)
            assertEquals(legSession.terminatedAt, terminatedAt)
            assertEquals(28, blocks.size)
            assertEquals(19768, duration)

            assertEquals(4.409228801727295, mos)
            assertEquals(93.19705963134766, rFactor)

            assertEquals(988, packets.expected)
            assertEquals(988, packets.received)
            assertEquals(0, packets.lost)
            assertEquals(0, packets.rejected)

            assertEquals(0.005375981330871582, jitter.min)
            assertEquals(1.3704850673675537, jitter.max)
            assertEquals(0.2594599425792694, jitter.avg)
        }

        legSession.`in`.first().apply {
            assertEquals(1599699368452L, createdAt)
            assertEquals(1599699388109L, terminatedAt)
            assertEquals(28, blocks.size)
            assertEquals(19657, duration)

            assertEquals(4.408666610717773, mos)
            assertEquals(93.16806030273438, rFactor)

            assertEquals(983, packets.expected)
            assertEquals(983, packets.received)
            assertEquals(0, packets.lost)
            assertEquals(0, packets.rejected)

            assertEquals(0.01731395721435547, jitter.min)
            assertEquals(1.870510220527649, jitter.max)
            assertEquals(0.7710570693016052, jitter.avg)
        }
    }
}