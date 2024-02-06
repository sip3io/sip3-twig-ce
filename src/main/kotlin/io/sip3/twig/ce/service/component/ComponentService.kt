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

import io.sip3.twig.ce.domain.Component
import io.sip3.twig.ce.repository.ComponentRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
open class ComponentService {

    @Autowired
    protected lateinit var componentRepository: ComponentRepository

    open fun list(): List<Component> {
        return componentRepository.findAll().toList()
    }

    open fun listLatest(): List<Component> {
        return componentRepository.findAll()
            .sortedByDescending { it.updatedAt }
            .distinctBy { it.name }
    }

    open fun getByDeploymentId(deploymentId: String): Component {
        return componentRepository.getByDeploymentId(deploymentId)
    }

    open fun getByNameIgnoreCase(name: String): Component {
        return componentRepository.getByNameIgnoreCase(name)
    }

    open fun findByNameIgnoreCase(name: String): Set<Component> {
        return componentRepository.findByNameIgnoreCase(name)
    }

    open fun deleteByDeploymentId(deploymentId: String) {
        componentRepository.getByDeploymentId(deploymentId).let {
            componentRepository.delete(it)
        }
    }
    open fun deleteByName(name: String) {
        componentRepository.findByNameIgnoreCase(name).let {
            componentRepository.deleteAll(it)
        }
    }

    open fun mediaRecordingReset(deploymentId: String) {
        throw UnsupportedOperationException("Media Recording Reset is not supported in CE version")
    }

    open fun mediaRecordingResetByName(name: String) {
        throw UnsupportedOperationException("Media Recording Reset is not supported in CE version")
    }

    open fun shutdown(deploymentId: String) {
        throw UnsupportedOperationException("Shutdown is not supported in CE version")
    }

    open fun shutdownByName(name: String) {
        throw UnsupportedOperationException("Shutdown is not supported in CE version")
    }
}