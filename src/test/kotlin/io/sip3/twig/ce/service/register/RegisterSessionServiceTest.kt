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

package io.sip3.twig.ce.service.register

import io.pkts.packet.sip.impl.PreConditions.assertNotNull
import io.sip3.twig.ce.MockitoExtension.any
import io.sip3.twig.ce.domain.SessionRequest
import io.sip3.twig.ce.mongo.MongoClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.BDDMockito.*
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.util.*

@SpringBootTest
class RegisterSessionServiceTest {

    @MockBean
    private lateinit var client: MongoClient

    @Autowired
    private lateinit var service: RegisterSessionService

    @Test
    fun `Validate 'findInRawBySessionRequest()' method`() {
        // Init
        given(client.find(any(), any(), any(), any(), any())).willReturn(Collections.emptyIterator())
        val request = SessionRequest(
            createdAt = 1599729288822,
            terminatedAt = 1599730288822,
            callId = listOf("call-id-1", "call-id-2")
        )

        // Execute
        val result = service.findInRawBySessionRequest(request)

        // Assert
        assertNotNull(result)
        assertFalse(result.hasNext())
        verify(client, only()).find(any(), any(), any(), any(), any())

        // Assert required parameters
        assertThrows<IllegalArgumentException> {
            service.findInRawBySessionRequest(request.copy(createdAt = null))
        }
        assertThrows<IllegalArgumentException> {
            service.findInRawBySessionRequest(request.copy(terminatedAt = null))
        }
        assertThrows<IllegalArgumentException> {
            service.findInRawBySessionRequest(request.copy(callId = null))
        }
    }

    @AfterEach
    fun resetMocks() {
        reset(client)
    }
}
