package io.sip3.twig.ce.service

import io.sip3.twig.ce.mongo.MongoClient
import io.sip3.twig.ce.service.attribute.AttributeService
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "cache", name = ["refresh-delay"])
@EnableScheduling
class CacheService {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var mongoClient: MongoClient

    @Autowired
    private lateinit var attributeService: AttributeService

    @Scheduled(fixedDelayString = "\${cache.refresh-delay}", initialDelayString = "\${cache.refresh-delay}")
    fun refreshCache() {
        try {
            mongoClient.listCollectionNames()
            attributeService.list()
        } catch (e: Exception) {
            logger.error(e) { "CacheService `refreshCache()` failed." }
        }
    }
}