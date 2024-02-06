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
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@Tag(name = "Components API", description = "Components Controller")
@RestController
@RequestMapping("/management/components")
class ComponentController {

    @Autowired
    private lateinit var componentService: ComponentService

    @Operation(summary = "List components ")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns components"),
        ApiResponse(responseCode = "500", description = "InternalServerError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)])
    )
    @GetMapping(
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun listWithExpired(): List<Component> {
        return componentService.list()
    }

    @Operation(summary = "List latest components")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns registered components only"),
        ApiResponse(responseCode = "500", description = "InternalServerError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)])
    )
    @GetMapping(
        path = ["/latest"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun listRegistered(): List<Component> {
        return componentService.listLatest()
    }

    @Operation(summary = "Get component by Deployment ID")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns component by Deployment ID"),
        ApiResponse(responseCode = "404", description = "Component not found", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "500", description = "InternalServerError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)])
    )
    @GetMapping(
        path = ["/{deploymentId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun getByDeploymentId(@PathVariable("deploymentId") deploymentId: String): Component {
        return componentService.getByDeploymentId(deploymentId)
    }

    @Operation(summary = "Get component by name")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns component by name"),
        ApiResponse(responseCode = "500", description = "InternalServerError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)])
    )
    @GetMapping(
        path = ["/name/{name}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun getRegisteredByName(@PathVariable("name") name: String): Component {
        return componentService.getByNameIgnoreCase(name)
    }

    @Operation(summary = "Send Media Recording Reset command to component")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Media Recording Reset command sent"),
        ApiResponse(responseCode = "404", description = "Component not found", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "500", description = "InternalServerError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)])
    )
    @PutMapping(
        path = ["/{deploymentId}/recording-reset"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    fun mediaRecordingReset(@PathVariable("deploymentId") deploymentId: String) {
        componentService.mediaRecordingReset(deploymentId)
    }

    @Operation(summary = "Send Media Recording Reset command to component by Name")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Media Recording Reset command sent"),
        ApiResponse(responseCode = "404", description = "Component not found", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "500", description = "InternalServerError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)])
    )
    @PutMapping(
        path = ["/name/{name}/recording-reset"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    fun mediaRecordingResetByName(@PathVariable("name") name: String) {
        componentService.mediaRecordingResetByName(name)
    }

    @Operation(summary = "Send Shutdown command to component")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Shutdown command sent"),
        ApiResponse(responseCode = "404", description = "Component not found", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "500", description = "InternalServerError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)])
    )
    @PutMapping(
        path = ["/{deploymentId}/shutdown"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    fun sendShutdown(@PathVariable("deploymentId") deploymentId: String) {
        componentService.shutdown(deploymentId)
    }

    @Operation(summary = "Send Shutdown command to component by Name")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Shutdown command sent"),
        ApiResponse(responseCode = "404", description = "Component not found", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "500", description = "InternalServerError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)])
    )
    @PutMapping(
        path = ["/name/{name}/shutdown"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    fun sendShutdownByName(@PathVariable("name") name: String) {
        componentService.shutdownByName(name)
    }

    @Operation(summary = "Delete component by Deployment ID")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Component removed"),
        ApiResponse(responseCode = "404", description = "Component not found", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "500", description = "InternalServerError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)])
    )
    @DeleteMapping(
        path = ["/{deploymentId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    fun deleteByDeploymentId(@PathVariable("deploymentId") deploymentId: String) {
        componentService.deleteByDeploymentId(deploymentId)
    }
    @Operation(summary = "Delete component by name")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Component removed"),
        ApiResponse(responseCode = "404", description = "Component not found", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "500", description = "InternalServerError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError", content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)])
    )
    @DeleteMapping(
        path = ["/name/{name}"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    fun deleteByName(@PathVariable("name") name: String) {
        componentService.deleteByName(name)
    }
}