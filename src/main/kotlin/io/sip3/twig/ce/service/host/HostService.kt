/*
 * Copyright 2018-2025 SIP3.IO, Corp.
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

import io.sip3.twig.ce.domain.Host
import io.sip3.twig.ce.repository.HostRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component

@Component
open class HostService {

    @Autowired
    private lateinit var hostRepository: HostRepository

    open fun list(): Set<Host> {
        return hostRepository.findAll().toSet()
    }

    open fun getByName(name: String): Host {
        return hostRepository.getByNameIgnoreCase(name)
    }

    open fun findByNameIgnoreCase(name: String): Host? {
        return hostRepository.findByNameIgnoreCase(name)
    }

    open fun findByAddr(address: String): Host? {
        return hostRepository.findByAddrContains(address)
    }

    open fun create(host: Host): Host {
        hostRepository.findByNameIgnoreCase(host.name)?.let {
            throw DuplicateKeyException("Host with name \"${host.name}\" already exists")
        }

        return hostRepository.save(host)
    }

    open fun update(host: Host): Host {
        return hostRepository.getByNameIgnoreCase(host.name).let { existingHost ->
            existingHost.addr = host.addr
            existingHost.mapping = host.mapping
            existingHost.feature = host.feature
            hostRepository.save(existingHost)
        }
    }

    open fun deleteByName(name: String) {
        hostRepository.findByNameIgnoreCase(name)?.let { hostRepository.delete(it) }
    }

    open fun saveAll(hosts: Set<Host>) {
        hosts.forEach { host ->
            val existingHost = hostRepository.findByNameIgnoreCase(host.name)

            if (existingHost == null) {
                hostRepository.save(host)
            } else {
                existingHost.addr = host.addr
                existingHost.mapping = host.mapping
                existingHost.feature = host.feature
                hostRepository.save(existingHost)
            }
        }
    }
}