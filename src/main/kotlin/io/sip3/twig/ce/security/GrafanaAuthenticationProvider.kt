package io.sip3.twig.ce.security

import mu.KotlinLogging
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
class GrafanaAuthenticationProvider(private val config: GrafanaSecurityConfiguration) : AuthenticationProvider {

    private val logger = KotlinLogging.logger {}

    private val restTemplate = RestTemplate()

    override fun authenticate(auth: Authentication?): Authentication {
        requireNotNull(auth) { "Authentication data required." }
        val username = auth.name
        val password = auth.credentials as? String

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