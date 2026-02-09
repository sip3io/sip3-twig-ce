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

package io.sip3.twig.ce.service.component

import io.sip3.twig.ce.configuration.TwigApplicationProperties
import io.sip3.twig.ce.domain.Component
import mu.KotlinLogging
import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.info.GitProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.net.URI
import java.time.ZoneId
import java.util.*
import java.util.concurrent.TimeUnit

@Service
open class ComponentRegistrationService {

    private val logger = KotlinLogging.logger {}

    @Autowired
    protected lateinit var twigApplicationProperties: TwigApplicationProperties

    @Autowired
    protected var buildProperties: BuildProperties? = null

    @Autowired
    protected var gitProperties: GitProperties? = null

    @Autowired
    protected lateinit var mongoTemplate: MongoTemplate

    @Autowired
    lateinit var componentService: ComponentService

    @Value("\${name:sip3-twig-ce}")
    protected lateinit var name: String

    @Value("\${server.address:0.0.0.0}")
    private val serverAddress: String? = null

    @Value("\${server.port:8080}")
    private val serverPort: String? = null

    @Value("\${server.servlet.context_path:/api}")
    private val contextPath: String? = null

    @Value("\${mongo.uri:mongodb://127.0.0.1:27017}")
    protected lateinit var mongoUri: String

    @Value("\${mongo.db:sip3}")
    protected lateinit var mongoDb: String

    protected val deploymentId = UUID.randomUUID().toString()
    protected var component: Component? = null

    @Scheduled(fixedRateString = "\${management.register_delay:60000}", timeUnit = TimeUnit.MILLISECONDS)
    open fun periodicRegistration() {
        logger.debug { "Periodic registration started" }
        if (component == null) {
            component = init()
            addBuildInfo(component!!.config)
        }
        update(component!!)

        component = componentService.register(component!!)
    }

    open fun init(): Component {
        val config = Document()
        try {
            mongoTemplate.converter.write(twigApplicationProperties, config)
        } catch (ex: Exception) {
            logger.error(ex) { "Failed to get component configuration" }
        }

        val mongoConnections = mutableSetOf<String>().apply {
            add(mongoConnectedTo(mongoUri, mongoDb))
            twigApplicationProperties.mongo
                ?.get("secondary")
                ?.let { it as? Collection<*> }
                ?.forEach { uri ->
                    add(mongoConnectedTo(uri as String, mongoDb))
                }
        }

        return Component(
            null,
            name,
            deploymentId,
            "twig",
            setOf("http://${serverAddress}:${serverPort}${contextPath}"),
            mongoConnections,
            System.currentTimeMillis(),
            0L,
            0L,
            config
        )
    }

    open fun addBuildInfo(config: Document) {
        buildProperties?.let { props ->
            config.put("version", props.version)
            config.put("project", props.artifact)
            config.put("build_at", props.time.atZone(ZoneId.of("UTC")).toString())
        }

        gitProperties?.let { props ->
            config.put("git_commit_id", props.shortCommitId)
        }
    }

    open fun update(component: Component) {
        val now = System.currentTimeMillis()
        component.updatedAt = now
        component.remoteUpdatedAt = now
    }

    private fun mongoConnectedTo(uri: String, db: String): String {
        return URI(uri).let { "${it.scheme}://${it.authority}/$db" }
    }
}