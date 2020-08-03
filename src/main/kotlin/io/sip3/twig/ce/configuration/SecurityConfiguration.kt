package io.sip3.twig.ce.configuration

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.stereotype.Component
import org.springframework.web.context.WebApplicationContext
import java.util.*

@Configuration
open class SecurityConfiguration(private val security: SecurityConfigurationProperties) : WebSecurityConfigurerAdapter() {

    private val logger = KotlinLogging.logger {}

    @Autowired
    lateinit var context: WebApplicationContext

    override fun configure(http: HttpSecurity?) {
        http!!.csrf().disable()
                .authorizeRequests().anyRequest().apply {
                    if (security.enabled) {
                        authenticated().and()
                                .formLogin()
                                .successHandler { _, _, authentication ->
                                    logger.info("Login attempt. User: ${authentication.principal}, State: SUCCESSFUL")
                                }
                                .failureHandler { _, response, exception ->
                                    logger.info("Login attempt. User: ${exception.message}, State: FAILED")
                                    response.sendError(HttpStatus.FORBIDDEN.value())
                                }
                                .and()
                                .exceptionHandling()
                                .authenticationEntryPoint(Http403ForbiddenEntryPoint())
                    } else {
                        permitAll()
                    }
                }
    }

    override fun configure(auth: AuthenticationManagerBuilder?) {
        if (security.enabled) {
            val providers = context.getBeanNamesForType(AuthenticationProvider::class.java)

            val enabledProviders = mutableListOf<String>().apply {
                security.grafana?.let { add("grafana")}
                security.mailChimp?.let { add("mailchimp")}
                security.ldap?.let { add("ldap")}
                security.file?.let { add("file")}

                intersect(providers.asIterable())
            }

            enabledProviders.forEach { name ->
                if (providers.contains(name)) {
                    val provider = context.getBean(name, AuthenticationProvider::class.java)
                    auth!!.authenticationProvider(provider)
                    logger.info { "Authentication provider '$name' added." }
                }
            }
        }
    }
}

@Component
@ConfigurationProperties(prefix = "security")
open class SecurityConfigurationProperties {

    var enabled: Boolean = false

    var mailChimp: Properties? = null
    var ldap: Properties? = null
    var grafana: Properties? = null
    var file: Properties? = null
}