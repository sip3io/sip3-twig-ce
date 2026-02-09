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

package io.sip3.twig.ce.configuration

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.web.context.WebApplicationContext

@Configuration
@ConditionalOnProperty(prefix = "security.oauth2", name = ["client_id"], matchIfMissing = true)
open class SecurityConfiguration {

    private val logger = KotlinLogging.logger {}

    @Autowired
    lateinit var context: WebApplicationContext

    @Value("\${security.enabled:false}")
    private var securityEnabled = false

    @Bean
    open fun filterChain(http: HttpSecurity): SecurityFilterChain {
        if (securityEnabled) {
            val auth: AuthenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder::class.java)
            context.getBeansOfType(AuthenticationProvider::class.java).forEach { (name, provider) ->
                auth.authenticationProvider(provider)
                logger.info { "Authentication provider '$name' added." }
            }
        }

        return http.csrf { it.disable() }
            .authorizeHttpRequests { authz ->
                authz
                    // Permit all Swagger endpoints
                    .requestMatchers("/swagger-resources/**").permitAll()
                    .requestMatchers("/swagger-ui/**").permitAll()
                    .requestMatchers("/v3/api-docs/**").permitAll()
                    // Permit hoof configuration endpoint
                    .requestMatchers("/management/configuration/hoof").permitAll()
                    // Secure the rest of the endpoints accordingly to the settings
                    .anyRequest().apply {
                        if (securityEnabled) {
                            // Login form handling
                            http.formLogin { formLogin ->
                                    formLogin.successHandler { _, _, authentication ->
                                        logger.info { "Login attempt. User: ${authentication.principal}, State: SUCCESSFUL" }
                                    }
                                    formLogin.failureHandler { _, response, exception ->
                                        logger.info { "Login attempt. User: ${exception.message}, State: FAILED" }
                                        response.sendError(HttpStatus.FORBIDDEN.value())
                                    }
                                }
                                // Basic authorization handling
                                .httpBasic { }
                                // Exception handling
                                .exceptionHandling { exceptionHandlingCustomizer ->
                                    exceptionHandlingCustomizer.authenticationEntryPoint(Http403ForbiddenEntryPoint())
                                }

                            authenticated()
                        } else {
                            permitAll()
                        }
                    }
            }.build()
    }
}