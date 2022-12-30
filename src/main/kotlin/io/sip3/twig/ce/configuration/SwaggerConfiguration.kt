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

package io.sip3.twig.ce.configuration

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Configuration
open class SwaggerConfiguration {

    @Value("\${security.enabled}")
    private var securityEnabled = false

    @Autowired
    var buildProperties: BuildProperties? = null

    @Autowired
    lateinit var swaggerCustomization: SwaggerCustomization

    @Bean
    open fun openApi(): OpenAPI {
        return OpenAPI()
            .info(
                Info().title("Twig API")
                    .description(swaggerCustomization.description)
                    .version(buildProperties?.version)
            )
            .addTags()
            .apply {
                if (securityEnabled) {
                    components(
                        Components()
                            .addSecuritySchemes(
                                "Authorization",
                                SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")
                            )
                    )
                    addSecurityItem(SecurityRequirement().addList("Authorization"))
                }
            }
    }

    private fun OpenAPI.addTags(): OpenAPI {
        val tags = mutableSetOf(
            100 to Tag().name("Attributes API"),
            200 to Tag().name("Hosts API"),
            300 to Tag().name("Search API"),
            400 to Tag().name("Session API")
        )
        tags.addAll(swaggerCustomization.additionalTags())

        tags.sortedBy { it.first }
            .map { it.second }
            .let {
                this.tags(it)
            }
        return this
    }
}

@Component
open class SwaggerCustomization {

    lateinit var description: String

    @PostConstruct
    open fun init() {
        description = this.javaClass.classLoader.getResource("description/twig-api.md")?.readText() ?: ""
    }

    open fun additionalTags(): MutableSet<Pair<Int,Tag>> {
        return mutableSetOf()
    }
}