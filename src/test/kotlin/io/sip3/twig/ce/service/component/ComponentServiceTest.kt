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

package io.sip3.twig.ce.service.component

import io.sip3.twig.ce.MockitoExtension.any
import io.sip3.twig.ce.domain.Component
import io.sip3.twig.ce.repository.ComponentRepository
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.verify
import org.mockito.internal.verification.VerificationModeFactory.times
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [ComponentService::class])
class ComponentServiceTest {

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

    @MockBean
    private lateinit var componentRepository: ComponentRepository

    @Autowired
    private lateinit var componentService: ComponentService

    @Test
    fun `List all components`() {
        given(componentRepository.findAll()).willReturn(listOf(COMPONENT_1, COMPONENT_2_EXPIRED, COMPONENT_2_REGISTERED))

        val components = componentService.list()
        assertEquals(3, components.size)
        assertEquals(COMPONENT_1, components[0])
    }

    @Test
    fun `List all the latest components`() {
        given(componentRepository.findAll()).willReturn(listOf(COMPONENT_2_REGISTERED, COMPONENT_2_EXPIRED, COMPONENT_1))

        val components = componentService.listLatest()
        assertEquals(2, components.size)
        assertEquals(COMPONENT_2_REGISTERED, components[1])
    }

    @Test
    fun `Get component by Deployment ID`() {
        given(componentRepository.getByDeploymentId(any())).willReturn(COMPONENT_1)

        assertEquals(COMPONENT_1, componentService.getByDeploymentId(COMPONENT_1.deploymentId))
    }

    @Test
    fun `Get component by name ignore case`() {
        given(componentRepository.getByNameIgnoreCase(any())).willReturn(COMPONENT_1)

        assertEquals(COMPONENT_1, componentService.getByNameIgnoreCase(COMPONENT_1.name.uppercase()))
    }

    @Test
    fun `Get component that does not exist`() {
        given(componentRepository.getByNameIgnoreCase(any())).willThrow(EmptyResultDataAccessException(1))

        assertThrows(EmptyResultDataAccessException::class.java) {
            componentService.getByNameIgnoreCase("bad_name")
        }

        verify(componentRepository, times(1)).getByNameIgnoreCase(any())
    }

    @Test
    fun `Find component by name`() {
        given(componentRepository.findByNameIgnoreCase(any())).willReturn(setOf(COMPONENT_2_EXPIRED, COMPONENT_2_REGISTERED))

        val components = componentService.findByNameIgnoreCase(COMPONENT_2_REGISTERED.name).toList()
        assertEquals(2, components.size)
        assertEquals(COMPONENT_2_EXPIRED, components[0])
        assertEquals(COMPONENT_2_REGISTERED, components[1])
    }
}