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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RestController
import springfox.documentation.builders.ApiInfoBuilder
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.*
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spi.service.contexts.SecurityContext
import springfox.documentation.spring.web.plugins.Docket
import javax.annotation.PostConstruct

@Configuration
open class SwaggerConfiguration {

    @Value("\${security.enabled}")
    private var securityEnabled = false

    @Autowired
    var buildProperties: BuildProperties? = null

    @Autowired
    lateinit var springFoxCustomization: SpringFoxCustomization

    @Bean
    open fun docket(): Docket {
        return Docket(DocumentationType.SWAGGER_2)
            .apiInfo(
                ApiInfoBuilder()
                    .title("Twig API")
                    .description(springFoxCustomization.description)
                    .version(buildProperties?.version)
                    .build()
            )
            .apply {
                if (securityEnabled) {
                    securityContexts(mutableListOf<SecurityContext>().apply {
                        val context = SecurityContext.builder()
                            .securityReferences(mutableListOf<SecurityReference>().apply {
                                val reference = SecurityReference("Authorization", arrayOfNulls<AuthorizationScope>(0))
                                add(reference)
                            })
                            .operationSelector { true }
                            .build()
                        add(context)
                    })
                    securitySchemes(mutableListOf<SecurityScheme>().apply {
                        val scheme = BasicAuth("Authorization")
                        add(scheme)
                    })
                }
            }
            .addTags()
            .select()
            .apis(RequestHandlerSelectors.withClassAnnotation(RestController::class.java))
            .paths(PathSelectors.any())
            .build()
            .useDefaultResponseMessages(false)
    }

    private fun Docket.addTags(): Docket {
        val tags = mutableSetOf(
            Tag("Attributes API", "", 100),
            Tag("Hosts API", "",  200),
            Tag("Search API", "",  300),
            Tag("Session API", "",  400)
        )

        tags.addAll(springFoxCustomization.additionalTags())
        this.tags(tags.first(), *tags.drop(1).toTypedArray())

        return this
    }
}

@Component
open class SpringFoxCustomization {

    lateinit var description: String

    @PostConstruct
    open fun init() {
        description = this.javaClass.classLoader.getResource("description/twig-api.md")?.readText() ?: ""
    }

    open fun additionalTags(): MutableSet<Tag> {
        return mutableSetOf()
    }
}