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
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiResponse
import io.swagger.annotations.ApiResponses
import org.springframework.dao.DuplicateKeyException
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import springfox.documentation.swagger2.annotations.EnableSwagger2
import javax.validation.ConstraintViolationException
import javax.validation.Valid
import javax.validation.Validation.buildDefaultValidatorFactory
import javax.validation.constraints.NotNull

@EnableSwagger2
@Api(
        tags = ["Host Controller"]
)
@RestController
@RequestMapping("/hosts")
class HostController(private val hostService: HostService, private val mapper: ObjectMapper) {

    @ApiOperation(
            position = 1,
            value = "Get all hosts",
            produces = "application/json"
    )
    @ApiResponses(value = [
        ApiResponse(code = 200, message = "Returns all hosts"),
        ApiResponse(code = 500, message = "Internal Server Error"),
        ApiResponse(code = 504, message = "Connection timeout")]
    )
    @GetMapping
    fun getAll(): Set<Host> {
        return hostService.getAll()
    }

    @ApiOperation(
            position = 2,
            value = "Get Host by name",
            produces = "application/json"
    )
    @ApiResponses(value = [
        ApiResponse(code = 200, message = "Returns host if it saved in storage"),
        ApiResponse(code = 404, message = "Host not found by name"),
        ApiResponse(code = 500, message = "Internal Server Error"),
        ApiResponse(code = 504, message = "Connection timeout")]
    )
    @GetMapping("/{name}")
    fun getByName(@Valid @NotNull @PathVariable("name") name: String): Host {
        return hostService.getByName(name.toLowerCase())
    }

    @ApiOperation(
            position = 3,
            value = "Add Host to storage",
            produces = "application/json"
    )
    @ApiResponses(value = [
        ApiResponse(code = 200, message = "Returns saved Host"),
        ApiResponse(code = 400, message = "Received bad request from client"),
        ApiResponse(code = 409, message = "Host with such name already exists"),
        ApiResponse(code = 500, message = "Internal Server Error"),
        ApiResponse(code = 504, message = "Connection timeout")]
    )
    @PostMapping
    fun add(@Valid @RequestBody host: Host): Host {
        return hostService.add(host)
    }

    @ApiOperation(
            position = 4,
            value = "Update Host in storage",
            produces = "application/json"
    )
    @ApiResponses(value = [
        ApiResponse(code = 200, message = "Returns updated Host"),
        ApiResponse(code = 400, message = "Received bad request from client"),
        ApiResponse(code = 404, message = "Host was not found by name"),
        ApiResponse(code = 500, message = "Internal Server Error"),
        ApiResponse(code = 504, message = "Connection timeout")]
    )
    @PutMapping
    fun update(@Valid @RequestBody host: Host): Host {
        return hostService.update(host)
    }

    @ApiOperation(
            position = 4,
            value = "Delete Host from storage"
    )
    @ApiResponses(value = [
        ApiResponse(code = 204, message = "Empty body if host removed successfully"),
        ApiResponse(code = 400, message = "Received bad request from client"),
        ApiResponse(code = 404, message = "Host was not found by name"),
        ApiResponse(code = 500, message = "Internal Server Error"),
        ApiResponse(code = 504, message = "Connection timeout")]
    )
    @DeleteMapping("/{name}")
    fun delete(@Valid @PathVariable("name") @NotNull name: String) {
        hostService.deleteByName(name)
    }

    @ApiOperation(
            position = 5,
            value = "Import Host list from JSON file"
    )
    @ApiResponses(value = [
        ApiResponse(code = 204, message = "Hosts added and updated successfully"),
        ApiResponse(code = 400, message = "Received bad request from client"),
        ApiResponse(code = 409, message = "File contains duplicated hosts by name"),
        ApiResponse(code = 500, message = "Internal Server Error"),
        ApiResponse(code = 504, message = "Connection timeout")]
    )
    @PostMapping("/import")
    fun import(@RequestParam("file") @Valid @NotNull file: MultipartFile) {
        val hosts: Set<Host> = mapper.readValue(file.inputStream)

        // Duplication by name validation
        val hasDuplicates = hosts.map { it.name.toLowerCase() }.toSet().size != hosts.size
        if (hasDuplicates) {
            throw DuplicateKeyException("File contains duplicated hosts by name")
        }

        val validator = buildDefaultValidatorFactory().validator

        // Validate hosts
        hosts.forEach { host ->
            val violations = validator.validate(host)
            if (violations.isNotEmpty()) {
                throw ConstraintViolationException(violations)
            }
        }

        hostService.saveAll(hosts)
    }
}