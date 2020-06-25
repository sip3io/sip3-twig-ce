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

package io.sip3.twig.ce.mongo

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCursor
import io.sip3.commons.util.format
import mu.KotlinLogging
import org.bson.Document
import org.bson.conversions.Bson
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

@Component
open class MongoClient(@Value("\${time-suffix}") suffix: String,
                       @Value("\${mongo.uri}") uri: String,
                       @Value("\${mongo.db}") private val db: String,
                       @Value("\${mongo.max-execution-time}") private val maxExecutionTime: Long,
                       @Value("\${mongo.batch-size}") private val batchSize: Int) {

    private val logger = KotlinLogging.logger {}

    private val suffix: DateTimeFormatter = DateTimeFormatter.ofPattern(suffix)
    private val client: MongoClient = MongoClients.create(uri)

    open fun find(prefix: String, timeRange: Pair<Long, Long>, filter: Bson, sort: Bson? = null, limit: Int? = null): Iterator<Document> {
        val collections = listCollectionNames(prefix, timeRange)
        return find(collections, filter, sort, limit)
    }

    open fun find(collections: List<String>, filter: Bson? = null, sort: Bson? = null, limit: Int? = null): Iterator<Document> {
        val collectionNames = collections.iterator()
        return object : Iterator<Document> {

            var cursor: MongoCursor<Document>? = null

            override fun hasNext(): Boolean {
                if (cursor != null && cursor!!.hasNext()) return true

                if (!collectionNames.hasNext()) return false

                if (collectionNames.hasNext()) {
                    cursor = client.getDatabase(db)
                            .getCollection(collectionNames.next())
                            .run {
                                if (filter != null) find(filter) else find()
                            }
                            .apply {
                                maxTime(maxExecutionTime, TimeUnit.MILLISECONDS)
                                batchSize(limit ?: batchSize)
                                sort?.let { sort(it) }
                            }
                            .iterator()
                }

                return hasNext()
            }

            override fun next(): Document {
                if (!hasNext()) throw NoSuchElementException()
                return cursor!!.next()
            }
        }
    }

    open fun listCollectionNames(prefix: String, timeRange: Pair<Long, Long>): List<String> {
        return listCollectionNames(prefix).asSequence()
                .filter { name -> "${prefix}_${suffix.format(timeRange.first)}" <= name }
                .filter { name -> "${prefix}_${suffix.format(timeRange.second)}" >= name }
                .toList()
    }

    @Cacheable(value = ["listCollectionNames"], key = "#prefix")
    open fun listCollectionNames(prefix: String): List<String> {
        return client.getDatabase(db).listCollectionNames().asSequence()
                .filter { name -> name.startsWith(prefix) }
                .sorted()
                .toList()
    }
}