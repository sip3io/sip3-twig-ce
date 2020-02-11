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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.sip3.twig.ce.domain.Host
import io.sip3.twig.ce.service.HostService
import io.sip3.twig.ce.util.IpAddressUtil.isValid
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiResponse
import io.swagger.annotations.ApiResponses
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import springfox.documentation.swagger2.annotations.EnableSwagger2
import javax.validation.Valid
import javax.validation.constraints.NotNull

@EnableSwagger2
@Api(
        tags = ["Hosts API"]
)
@RestController
@RequestMapping("/hosts")
class HostController(private val hostService: HostService, private val mapper: ObjectMapper) {

    @ApiOperation(
            position = 1,
            value = "List hosts",
            produces = "application/json"
    )
    @ApiResponses(value = [
        ApiResponse(code = 200, message = "Returns hosts"),
        ApiResponse(code = 500, message = "InternalServerError"),
        ApiResponse(code = 504, message = "ConnectionTimeoutError")]
    )
    @GetMapping
    fun list(): Set<Host> {
        return hostService.list()
    }

    @ApiOperation(
            position = 2,
            value = "Get host by name",
            produces = "application/json"
    )
    @ApiResponses(value = [
        ApiResponse(code = 200, message = "Returns host"),
        ApiResponse(code = 404, message = "Host not found"),
        ApiResponse(code = 500, message = "InternalServerError"),
        ApiResponse(code = 504, message = "ConnectionTimeoutError")]
    )
    @GetMapping("/{name}")
    fun getByName(@Valid @NotNull @PathVariable("name") name: String): Host {
        return hostService.getByName(name.toLowerCase())
    }

    @ApiOperation(
            position = 3,
            value = "Create host",
            produces = "application/json"
    )
    @ApiResponses(value = [
        ApiResponse(code = 200, message = "Returns host"),
        ApiResponse(code = 400, message = "Bad request"),
        ApiResponse(code = 409, message = "Duplicate host"),
        ApiResponse(code = 500, message = "InternalServerError"),
        ApiResponse(code = 504, message = "ConnectionTimeoutError")]
    )
    @PostMapping
    fun create(@Valid @RequestBody host: Host): Host {
        validate(host)
        return hostService.create(host)
    }

    @ApiOperation(
            position = 4,
            value = "Update host",
            produces = "application/json"
    )
    @ApiResponses(value = [
        ApiResponse(code = 200, message = "Returns host"),
        ApiResponse(code = 400, message = "Bad request"),
        ApiResponse(code = 404, message = "Host not found"),
        ApiResponse(code = 500, message = "InternalServerError"),
        ApiResponse(code = 504, message = "ConnectionTimeoutError")]
    )
    @PutMapping
    fun update(@Valid @RequestBody host: Host): Host {
        validate(host)
        return hostService.update(host)
    }

    @ApiOperation(
            position = 5,
            value = "Delete host by name"
    )
    @ApiResponses(value = [
        ApiResponse(code = 204, message = "Host deleted successfully"),
        ApiResponse(code = 400, message = "Bad request"),
        ApiResponse(code = 500, message = "InternalServerError"),
        ApiResponse(code = 504, message = "ConnectionTimeoutError")]
    )
    @DeleteMapping("/{name}")
    fun deleteByName(@Valid @PathVariable("name") @NotNull name: String) {
        hostService.deleteByName(name)
    }

    @ApiOperation(
            position = 6,
            value = "Import hosts from JSON file"
    )
    @ApiResponses(value = [
        ApiResponse(code = 204, message = "Hosts added and updated successfully"),
        ApiResponse(code = 400, message = "Bad request"),
        ApiResponse(code = 500, message = "InternalServerError"),
        ApiResponse(code = 504, message = "ConnectionTimeoutError")]
    )
    @PostMapping("/import")
    fun import(@RequestParam("file") @Valid @NotNull file: MultipartFile) {
        val hosts: Set<Host> = mapper.readValue(file.inputStream)

        // Validate host names
        val hasDuplicates = hosts.map { it.name.toLowerCase() }.toSet().size != hosts.size
        if (hasDuplicates) {
            throw IllegalArgumentException("name")
        }

        // Validate hosts
        hosts.forEach { validate(it) }

        hostService.saveAll(hosts)
    }

    private fun validate(host: Host) {
        host.sip?.forEach { address ->
            if (!isValid(address)) {
                throw IllegalArgumentException("sip")
            }
        }

        host.media?.forEach { address ->
            if (!isValid(address)) {
                throw IllegalArgumentException("media")
            }
        }
    }
}