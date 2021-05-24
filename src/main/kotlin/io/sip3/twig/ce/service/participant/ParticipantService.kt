package io.sip3.twig.ce.service.participant

import io.sip3.twig.ce.domain.Event
import io.sip3.twig.ce.domain.Participant
import io.sip3.twig.ce.service.host.HostService
import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
open class ParticipantService {

    @Autowired
    private lateinit var hostService: HostService

    open fun collectParticipants(events: List<Event>): List<Participant> {
        return mutableSetOf<String>().apply {
            events.forEach {
                add(it.src)
                add(it.dst)
            }
        }.map { name ->
            val host = hostService.findByNameIgnoreCase(name) ?: Document()
            Participant(name, "host", host)
        }
    }
}