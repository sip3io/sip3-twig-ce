/*
 * Copyright 2018-2024 SIP3.IO, Corp.
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
import jakarta.validation.ValidationException
import org.bson.conversions.Bson
import org.springframework.beans.factory.annotation.Autowired

abstract class SearchService {

    @Autowired
    protected lateinit var attributeService: AttributeService

    @Autowired
    protected lateinit var mongoClient: MongoClient

    abstract fun search(request: SearchRequest): Iterator<SearchResponse>

    fun filter(expression: String, transform: ((String) -> String)? = null): Bson {
        return when {
            expression.contains("!=") -> filter(expression, "!=") { field, value ->
                ne(transform?.invoke(field) ?: field, value)
            }

            expression.contains(">") -> filter(expression, ">") { field, value ->
                gt(transform?.invoke(field) ?: field, value)
            }

            expression.contains("<") -> filter(expression, "<") { field, value ->
                lt(transform?.invoke(field) ?: field, value)
            }

            expression.contains("=~") -> filter(expression, "=~") { field, value ->
                if (value is String) {
                    regex(transform?.invoke(field) ?: field, value)
                } else {
                    throw ValidationException("Attribute doesn't support regex query: $expression")
                }
            }

            expression.contains("=") -> filter(expression, "=") { field, value ->
                eq(transform?.invoke(field) ?: field, value)
            }

            else -> {
                throw ValidationException("Couldn't parse the expression: $expression")
            }
        }
    }

    private fun filter(expression: String, delimiter: String, mapping: (String, Any) -> Bson): Bson {
        val attribute = expression.substringBefore(delimiter)

        val field = attribute.substringAfter(".")
        val type = attributeService.list()
            .firstOrNull { it.name == attribute }
            ?.type

        val rawValue = expression.substringAfter(delimiter)
        val value = when (type) {
            Attribute.TYPE_STRING -> rawValue
            Attribute.TYPE_NUMBER -> readNumber(rawValue)
            Attribute.TYPE_BOOLEAN -> rawValue.toBoolean()
            else -> {
                throw ValidationException("Unknown attribute: $expression")
            }
        }

        return AttributeService.VIRTUAL_ATTRIBUTES[attribute]?.attributes
            ?.map { mapping.invoke(it.substringAfter("."), value) }
            ?.let { or(it) }
            ?: mapping.invoke(field, value)
    }

    private fun readNumber(rawValue: String): Double {
        return when {
            rawValue.endsWith("s") -> rawValue.replace("s", "").toDouble() * 1000
            rawValue.endsWith("m") -> rawValue.replace("m", "").toDouble() * 60 * 1000
            else -> rawValue.toDouble()
        }
    }
}
