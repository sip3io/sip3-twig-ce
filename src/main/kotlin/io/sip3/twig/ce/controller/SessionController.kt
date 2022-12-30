/*
 * Copyright 2018-2022 SIP3.IO, Corp.
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

import io.sip3.twig.ce.domain.Event
import io.sip3.twig.ce.domain.SessionRequest
import io.sip3.twig.ce.service.ServiceLocator
import io.sip3.twig.ce.service.SessionService
import io.sip3.twig.ce.service.media.MediaSessionService
import io.sip3.twig.ce.service.participant.ParticipantService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.util.*
import javax.servlet.http.HttpServletResponse

@Tag(name = "Session API", description = "Session Controller")
@RestController
@RequestMapping("/session")
class SessionController {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var serviceLocator: ServiceLocator

    @Autowired
    private lateinit var mediaSessionService: MediaSessionService

    @Autowired
    private lateinit var participantService: ParticipantService

    @Operation(summary = "List session details")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns session details"),
        ApiResponse(responseCode = "400", description = "Bad request"),
        ApiResponse(responseCode = "500", description = "InternalServerError"),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError")
    )
    @PostMapping(
        value = ["/details"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun details(@RequestBody req: SessionRequest): Any? {
        return getSessionService(req).details(req)
    }

    @Operation(summary = "List session content")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns session messages and host list"),
        ApiResponse(responseCode = "400", description = "Bad request"),
        ApiResponse(responseCode = "500", description = "InternalServerError"),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError")
    )
    @PostMapping(
        value = ["/content"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun content(@RequestBody req: SessionRequest): Map<String, Any> {
        val messages = getSessionService(req).content(req)
        val hosts = mutableSetOf<String>().apply {
            messages.forEach { message ->
                add(message.getString("src_host") ?: message.getString("src_addr"))
                add(message.getString("dst_host") ?: message.getString("dst_addr"))
            }
        }

        return mapOf(
            "hosts" to hosts,
            "messages" to messages
        )
    }

    @Operation(summary = "Build session flow")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns session events and participants info"),
        ApiResponse(responseCode = "400", description = "Bad request"),
        ApiResponse(responseCode = "500", description = "InternalServerError"),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError")
    )
    @PostMapping(
        value = ["/flow"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun flow(@RequestBody req: SessionRequest): Map<String, Any> {
        val events = mutableListOf<Event>()

        // Add SIP events
        getSessionService(req).content(req).map { message ->
            events.add(
                Event(
                    message.getLong("created_at"),
                    message.getString("src_host") ?: message.getString("src_addr"),
                    message.getString("dst_host") ?: message.getString("dst_addr"),
                    "SIP",
                    message.remove("transaction_id") as? String,
                    message
                )
            )
        }

        // Add RTPR events only for calls
        if (req.method?.firstOrNull() == "INVITE") {
            // Add RTPR events
            mediaSessionService.details(req).forEach { rtpr ->
                rtpr.values.filterNotNull().minByOrNull { it.createdAt }?.let { legSession ->
                    events.add(
                        Event(
                            legSession.createdAt,
                            legSession.srcHost ?: legSession.srcAddr,
                            legSession.dstHost ?: legSession.dstAddr,
                            "RTPR",
                            null,
                            rtpr
                        )
                    )
                }
            }

            mediaSessionService.dtmf(req).forEach { dtmf ->
                events.add(
                    Event(
                        dtmf.getLong("created_at"),
                        dtmf.getString("src_host") ?: dtmf.getString("src_addr"),
                        dtmf.getString("dst_host") ?: dtmf.getString("dst_addr"),
                        "DTMF",
                        null,
                        dtmf
                    )
                )
            }
        }
        events.sortBy { it.timestamp }

        // Collect participants
        val participants = participantService.collectParticipants(events)

        return mapOf(
            "participants" to participants,
            "events" to events
        )
    }

    @Operation(summary = "Get media session statistics")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns media session statistics"),
        ApiResponse(responseCode = "400", description = "Bad request"),
        ApiResponse(responseCode = "500", description = "InternalServerError"),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError")
    )
    @PostMapping(
        value = ["/media"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun media(@RequestBody req: SessionRequest): Any? {
        return mediaSessionService.details(req)
    }

    @Operation(summary = "Get PCAP for session")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Returns session PCAP file"),
        ApiResponse(responseCode = "400", description = "Bad request"),
        ApiResponse(responseCode = "500", description = "InternalServerError"),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError")
    )
    @PostMapping(
        value = ["/pcap"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE]
    )
    fun pcap(@RequestBody req: SessionRequest, response: HttpServletResponse) {
        response.contentType = "application/vnd.tcpdump.pcapOutputStream"
        response.setHeader("Content-Disposition", "attachment; filename=\"SIP3_${UUID.randomUUID()}.pcapOutputStream\"")

        getSessionService(req).pcap(req).use { content ->
            response.outputStream.use { response -> content.writeTo(response) }
        }
    }

    @Operation(summary = "Stash session")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Session stashed"),
        ApiResponse(responseCode = "400", description = "Bad request"),
        ApiResponse(responseCode = "500", description = "InternalServerError"),
        ApiResponse(responseCode = "504", description = "ConnectionTimeoutError")
    )
    @PostMapping(
        value = ["/stash"],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    fun stash(@RequestBody req: SessionRequest) {
        getSessionService(req).stash(req)
    }

    private fun getSessionService(req: SessionRequest): SessionService {
        val method = req.method?.firstOrNull() ?: throw IllegalArgumentException("method")
        return serviceLocator.sessionService(method)
    }
}