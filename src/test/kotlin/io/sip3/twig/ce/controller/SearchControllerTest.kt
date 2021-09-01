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

package io.sip3.twig.ce.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.sip3.twig.ce.MockitoExtension.any
import io.sip3.twig.ce.domain.SearchRequest
import io.sip3.twig.ce.domain.SearchResponse
import io.sip3.twig.ce.service.ServiceLocator
import io.sip3.twig.ce.service.call.CallSearchService
import io.sip3.twig.ce.service.register.RegisterSearchService
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@ExtendWith(SpringExtension::class)
@WebMvcTest(SearchController::class)
@Import(SearchRequestValidator::class)
class SearchControllerTest {

    companion object {

        val CREATED_AT = System.currentTimeMillis()
        val TERMINATED_AT = CREATED_AT + 60000

        val RESPONSE_1 = sequenceOf(
            SearchResponse().apply {
                createdAt = CREATED_AT
                terminatedAt = TERMINATED_AT
                method = "INVITE"
                state = "answered"
                caller = "101"
                callee = "102"
                callId = setOf("CALL-ID-1", "CALL-ID-2")
                duration = 600
            }
        )

        val RESPONSE_2 = sequenceOf(
            SearchResponse().apply {
                createdAt = CREATED_AT
                terminatedAt = TERMINATED_AT
                method = "REGISTER"
                state = "registered"
                caller = "101"
                callee = "102"
                callId = setOf("CALL-ID-1", "CALL-ID-2")
            }
        )


        val objectMapper = ObjectMapper()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var callSearchService: CallSearchService

    @MockBean
    private lateinit var registerSearchService: RegisterSearchService

    @MockBean
    private lateinit var serviceLocator: ServiceLocator

    @BeforeEach
    fun init() {
        given(serviceLocator.searchServices()).willReturn(listOf(callSearchService, registerSearchService))
        given(serviceLocator.searchService("INVITE")).willReturn(callSearchService)
        given(serviceLocator.searchService("REGISTER")).willReturn(registerSearchService)
    }

    @Test
    fun `Search by query with INVITE method`() {
        val query = "sip.method=INVITE"
        val request = SearchRequest(CREATED_AT, TERMINATED_AT, query, 50)

        given(callSearchService.search(any())).willReturn(RESPONSE_1.iterator())

        mockMvc.perform(
            post("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].created_at", `is`(CREATED_AT)))
            .andExpect(jsonPath("$[0].method", `is`("INVITE")))

        verify(callSearchService, only()).search(any())
        verify(registerSearchService, never()).search(any())
    }

    @Test
    fun `Search by query with REGISTER method`() {
        val query = "sip.method=REGISTER"
        val request = SearchRequest(CREATED_AT, TERMINATED_AT, query, 50)

        given(registerSearchService.search(any())).willReturn(RESPONSE_2.iterator())

        mockMvc.perform(
            post("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].created_at", `is`(CREATED_AT)))
            .andExpect(jsonPath("$[0].method", `is`("REGISTER")))

        verify(callSearchService, never()).search(any())
        verify(registerSearchService, only()).search(any())
    }

    @Test
    fun `Search by query without SIP method`() {
        val query = ""
        val request = SearchRequest(CREATED_AT, TERMINATED_AT, query, 50)

        given(callSearchService.search(any())).willReturn(RESPONSE_1.iterator())
        given(registerSearchService.search(any())).willReturn(RESPONSE_2.iterator())

        mockMvc.perform(
            post("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].created_at", `is`(CREATED_AT)))
            .andExpect(jsonPath("$[0].method", `is`("INVITE")))
            .andExpect(jsonPath("$[1].created_at", `is`(CREATED_AT)))
            .andExpect(jsonPath("$[1].method", `is`("REGISTER")))

        verify(callSearchService, only()).search(any())
        verify(registerSearchService, only()).search(any())
    }

    @Test
    fun `Handle bad request`() {
        given(callSearchService.search(any())).willReturn(RESPONSE_1.iterator())

        mockMvc.perform(
            post("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"created_at": -1, "terminated_at": -1}""")
        )
            .andExpect(status().isBadRequest)

        verify(callSearchService, never()).search(any())
        verify(registerSearchService, never()).search(any())
    }

    @Test
    fun `Complex search by RTP and RTCP`() {
        val query = "sip.method=INVITE rtcp.r_factor>30 rtp.mos<3"
        val request = SearchRequest(CREATED_AT, TERMINATED_AT, query, 50)

        mockMvc.perform(
            post("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().is4xxClientError)
            .andExpect(content().string(startsWith("UnsupportedOperationException")))

        verify(callSearchService, never()).search(any())
        verify(registerSearchService, never()).search(any())
    }
}
