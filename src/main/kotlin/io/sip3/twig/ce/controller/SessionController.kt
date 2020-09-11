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

import io.sip3.twig.ce.domain.Event
import io.sip3.twig.ce.domain.Participant
import io.sip3.twig.ce.domain.SessionRequest
import io.sip3.twig.ce.service.ServiceLocator
import io.sip3.twig.ce.service.SessionService
import io.sip3.twig.ce.service.host.HostService
import io.sip3.twig.ce.service.media.MediaSessionService
import io.swagger.annotations.Api
import mu.KotlinLogging
import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import springfox.documentation.swagger2.annotations.EnableSwagger2
import java.util.*
import javax.servlet.http.HttpServletResponse

@EnableSwagger2
@Api(
        tags = ["Session API"]
)
@RestController
@RequestMapping("/session")
class SessionController {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var serviceLocator: ServiceLocator

    @Autowired
    private lateinit var mediaSessionService: MediaSessionService

    @Autowired
    private lateinit var hostService: HostService

    @PostMapping("/details")
    fun details(@RequestBody req: SessionRequest): Any? {
        return getSessionService(req).details(req)
    }

    @PostMapping("/content")
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

    @PostMapping("/flow")
    fun flow(@RequestBody req: SessionRequest): Map<String, Any> {
        val events = mutableListOf<Event>()

        // Add SIP events
        getSessionService(req).content(req).map { message ->
            events.add(Event(
                    message.getLong("created_at"),
                    message.getString("src_host") ?: message.getString("src_addr"),
                    message.getString("dst_host") ?: message.getString("dst_addr"),
                    "SIP",
                    message
            ))
        }

        // Add RTPR events only for calls
        if (req.method?.firstOrNull() == "INVITE") {
            // Add RTPR events
            mediaSessionService.details(req).forEach { rtpr ->
                rtpr.values.filterNotNull().minBy { it.createdAt }?.let { legSession ->
                    events.add(Event(
                            legSession.createdAt,
                            legSession.srcHost ?: legSession.srcAddr,
                            legSession.dstHost ?: legSession.dstAddr,
                            "RTPR",
                            rtpr
                    ))
                }
            }
        }

        // Collect participants
        val participants = mutableSetOf<String>().apply {
            events.forEach {
                add(it.src)
                add(it.dst)
            }
        }.map { name ->
            val host = hostService.findByNameIgnoreCase(name) ?: Document()
            Participant(name, "host", host)
        }

        events.sortBy { it.timestamp }
        return mapOf(
                "participants" to participants,
                "events" to events
        )
    }

    @PostMapping("/media")
    fun media(@RequestBody req: SessionRequest): Any? {
        return mediaSessionService.details(req)
    }

    @PostMapping("/pcap")
    fun pcap(@RequestBody req: SessionRequest, response: HttpServletResponse) {
        response.contentType = "application/vnd.tcpdump.pcapOutputStream"
        response.setHeader("Content-Disposition", "attachment; filename=\"SIP3_${UUID.randomUUID()}.pcapOutputStream\"")

        getSessionService(req).pcap(req).use { content ->
            response.outputStream.use { response -> content.writeTo(response) }
        }
    }

    private fun getSessionService(req: SessionRequest): SessionService {
        val method = req.method?.firstOrNull() ?: throw IllegalArgumentException("method")
        return serviceLocator.sessionService(method)
    }
}