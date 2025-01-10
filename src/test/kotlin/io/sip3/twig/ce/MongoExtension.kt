/*
 * Copyright 2018-2025 SIP3.IO, Corp.
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

package io.sip3.twig.ce

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.MongoDBContainer

class MongoExtension : BeforeAllCallback {

    companion object {

        @JvmField
        val MONGODB_CONTAINER = MongoDBContainer("mongo:4.4").apply {
            start()
        }

        val MONGO_URI
            get() = "mongodb://${MONGODB_CONTAINER.host}:${MONGODB_CONTAINER.firstMappedPort}"
    }

    override fun beforeAll(context: ExtensionContext?) {
        // Do nothing
    }

    class MongoDbInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
            TestPropertyValues.of(
                "time-suffix=yyyyMMdd",
                "mongo.uri=${MONGO_URI}",
                "mongo.db=sip3-test",
                "mongo.max-execution-time=1000",
                "mongo.batch-size=128"
            ).applyTo(configurableApplicationContext.environment)
        }
    }
}
