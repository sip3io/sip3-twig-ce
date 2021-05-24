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

package io.sip3.twig.ce.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.sip3.twig.ce.MockitoExtension.any
import io.sip3.twig.ce.domain.Host
import io.sip3.twig.ce.domain.Participant
import io.sip3.twig.ce.domain.SessionRequest
import io.sip3.twig.ce.service.ServiceLocator
import io.sip3.twig.ce.service.call.CallSessionService
import io.sip3.twig.ce.service.host.HostService
import io.sip3.twig.ce.service.media.MediaSessionService
import io.sip3.twig.ce.service.media.domain.LegSession
import io.sip3.twig.ce.service.participant.ParticipantService
import io.sip3.twig.ce.service.register.RegisterSessionService
import org.bson.Document
import org.hamcrest.Matchers
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

@ExtendWith(SpringExtension::class)
@WebMvcTest(SessionController::class)
class SessionControllerTest {

    companion object {

        val CREATED_AT = System.currentTimeMillis()
        val TERMINATED_AT = CREATED_AT + 60000

        val RESPONSE_1 = listOf(
            Document().apply {
                put("created_at", CREATED_AT)

                put("src_addr", "10.10.10.10")
                put("src_port", 5060)
                put("src_host", "HOST1")

                put("dst_addr", "20.20.20.20")
                put("dst_port", 5060)
                put("dst_host", "HOST2")

                put("call_id", "call-id-1")
                put("request_uri", "request_uri")
                put("from_uri", "from_uri")
                put("to_uri", "to_uri")
            }
        )

        // Register 1 (1/2)
        val REGISTER_1 = Document().apply {
            put("created_at", CREATED_AT)
            put("src_addr", "192.168.10.123")
            put("src_port", 55399)
            put("dst_addr", "192.168.10.5")
            put("dst_port", 5060)
            put("dst_host", "pbx")
            put(
                "raw_data", """
                        REGISTER sip:192.168.10.5:5060 SIP/2.0
                        Via: SIP/2.0/UDP 192.168.10.123:55399;branch=z9hG4bK-d8754z-240d73239a6da57b-1---d8754z-;rport
                        Max-Forwards: 70
                        Contact: <sip:1010@192.168.10.123:55399;rinstance=13bf343a521442b5>
                        To: "1010"<sip:1010@192.168.10.5:5060>
                        From: "1010"<sip:1010@192.168.10.5:5060>;tag=bd285f07
                        Call-ID: call-id-1
                        CSeq: 143 REGISTER
                        Expires: 120
                        Allow: INVITE, ACK, CANCEL, OPTIONS, BYE, REGISTER, SUBSCRIBE, NOTIFY, REFER, INFO, MESSAGE
                        Supported: replaces
                        User-Agent: 3CXPhone 6.0.26523.0
                        Authorization: Digest username="1010",realm="asterisk",nonce="1589932693/afdc97bf8bb891c6c3c8072baeb2d3d6",uri="sip:192.168.10.5:5060",response="1337bca977875e158b7e1b96094521ee",cnonce="557bd23e12dc3db950db3c7e77aa91ad",nc=00000002,qop=auth,algorithm=md5,opaque="5b7b35877f215628"
                        Content-Length: 0
                    """.trimIndent().trimIndent()
            )
        }

        // Register 1 (2/2)
        val REGISTER_2 = Document().apply {
            put("created_at", CREATED_AT + 1)
            put("src_addr", "192.168.10.5")
            put("src_port", 5060)
            put("src_host", "pbx")
            put("dst_addr", "192.168.10.123")
            put("dst_port", 55399)
            put(
                "raw_data", """
                        SIP/2.0 200 OK
                        Via: SIP/2.0/UDP 192.168.10.123:55399;rport=55399;received=192.168.10.123;branch=z9hG4bK-d8754z-ef77c05e05556d61-1---d8754z-
                        Call-ID: call-id-1
                        From: "1010" <sip:1010@192.168.10.5>;tag=bd285f07
                        To: "1010" <sip:1010@192.168.10.5>;tag=z9hG4bK-d8754z-ef77c05e05556d61-1---d8754z-
                        CSeq: 144 REGISTER
                        Date: Wed, 20 May 2020 00:00:01 GMT
                        Contact: <sip:1010@192.168.10.123:55399;rinstance=13bf343a521442b5>;expires=119
                        Expires: 120
                        Server: FPBX-14.0.13.23(13.29.2)
                        Content-Length:  0
                    """.trimIndent()
            )
        }
        val RESPONSE_2 = listOf(REGISTER_1, REGISTER_2)

        val objectMapper = ObjectMapper()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var participantService: ParticipantService

    @MockBean
    private lateinit var callSessionService: CallSessionService

    @MockBean
    private lateinit var registerSessionService: RegisterSessionService

    @MockBean
    private lateinit var mediaSessionService: MediaSessionService

    @MockBean
    private lateinit var serviceLocator: ServiceLocator

    @BeforeEach
    fun init() {
        given(serviceLocator.sessionService("INVITE")).willReturn(callSessionService)
        given(serviceLocator.sessionService("REGISTER")).willReturn(registerSessionService)
    }

    @Test
    fun `Call session details`() {
        val request = SessionRequest().apply {
            createdAt = CREATED_AT
            terminatedAt = TERMINATED_AT
            method = listOf("INVITE")
            callId = listOf("callId")
        }

        given(callSessionService.details(any())).willReturn(RESPONSE_1)

        mockMvc.perform(
            post("/session/details")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].created_at", `is`(CREATED_AT)))
            .andExpect(jsonPath("$[0].call_id", `is`("call-id-1")))

        verify(callSessionService, only()).details(any())
        verify(registerSessionService, never()).details(any())
    }

    @Test
    fun `Register session details`() {
        val request = SessionRequest().apply {
            createdAt = CREATED_AT
            terminatedAt = TERMINATED_AT
            method = listOf("REGISTER")
            callId = listOf("callId")
        }

        given(registerSessionService.details(any())).willReturn(RESPONSE_1)

        mockMvc.perform(
            post("/session/details")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].created_at", `is`(CREATED_AT)))
            .andExpect(jsonPath("$[0].call_id", `is`("call-id-1")))

        verify(registerSessionService, only()).details(any())
        verify(callSessionService, never()).details(any())
    }

    @Test
    fun `Register session content`() {
        val request = SessionRequest().apply {
            createdAt = CREATED_AT
            terminatedAt = TERMINATED_AT
            method = listOf("REGISTER")
            callId = listOf("callId")
        }

        given(registerSessionService.content(any())).willReturn(RESPONSE_2)

        mockMvc.perform(
            post("/session/content")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("hosts[0]", `is`("192.168.10.123")))
            .andExpect(jsonPath("hosts[1]", `is`("pbx")))
            .andExpect(jsonPath("messages[0].created_at", `is`(CREATED_AT)))

        verify(registerSessionService, only()).content(any())
        verify(callSessionService, never()).content(any())
    }

    @Test
    fun `Register session flow`() {
        val request = SessionRequest().apply {
            createdAt = CREATED_AT
            terminatedAt = TERMINATED_AT
            method = listOf("REGISTER")
            callId = listOf("callId")
        }

        given(registerSessionService.details(any())).willReturn(RESPONSE_1)
        given(registerSessionService.content(any())).willReturn(RESPONSE_2)
        given(participantService.collectParticipants(any())).willReturn(
            listOf(
                Participant("192.168.10.123", "HOST", null),
                Participant("pbx", "HOST", Host(null, "pbx", listOf("192.168.10.5"), null))
            )
        )

        mockMvc.perform(
            post("/session/flow")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("participants[0].name", `is`("192.168.10.123")))
            .andExpect(jsonPath("participants[1].name", `is`("pbx")))
            .andExpect(jsonPath("participants[1].details.name", `is`("pbx")))

            .andExpect(jsonPath("events[0].timestamp", `is`(CREATED_AT)))
            .andExpect(jsonPath("events[0].type", `is`("SIP")))

        verify(registerSessionService, only()).content(any())
        verify(callSessionService, never()).content(any())
    }

    @Test
    fun `Call session flow`() {
        val request = SessionRequest().apply {
            createdAt = CREATED_AT
            terminatedAt = TERMINATED_AT
            method = listOf("INVITE")
            callId = listOf("callId")
        }

        given(callSessionService.details(any())).willReturn(RESPONSE_1)
        given(callSessionService.content(any())).willReturn(RESPONSE_2)
        given(mediaSessionService.details(any())).willReturn(
            listOf(
                mapOf(
                    "rtp" to LegSession().apply {
                        createdAt = CREATED_AT
                        srcAddr = "192.168.10.123"
                        dstAddr = "192.168.10.5"
                        dstHost = "pbx"
                        callId = "call-id-1"
                    },
                    "rtcp" to LegSession().apply {
                        createdAt = CREATED_AT + 1
                        srcAddr = "192.168.10.123"
                        dstAddr = "192.168.10.5"
                        dstHost = "pbx"
                        callId = "call-id-1"
                    }
                )
            )
        )

        given(participantService.collectParticipants(any())).willReturn(
            listOf(
                Participant("192.168.10.123", "HOST", null),
                Participant("pbx", "HOST", Host(null, "pbx", listOf("192.168.10.5"), null))
            )
        )
        mockMvc.perform(
            post("/session/flow")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("participants[0].name", `is`("192.168.10.123")))
            .andExpect(jsonPath("participants[1].name", `is`("pbx")))
            .andExpect(jsonPath("participants[1].details.name", `is`("pbx")))

            .andExpect(jsonPath("events[0].timestamp", `is`(CREATED_AT)))
            .andExpect(jsonPath("events[0].type", `is`("SIP")))

            .andExpect(jsonPath("events[1].timestamp", `is`(CREATED_AT)))
            .andExpect(jsonPath("events[1].type", `is`("RTPR")))

            .andExpect(jsonPath("events[2].timestamp", `is`(CREATED_AT + 1)))
            .andExpect(jsonPath("events[2].type", `is`("SIP")))


        verify(callSessionService, only()).content(any())
    }

    @Test
    fun `Register session pcap`() {
        val request = SessionRequest().apply {
            createdAt = CREATED_AT
            terminatedAt = TERMINATED_AT
            method = listOf("REGISTER")
            callId = listOf("callId")
        }

        val pcap = ByteArrayOutputStream().apply {
            write("pcap-content".toByteArray(Charset.defaultCharset()))
        }

        given(registerSessionService.pcap(any())).willReturn(pcap)

        mockMvc.perform(
            post("/session/pcap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(content().string(Matchers.startsWith("pcap-content")))

        verify(registerSessionService, only()).pcap(any())
        verify(callSessionService, never()).pcap(any())
    }
}
