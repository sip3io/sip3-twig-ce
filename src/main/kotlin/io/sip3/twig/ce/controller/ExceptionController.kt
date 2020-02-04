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

import com.mongodb.MongoExecutionTimeoutException
import mu.KotlinLogging
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import javax.servlet.http.HttpServletRequest
import javax.validation.ValidationException

@ControllerAdvice
class ExceptionController {

    private val logger = KotlinLogging.logger {}

    @ExceptionHandler(Exception::class)
    fun exception(e: Exception, req: HttpServletRequest): ResponseEntity<*> {
        logger.error("$req failed.", e)

        return when (e) {
            is HttpMessageNotReadableException -> ResponseEntity.badRequest()
                    .body("HttpMessageNotReadableError: Check your HTTP request.")

            is IllegalArgumentException -> ResponseEntity.badRequest()
                    .body("IllegalArgumentError: Check `${e.message}` parameter.")

            is IllegalStateException -> ResponseEntity.badRequest()
                    .body("IllegalStateException: `${e.message}`.")

            is EmptyResultDataAccessException -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("DataAccessException: Entity not found")

            is DuplicateKeyException -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("DuplicateKeyException: `${e.message}`.")

            is ValidationException -> ResponseEntity.badRequest()
                    .body("Validation failed: `${e.message}`.")

            is MethodArgumentNotValidException -> ResponseEntity.badRequest()
                    .body("MethodArgumentNotValidException: Invalid input data")

            is MongoExecutionTimeoutException -> ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body("GatewayTimeoutError: Try to repeat your request or adjust request params.")

            else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("InternalServerError: Try to check service logs and contact with Support team if needed")
        }
    }
}