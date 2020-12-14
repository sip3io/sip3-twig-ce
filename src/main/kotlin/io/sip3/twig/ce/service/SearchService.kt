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

package io.sip3.twig.ce.service

import com.mongodb.client.model.Filters.*
import io.sip3.commons.domain.Attribute
import io.sip3.twig.ce.domain.SearchRequest
import io.sip3.twig.ce.domain.SearchResponse
import io.sip3.twig.ce.mongo.MongoClient
import io.sip3.twig.ce.service.attribute.AttributeService
import org.bson.conversions.Bson
import org.springframework.beans.factory.annotation.Autowired
import javax.validation.ValidationException

abstract class SearchService {

    @Autowired
    protected lateinit var attributeService: AttributeService

    @Autowired
    protected lateinit var mongoClient: MongoClient

    abstract fun search(request: SearchRequest): Iterator<SearchResponse>

    fun filter(expression: String): Bson {
        return when {
            expression.contains("!=") -> {
                val (field, value) = readAttribute(expression, "!=")
                ne(field, value)
            }
            expression.contains(">") -> {
                val (field, value) = readAttribute(expression, ">")
                gt(field, value)
            }
            expression.contains("<") -> {
                val (field, value) = readAttribute(expression, "<")
                lt(field, value)
            }
            expression.contains("=~") -> {
                val (field, value) = readAttribute(expression, "=~")
                if (value is String) {
                    regex(field, value)
                } else {
                    throw ValidationException("Attribute doesn't support regex query: $expression")
                }
            }
            expression.contains("=") -> {
                val (field, value) = readAttribute(expression, "=")
                eq(field, value)
            }
            else -> {
                throw ValidationException("Couldn't parse the expression: $expression")
            }
        }
    }

    private fun readAttribute(expression: String, delimiter: String): Pair<String, Any> {
        val attribute = expression.substringBefore(delimiter)

        val type = attributeService.list()
            .firstOrNull { it.name == attribute }
            ?.type

        val name = attribute.substringAfter("sip.")
            .substringAfter("ip.")
            .substringAfter("rtp.")
            .substringAfter("rtcp.")

        val value = expression.substringAfter(delimiter)

        return when (type) {
            Attribute.TYPE_STRING -> Pair(name, value)
            Attribute.TYPE_NUMBER -> Pair(name, value.toDouble())
            Attribute.TYPE_BOOLEAN -> Pair(name, value.toBoolean())
            else -> {
                throw ValidationException("Unknown attribute: $expression")
            }
        }
    }
}
