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

    fun filter(expression: String, transform: ((String) -> String)? = null): Bson {
        return when {
            expression.contains("!=") -> {
                val (field, value) = readAttribute(expression, "!=")
                ne(transform?.invoke(field) ?: field, value)
            }
            expression.contains(">") -> {
                val (field, value) = readAttribute(expression, ">")
                gt(transform?.invoke(field) ?: field, value)
            }
            expression.contains("<") -> {
                val (field, value) = readAttribute(expression, "<")
                lt(transform?.invoke(field) ?: field, value)
            }
            expression.contains("=~") -> {
                val (field, value) = readAttribute(expression, "=~")
                if (value is String) {
                    regex(transform?.invoke(field) ?: field, value)
                } else {
                    throw ValidationException("Attribute doesn't support regex query: $expression")
                }
            }
            expression.contains("=") -> {
                val (field, value) = readAttribute(expression, "=")
                eq(transform?.invoke(field) ?: field, value)
            }
            else -> {
                throw ValidationException("Couldn't parse the expression: $expression")
            }
        }
    }

    private fun readAttribute(expression: String, delimiter: String): Pair<String, Any> {
        val attribute = expression.substringBefore(delimiter)

        val name = attribute.substringAfter(".")
        val type = attributeService.list()
            .firstOrNull { it.name == attribute }
            ?.type

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
