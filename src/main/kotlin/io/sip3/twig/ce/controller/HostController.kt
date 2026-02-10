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

package io.sip3.twig.ce.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.sip3.twig.ce.domain.Host
import io.sip3.twig.ce.service.host.HostService
import io.sip3.twig.ce.util.IpAddressUtil.isValid
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Tag(name = "Hosts API", description = "Host Controller")
@RestController
@RequestMapping("/hosts")
class HostController {

    @Autowired
    private lateinit var hostService: HostService

    @Autowired
    private lateinit var mapper: ObjectMapper

    @Operation(summary = "List hosts")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns hosts"),
        ApiResponse(responseCode = "500", description = "InternalServerError"),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError")
    )
    @GetMapping(
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun list(): Set<Host> {
        return hostService.list()
    }

    @Operation(summary = "Get host by name")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns host"),
        ApiResponse(responseCode = "404", description = "Host not found"),
        ApiResponse(responseCode = "500", description = "InternalServerError"),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError")
    )
    @GetMapping(
        value = ["/{name}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun getByName(@Valid @NotNull @PathVariable("name") name: String): Host {
        return hostService.getByName(name.lowercase())
    }

    @Operation(summary = "Find all hosts by IP Address")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns host list"),
        ApiResponse(responseCode = "500", description = "InternalServerError"),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError")
    )
    @GetMapping(
        value = ["/addr/{addr}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun findAllByAddr(@Valid @NotNull @PathVariable("addr") addr: String): List<Host> {
        return hostService.findAllByAddr(addr)
    }

    @Operation(summary = "Create host")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns host"),
        ApiResponse(responseCode = "400", description = "Bad request"),
        ApiResponse(responseCode = "409", description = "Duplicate host"),
        ApiResponse(responseCode = "500", description = "InternalServerError"),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError")
    )
    @PostMapping(
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun create(@Valid @RequestBody host: Host): Host {
        validate(host)
        return hostService.create(host)
    }

    @Operation(summary = "Update host")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns host"),
        ApiResponse(responseCode = "400", description = "Bad request"),
        ApiResponse(responseCode = "404", description = "Host not found"),
        ApiResponse(responseCode = "500", description = "InternalServerError"),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError")
    )
    @PutMapping(
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun update(@Valid @RequestBody host: Host): Host {
        validate(host)
        return hostService.update(host)
    }

    @Operation(summary = "Delete host by name")
    @ApiResponses(
        ApiResponse(responseCode = "204", description =  "Host deleted successfully"),
        ApiResponse(responseCode = "400", description =  "Bad request"),
        ApiResponse(responseCode = "500", description =  "InternalServerError"),
        ApiResponse(responseCode = "504", description =  "ConnectionTimeoutError")
    )
    @DeleteMapping(
        value = ["/{name}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    fun deleteByName(@Valid @PathVariable("name") @NotNull name: String) {
        hostService.deleteByName(name)
    }

    @Operation(summary = "Delete all hosts by name")
    @ApiResponses(
        ApiResponse(responseCode = "204", description =  "Hosts deleted successfully"),
        ApiResponse(responseCode = "400", description =  "Bad request"),
        ApiResponse(responseCode = "500", description =  "InternalServerError"),
        ApiResponse(responseCode = "504", description =  "ConnectionTimeoutError")
    )
    @DeleteMapping(
        value = ["/{name}/all"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    fun deleteAllByName(@Valid @PathVariable("name") @NotNull name: String) {
        hostService.deleteAllByName(name)
    }

    @Operation(summary = "Import hosts from JSON file")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Hosts added and updated successfully"),
        ApiResponse(responseCode = "400", description = "Bad request"),
        ApiResponse(responseCode = "500", description = "InternalServerError"),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError")
    )
    @PostMapping(
        value = ["/import"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    fun import(@RequestPart("file", required = true) @Valid @NotNull file: MultipartFile) {
        val hosts: Set<Host> = mapper.readValue(file.inputStream)

        // Validate host names
        val hasDuplicates = hosts.map { it.name.lowercase() }.toSet().size != hosts.size
        if (hasDuplicates) {
            throw IllegalArgumentException("name")
        }

        // Validate hosts
        hosts.forEach { validate(it) }

        hostService.saveAll(hosts)
    }

    private fun validate(host: Host) {
        if (host.addr.isEmpty()) {
            throw IllegalArgumentException("addr")
        }

        host.addr.forEach { address ->
            if (!isValid(address)) {
                throw IllegalArgumentException("addr")
            }
        }

        host.mapping.forEach { mapping ->
            if (!isValid(mapping.source)) {
                throw IllegalArgumentException("mapping.source")
            }

            if (!isValid(mapping.target)) {
                throw IllegalArgumentException("mapping.target")
            }
        }
    }
}