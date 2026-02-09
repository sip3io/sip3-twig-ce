/*
 * Copyright 2018-2026 SIP3.IO, Corp.
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

package io.sip3.twig.ce.service.host

import io.sip3.twig.ce.MockitoExtension.any
import io.sip3.twig.ce.domain.AddressMapping
import io.sip3.twig.ce.domain.Host
import io.sip3.twig.ce.repository.HostRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [HostService::class])
class HostServiceTest {

    companion object {

        val HOST_1 = Host(
            "id1",
            "host1",
            listOf("10.10.10.0:5060", "10.10.10.0/28"),
            listOf(AddressMapping("10.0.0.1", "10.0.0.1"))
        )
        val HOST_2 = Host(
            "id2",
            "host2",
            listOf("10.10.20.0:5060", "10.10.20.0/28"),
            listOf(AddressMapping("10.0.0.1", "10.0.0.1")),
            setOf("proxy")
        )
        val HOST_3 = Host(
            "id2",
            "host2",
            listOf("10.10.10.0:5060", "10.10.30.0/28"),
            emptyList(),
            setOf("proxy")
        )
    }

    @MockBean
    private lateinit var hostRepository: HostRepository

    @Autowired
    private lateinit var hostService: HostService

    @Test
    fun `Get all hosts`() {
        given(hostRepository.findAll()).willReturn(setOf(HOST_1, HOST_2))
        val hosts = hostService.list()
        assertEquals(2, hosts.size)
        assertEquals(HOST_1, hosts.first())
    }

    @Test
    fun `Get by name`() {
        given(hostRepository.getByNameIgnoreCase(any())).willReturn(HOST_1)
        val host = hostService.getByName(HOST_1.name)
        assertEquals(HOST_1, host)
    }

    @Test
    fun `Get by name if host not exists`() {
        given(hostRepository.getByNameIgnoreCase(any())).willThrow(EmptyResultDataAccessException(1))

        assertThrows<EmptyResultDataAccessException> {
            hostService.getByName(HOST_1.name)
        }
    }

    @Test
    fun `Add new host`() {
        given(hostRepository.save(any<Host>())).willReturn(HOST_1)
        hostService.create(HOST_1)

        verify(hostRepository, times(1)).save(any<Host>())
    }

    @Test
    fun `Add existing host`() {
        given(hostRepository.findByNameIgnoreCase(any())).willReturn(HOST_1)
        given(hostRepository.save(any<Host>())).willReturn(HOST_1)

        assertThrows<DuplicateKeyException> {
            hostService.create(HOST_1)
        }
    }

    @Test
    fun `Update existing host`() {
        given(hostRepository.getByNameIgnoreCase(any())).willReturn(HOST_1)
        given(hostRepository.save(any<Host>())).willReturn(HOST_1)
        given(hostRepository.findAllByAddrContains(any())).willReturn(emptyList())

        hostService.update(HOST_1)

        verify(hostRepository, times(1)).save(HOST_1)
    }

    @Test
    fun `Update existing host and duplication by address`() {
        given(hostRepository.getByNameIgnoreCase(any())).willReturn(HOST_1)
        given(hostRepository.save(any<Host>())).willReturn(HOST_1)
        given(hostRepository.findAllByAddrContains(any())).willReturn(listOf(HOST_1, HOST_3))

        assertThrows<DuplicateKeyException> {
            hostService.update(HOST_1)
        }
    }

    @Test
    fun `Update not existing host`() {
        given(hostRepository.getByNameIgnoreCase(any())).willThrow(EmptyResultDataAccessException(1))

        assertThrows<EmptyResultDataAccessException> {
            hostService.update(HOST_1)
        }
    }

    @Test
    fun `Delete host by name`() {
        given(hostRepository.findByNameIgnoreCase(any())).willReturn(HOST_1)
        hostService.deleteByName(HOST_1.name)

        verify(hostRepository, times(1)).delete(any())
    }

    @Test
    fun `Delete not existing host by name`() {
        hostService.deleteByName(HOST_1.name)

        verify(hostRepository, never()).delete(any())
    }

    @Test
    fun `Save multiply hosts`() {
        val hosts = setOf(HOST_1, HOST_2)
        hostService.saveAll(hosts)
        verify(hostRepository, times(2)).save(any())
    }
}