/*
 * Copyright 2018-2021 SIP3.IO, Inc.
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

        return events.flatMap { event ->
                if (event.type == "SIP") {
                    val sipMessage = parseSIPMessage(event)
                    if (sipMessage != null && sipMessage.hasSdp()) {
                        val mediaDescription = sipMessage.sessionDescription()!!.getMediaDescription("audio")

                        if (sipMessage.method() == "INVITE" && isFirst) {
                            isFirst = false
                            return@flatMap listOf(mediaDescription.address(), event.src, event.dst)
                        } else {
                            return@flatMap listOf(event.dst, event.src, mediaDescription.address())
                        }
                    }
                }

                return@flatMap listOf(event.src, event.dst)
            }
            .toSet()
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
            logger.error("StringMsgParser 'parseSIPMessage()' failed.", e)
            return null
        }
    }
}