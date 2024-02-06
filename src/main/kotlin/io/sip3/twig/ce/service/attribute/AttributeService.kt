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

package io.sip3.twig.ce.service.attribute

import io.sip3.commons.domain.Attribute
import io.sip3.twig.ce.mongo.MongoClient
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

@Component
open class AttributeService(private val mongoClient: MongoClient) {

    companion object {

        val VIRTUAL_ATTRIBUTES = mutableMapOf<String, VirtualAttribute>().apply {
            put("sip.addr", VirtualAttribute(Attribute.TYPE_STRING, "sip.src_addr", "sip.dst_addr"))
            put("rtp.addr", VirtualAttribute(Attribute.TYPE_STRING, "rtp.src_addr", "rtp.dst_addr"))
            put("rtcp.addr", VirtualAttribute(Attribute.TYPE_STRING, "rtcp.src_addr", "rtcp.dst_addr"))

            put("sip.host", VirtualAttribute(Attribute.TYPE_STRING, "sip.src_host", "sip.dst_host"))
            put("rtp.host", VirtualAttribute(Attribute.TYPE_STRING, "rtp.src_host", "rtp.dst_host"))
            put("rtcp.host", VirtualAttribute(Attribute.TYPE_STRING, "rtcp.src_host", "rtcp.dst_host"))

            put("sip.user", VirtualAttribute(Attribute.TYPE_STRING, "sip.caller", "sip.callee"))
        }

        val ATTRIBUTE_COMPARATOR = compareBy<Attribute>(
            { ATTRIBUTE_WEIGHTS[it.name] ?: Int.MAX_VALUE },
            { it.name }
        )

        private val ATTRIBUTE_WEIGHTS = mutableMapOf<String, Int>().apply {
            // Prefix: sip
            put("sip.addr", 100)
            put("sip.src_addr", 110)
            put("sip.dst_addr", 120)

            put("sip.host", 130)
            put("sip.src_host", 140)
            put("sip.dst_host", 150)

            put("sip.user", 200)
            put("sip.caller", 210)
            put("sip.callee", 220)

            put("sip.method", 300)
            put("sip.call_id", 310)
            put("sip.state", 320)
            put("sip.error_code", 330)
            put("sip.error_type", 340)
            put("sip.duration", 350)

            put("sip.trying_delay", 400)
            put("sip.setup_time", 410)
            put("sip.cancel_time", 420)
            put("sip.establish_time", 430)
            put("sip.disconnect_time", 440)

            put("sip.transactions", 500)
            put("sip.retransmits", 510)
            put("sip.terminated_by", 520)

            put("sip.register_delay", 600)
            put("sip.overlapped_interval", 610)
            put("sip.overlapped_fraction", 620)

            // Prefix: rtp
            put("rtp.addr", 1100)
            put("rtp.src_addr", 1110)
            put("rtp.dst_addr", 1120)

            put("rtp.host", 1130)
            put("rtp.src_host", 1140)
            put("rtp.dst_host", 1150)

            put("rtp.mos", 1200)
            put("rtp.r_factor", 1210)
            put("rtp.codec", 1220)

            put("rtp.bad_report_fraction", 1300)
            put("rtp.one_way", 1310)

            // Prefix: rtcp
            put("rtcp.addr", 1100)
            put("rtcp.src_addr", 1110)
            put("rtcp.dst_addr", 1120)

            put("rtcp.host", 1130)
            put("rtcp.src_host", 1140)
            put("rtcp.dst_host", 1150)

            put("rtcp.mos", 1200)
            put("rtcp.r_factor", 1210)
            put("rtcp.codec", 1220)

            put("rtcp.bad_report_fraction", 1300)
            put("rtcp.one_way", 1310)
        }
    }

    @Cacheable(value = ["listAttributes"])
    open fun list(): Collection<Attribute> {
        val attributes = mutableMapOf<String, Attribute>()

        val collections = mongoClient.listCollectionNames("attributes")
        mongoClient.find(collections).forEach { document ->
            val name = document.getString("name")
            val type = document.getString("type")

            val attribute = attributes.getOrPut(name) {
                Attribute().apply {
                    this.name = name
                    this.type = type
                }
            }

            if (type == Attribute.TYPE_STRING) {
                document.get("options")?.let { options ->
                    if (attribute.options == null) {
                        attribute.options = mutableSetOf()
                    }

                    @Suppress("UNCHECKED_CAST")
                    attribute.options!!.addAll(options as List<String>)
                }
            }
        }

        // Add virtual attributes
        VIRTUAL_ATTRIBUTES.forEach { (name, virtualAttribute) ->
            virtualAttribute.attributes.mapNotNull { attributes[it] }
                .ifEmpty { null }
                ?.flatMap { it.options ?: emptyList() }
                ?.let { options ->
                    attributes[name] = Attribute().apply {
                        this.name = name
                        this.type = virtualAttribute.type
                        if (options.isNotEmpty()) this.options = options.toMutableSet()
                    }
                }
        }

        return attributes.values.sortedWith(ATTRIBUTE_COMPARATOR)
    }

    class VirtualAttribute(

        val type: String,
        vararg val attributes: String
    )
}