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

package io.sip3.twig.ce.service.host

import io.sip3.twig.ce.MockitoExtension.any
import io.sip3.twig.ce.domain.Host
import io.sip3.twig.ce.repository.HostRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
class HostServiceTest {

    companion object {

        val HOST_1 = Host("id1", "host1", listOf("10.10.10.0:5060", "10.10.10.0/28"), listOf("10.0.0.1"))
        val HOST_2 = Host("id2", "host2", listOf("10.10.20.0:5060", "10.10.20.0/28"), listOf("10.0.0.2"))
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
        val host = hostService.getByName("host1")
        assertEquals(HOST_1, host)
    }

    @Test(expected = EmptyResultDataAccessException::class)
    fun `Get by name if host not exists`() {
        given(hostRepository.getByNameIgnoreCase(any())).willThrow(EmptyResultDataAccessException(1))
        hostService.getByName("host1")
    }

    @Test
    fun `Add new host`() {
        given(hostRepository.save(any<Host>())).willReturn(HOST_1)
        hostService.create(HOST_1)

        verify(hostRepository, times(1)).save(any<Host>())
    }

    @Test(expected = DuplicateKeyException::class)
    fun `Add existing host`() {
        given(hostRepository.findByNameIgnoreCase(any())).willReturn(HOST_1)
        given(hostRepository.save(any<Host>())).willReturn(HOST_1)
        hostService.create(HOST_1)
    }

    @Test
    fun `Update existing host`() {
        given(hostRepository.getByNameIgnoreCase(any())).willReturn(HOST_1)
        given(hostRepository.save(any<Host>())).willReturn(HOST_1)
        hostService.update(HOST_1)

        verify(hostRepository, times(1)).save(HOST_1)
    }

    @Test(expected = EmptyResultDataAccessException::class)
    fun `Update not existing host`() {
        given(hostRepository.getByNameIgnoreCase(any())).willThrow(EmptyResultDataAccessException(1))
        hostService.update(HOST_1)
    }

    @Test
    fun `Delete host by name`() {
        given(hostRepository.findByNameIgnoreCase(any())).willReturn(HOST_1)
        hostService.deleteByName("host1")

        verify(hostRepository, times(1)).delete(any())
    }

    @Test
    fun `Delete not existing host by name`() {
        hostService.deleteByName("host1")

        verify(hostRepository, never()).delete(any())
    }

    @Test
    fun `Save multiply hosts`() {
        val hosts = setOf(HOST_1, HOST_2)
        hostService.saveAll(hosts)
        verify(hostRepository, times(2)).save(any())
    }
}