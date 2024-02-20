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

package io.sip3.twig.ce.controller

import io.sip3.twig.ce.domain.Component
import io.sip3.twig.ce.service.component.ComponentService
import org.bson.Document
import org.hamcrest.Matchers.`is`
import org.hamcrest.collection.IsCollectionWithSize
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mockito.only
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print

import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ExtendWith(SpringExtension::class)
@WebMvcTest(ComponentController::class)
@AutoConfigureMockMvc(addFilters = false)
class ComponentControllerTest {

    companion object {

        val COMPONENT_1 = Component(
            "id1",
            "pbx2.bellini.staging",
            "beacc255-f33c-400d-a97c-e3044edbe1ce",
            "captain",
            setOf("udp://192.168.10.5:46582"),
            setOf("sip3-salto.staging.2"),
            1677846564019,
            1678099464012,
            1678099464011L,
            Document()
        )

        val COMPONENT_2_EXPIRED = Component(
            "id2",
            "pbx1.bellini.staging",
            "3dde8bf4-b012-479d-9d0f-93d1c8a467ac",
            "captain",
            setOf("udp://192.168.9.119:38273"),
            setOf("sip3-salto.staging.2"),
            1678031049317,
            1678099449314,
            1678099449312,
            Document()
        )
        val COMPONENT_2_REGISTERED = Component(
            "id2",
            "pbx1.bellini.staging",
            "3dde8bf4-b012-479d-9d0f-93d1c8a467ac",
            "captain",
            setOf("udp://192.168.9.119:38273"),
            setOf("sip3-salto.staging.2"),
            1678099459314,
            1678099459314,
            1678099459312,
            Document()
        )
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var componentService: ComponentService

    @Test
    fun `List all components`() {
        given(componentService.list()).willReturn(listOf(COMPONENT_1, COMPONENT_2_EXPIRED, COMPONENT_2_REGISTERED))
        mockMvc.perform(get("/management/components"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", IsCollectionWithSize.hasSize<Any>(3)))
            .andExpect(jsonPath("$[0].id").doesNotExist())
            .andExpect(jsonPath("$[0].name", `is`(COMPONENT_1.name)))
            .andExpect(jsonPath("$[0].deployment_id", `is`(COMPONENT_1.deploymentId)))
            .andExpect(jsonPath("$[1].id").doesNotExist())
            .andExpect(jsonPath("$[1].name", `is`(COMPONENT_2_EXPIRED.name)))
            .andExpect(jsonPath("$[1].deployment_id", `is`(COMPONENT_2_EXPIRED.deploymentId)))
            .andExpect(jsonPath("$[2].id").doesNotExist())
            .andExpect(jsonPath("$[2].name", `is`(COMPONENT_2_REGISTERED.name)))
            .andExpect(jsonPath("$[2].deployment_id", `is`(COMPONENT_2_REGISTERED.deploymentId)))
    }

    @Test
    fun `List latest components`() {
        given(componentService.listLatest()).willReturn(listOf(COMPONENT_1, COMPONENT_2_REGISTERED))

        mockMvc.perform(get("/management/components/latest"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", IsCollectionWithSize.hasSize<Any>(2)))
            .andExpect(jsonPath("$[0].deployment_id", `is`(COMPONENT_1.deploymentId)))
            .andExpect(jsonPath("$[1].deployment_id", `is`(COMPONENT_2_REGISTERED.deploymentId)))
    }

    @Test
    fun `Get component by deploymentId`() {
        given(componentService.getByDeploymentId(COMPONENT_1.deploymentId)).willReturn(COMPONENT_1)

        mockMvc.perform(get("/management/components/${COMPONENT_1.deploymentId}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deployment_id", `is`(COMPONENT_1.deploymentId)))
    }

    @Test
    fun `Get component by deploymentId that does not exist`() {
        given(componentService.getByDeploymentId("bad_id")).willThrow(EmptyResultDataAccessException(1))

        mockMvc.perform(get("/management/components/bad_id"))
            .andExpect(status().isNotFound())
    }

    @Test
    fun `Get component by name`() {
        given(componentService.getByNameIgnoreCase(COMPONENT_1.name)).willReturn(COMPONENT_1)

        mockMvc.perform(get("/management/components/name/${COMPONENT_1.name}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deployment_id", `is`(COMPONENT_1.deploymentId)))
    }

    @Test
    fun `Get component by name that does not exist`() {
        given(componentService.getByNameIgnoreCase("bad_id")).willThrow(EmptyResultDataAccessException(1))

        mockMvc.perform(get("/management/components/name/bad_id"))
            .andExpect(status().isNotFound())
    }

    @Test
    fun `Shutdown component by Deployment ID without exit code`() {
        mockMvc.perform(
            put("/management/components/${COMPONENT_1.deploymentId}/shutdown")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNoContent())
        verify(componentService, only()).shutdown(COMPONENT_1.deploymentId, emptyMap())
    }

    @Test
    fun `Shutdown component by Deployment ID with exit code`() {
        val params = mapOf("exit_code" to 123)
        mockMvc.perform(
            put("/management/components/${COMPONENT_1.deploymentId}/shutdown")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exit_code\":  123}"))
            .andExpect(status().isNoContent())

        verify(componentService, only()).shutdown(COMPONENT_1.deploymentId, params)
    }
}


