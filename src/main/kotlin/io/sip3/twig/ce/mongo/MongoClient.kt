/*
 * Copyright 2018-2023 SIP3.IO, Corp.
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

package io.sip3.twig.ce.mongo

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCursor
import com.mongodb.client.model.WriteModel
import io.sip3.commons.util.format
import mu.KotlinLogging
import org.bson.Document
import org.bson.conversions.Bson
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct

@Component
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
open class MongoClient {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var proxy: io.sip3.twig.ce.mongo.MongoClient

    @Value("\${time_suffix:yyyyMM}")
    protected lateinit var timeSuffix: String

    @Value("\${mongo.uri}")
    protected lateinit var uri: String

    @Value("\${mongo.db}")
    protected lateinit var db: String

    @Value("\${mongo.max_execution_time:30000}")
    protected var maxExecutionTime: Long = 30000

    @Value("\${mongo.batch_size:1}")
    protected val batchSize: Int = 1

    protected open lateinit var suffix: DateTimeFormatter
    protected open lateinit var client: MongoClient

    @PostConstruct
    open fun init() {
        suffix = DateTimeFormatter.ofPattern(timeSuffix)
        client = MongoClients.create(uri)
    }

    open fun find(prefix: String, timeRange: Pair<Long, Long>, filter: Bson, sort: Bson? = null, limit: Int? = null): Iterator<Document> {
        val collections = listCollectionNames(prefix, timeRange)
        return find(collections, filter, sort, limit)
    }

    open fun find(collections: Collection<String>, filter: Bson? = null, sort: Bson? = null, limit: Int? = null): Iterator<Document> {
        val collectionNames = collections.iterator()

        return object : Iterator<Document> {

            var cursor: MongoCursor<Document>? = null

            override fun hasNext(): Boolean {
                if (cursor?.hasNext() == true) return true

                if (collectionNames.hasNext()) {
                    cursor = client.getDatabase(db)
                        .getCollection(collectionNames.next())
                        .run {
                            filter?.let { find(filter) } ?: find()
                        }
                        .apply {
                            maxTime(maxExecutionTime, TimeUnit.MILLISECONDS)
                            batchSize(limit ?: batchSize)
                            sort?.let { sort(it) }
                        }
                        .iterator()

                    return hasNext()
                }

                return false
            }

            override fun next(): Document {
                if (!hasNext()) throw NoSuchElementException()
                return cursor!!.next()
            }
        }
    }

    open fun bulkWrite(collection: String, operations: List<WriteModel<Document>>) {
        client.getDatabase(db).getCollection(collection).bulkWrite(operations)
    }

    open fun listCollectionNames(prefix: String, timeRange: Pair<Long, Long>): Collection<String> {
        val range = "${prefix}_${suffix.format(timeRange.first)}".."${prefix}_${suffix.format(timeRange.second)}"
        return proxy.listCollectionNames(prefix)
            .filter { name -> name in range }
    }

    @Cacheable(value = ["listCollectionNamesByPrefix"], key = "#prefix")
    open fun listCollectionNames(prefix: String): Collection<String> {
        return proxy.listCollectionNames()
            .filter { name -> name.startsWith(prefix) }
            .toSortedSet()
    }

    @Cacheable(value = ["listCollectionNames"])
    open fun listCollectionNames(): Collection<String> {
        return client.getDatabase(db).listCollectionNames().toList()
    }
}