/*
 * Copyright 2018-2021 SIP3.IO, Corp.
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

package io.sip3.twig.ce.security

import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component("grafana")
@ConditionalOnProperty(prefix = "security.grafana", name = ["url"])
class GrafanaAuthenticationProvider(private val config: GrafanaSecurityConfiguration) : AuthenticationProvider {

    private val logger = KotlinLogging.logger {}

    private val restTemplate = RestTemplate()

    override fun authenticate(auth: Authentication?): Authentication {
        requireNotNull(auth) { "Authentication data required." }
        val username = auth.name
        val password = auth.credentials as String

        val headers = HttpHeaders()
        headers.setBasicAuth(username, password)

        try {
            val statusCode = restTemplate.exchange(config.url, HttpMethod.GET, HttpEntity<String>(headers), String::class.java).statusCode
            if (statusCode.is2xxSuccessful) {
                return UsernamePasswordAuthenticationToken(username, password, emptyList())
            }
        } catch (e: Exception) {
            logger.error(e) { "GrafanaAuthenticationProvider `authenticate()` failed." }
        }

        throw BadCredentialsException(username)
    }

    override fun supports(auth: Class<*>?): Boolean {
        return auth?.equals(UsernamePasswordAuthenticationToken::class.java) ?: false
    }
}

@Component
@ConfigurationProperties("security.grafana")
open class GrafanaSecurityConfiguration {

    lateinit var url: String
}