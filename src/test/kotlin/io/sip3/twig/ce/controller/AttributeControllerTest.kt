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

package io.sip3.twig.ce.controller

import io.sip3.commons.domain.Attribute
import io.sip3.twig.ce.service.AttributeService
import org.hamcrest.collection.IsCollectionWithSize
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@RunWith(SpringRunner::class)
@WebMvcTest(AttributeController::class)
class AttributeControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var attributeService: AttributeService

    @Test
    fun `Get list of all attributes`() {
        val attribute1 = Attribute().apply {
            name = "number"
            type = Attribute.TYPE_NUMBER
        }
        val attribute2 = Attribute().apply {
            name = "string"
            type = Attribute.TYPE_STRING
            options = mutableSetOf("option1", "option2")
        }

        given(attributeService.list())
                .willReturn(listOf(attribute1, attribute2))

        mockMvc.perform(get("/attributes"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$", IsCollectionWithSize.hasSize<Any>(2)))
    }
}