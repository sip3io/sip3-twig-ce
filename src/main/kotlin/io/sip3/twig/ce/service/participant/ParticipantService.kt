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

package io.sip3.twig.ce.service.participant

import gov.nist.javax.sip.message.SIPMessage
import gov.nist.javax.sip.parser.StringMsgParser
import io.sip3.twig.ce.domain.Event
import io.sip3.twig.ce.domain.Participant
import io.sip3.twig.ce.service.host.HostService
import io.sip3.twig.ce.util.address
import io.sip3.twig.ce.util.hasSdp
import io.sip3.twig.ce.util.method
import io.sip3.twig.ce.util.sessionDescription
import mu.KotlinLogging
import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
open class ParticipantService {

    init {
        StringMsgParser.setComputeContentLengthFromMessage(true)
    }

    protected val logger = KotlinLogging.logger {}

    @Autowired
    protected lateinit var hostService: HostService

    open fun collectParticipants(events: List<Event>): List<Participant> {
        var isFirst = true
        val hasMedia = events.any { it.type == "RTPR" }

        val namesFromEvents = mutableSetOf<String>().apply {
            events.forEach { event ->
                add(event.src)
                add(event.dst)
            }
        }

        val names = mutableSetOf<String>()
        events.forEach { event ->
            val eventHosts = listOf(event.src, event.dst)
            if (hasMedia && event.type == "SIP") {
                val sipMessage = parseSIPMessage(event)
                if (sipMessage != null && sipMessage.hasSdp()) {
                    val mediaAddresses = collectMediaAddresses(sipMessage)
                        .map { hostService.findByAddr(it)?.name ?: it }

                    if (sipMessage.method() == "INVITE" && isFirst) {
                        isFirst = false
                        names.addAll(mediaAddresses)
                    } else {
                        names.addAll(eventHosts)
                        names.addAll(mediaAddresses)
                    }
                }
            }

            names.addAll(eventHosts)
        }

        return names.intersect(namesFromEvents)
            .map { name ->
                val host = hostService.findByNameIgnoreCase(name) ?: Document()
                Participant(name, "host", host)
            }
    }

    open fun parseSIPMessage(event: Event): SIPMessage? {
        val rawData = (event.details as Document).getString("raw_data")

        try {
            return StringMsgParser().parseSIPMessage(rawData.toByteArray(Charsets.ISO_8859_1), true, false, null)
        } catch (e: Exception) {
            logger.error(e) { "StringMsgParser 'parseSIPMessage()' failed." }
            return null
        }
    }

    open fun collectMediaAddresses(sipMessage: SIPMessage): Set<String> {
        val sessionDescription = try {
            sipMessage.sessionDescription()
        } catch (e: Exception) {
            logger.error(e) { "SIPMessage 'sessionDescription()' failed." }
            null
        }

        val mediaDescription = sessionDescription?.getMediaDescription("audio") ?: return emptySet()
        return mutableSetOf<String>().apply {
            add(mediaDescription.address())
            mediaDescription.candidates?.forEach { candidate ->
                candidate.address?.let { add(it) }
                candidate.relatedAddress?.let { add(it) }
            }
        }
    }
}