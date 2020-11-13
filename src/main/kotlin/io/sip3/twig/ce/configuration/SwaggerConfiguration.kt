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

package io.sip3.twig.ce.configuration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RestController
import springfox.documentation.builders.ApiInfoBuilder
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.AuthorizationScope
import springfox.documentation.service.BasicAuth
import springfox.documentation.service.SecurityReference
import springfox.documentation.service.SecurityScheme
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spi.service.contexts.SecurityContext
import springfox.documentation.spring.web.plugins.Docket

@Configuration
open class SwaggerConfiguration {

    @Value("\${security.enabled}")
    private var securityEnabled = false

    @Autowired
    var buildProperties: BuildProperties? = null

    @Bean
    open fun docket(): Docket {
        return Docket(DocumentationType.SWAGGER_2)
                .apiInfo(ApiInfoBuilder()
                        .title("Twig API")
                        .version(buildProperties?.version)
                        .build())
                .apply {
                    if (securityEnabled) {
                        securityContexts(mutableListOf<SecurityContext>().apply {
                            val context = SecurityContext.builder()
                                    .securityReferences(mutableListOf<SecurityReference>().apply {
                                        val reference = SecurityReference("Authorization", arrayOfNulls<AuthorizationScope>(0))
                                        add(reference)
                                    })
                                    // However, this method is deprecated, I couldn't find a clean example of how to use a recommended one
                                    // Let's use it till they will update official documentation
                                    .forPaths(PathSelectors.any())
                                    .build()
                            add(context)
                        })
                        securitySchemes(mutableListOf<SecurityScheme>().apply {
                            val scheme = BasicAuth("Authorization")
                            add(scheme)
                        })
                    }
                }
                .select()
                .apis(RequestHandlerSelectors.withClassAnnotation(RestController::class.java))
                .paths(PathSelectors.any())
                .build()
                .useDefaultResponseMessages(false)
    }
}