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

package io.sip3.twig.ce.service

import com.mongodb.client.model.Filters
import io.sip3.commons.domain.Attribute
import io.sip3.twig.ce.domain.SearchRequest
import io.sip3.twig.ce.domain.SearchResponse
import io.sip3.twig.ce.service.attribute.AttributeService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.stereotype.Component
import java.util.*

@SpringBootTest
class SearchServiceTest {

    companion object {

        val ATTRIBUTES = listOf(
            Attribute().apply {
                name = "sip.boolean"
                type = Attribute.TYPE_BOOLEAN
            },
            Attribute().apply {
                name = "sip.number"
                type = Attribute.TYPE_NUMBER
            },
            Attribute().apply {
                name = "sip.string"
                type = Attribute.TYPE_STRING
                options = mutableSetOf("string1", "string2")
            }
        )
    }

    @MockBean
    private lateinit var attributeService: AttributeService

    @Autowired
    @Qualifier("TEST")
    private lateinit var service: SearchService

    @Test
    fun `Validate 'filter()' method`() {
        given(attributeService.list()).willReturn(ATTRIBUTES)

        // Boolean filters
        assertEquals(Filters.eq("boolean", true), service.filter("sip.boolean=true"))
        assertEquals(Filters.ne("boolean", true), service.filter("sip.boolean!=true"))

        // String filters
        assertEquals(Filters.eq("string", "value"), service.filter("sip.string=value"))
        assertEquals(Filters.ne("string", "value"), service.filter("sip.string!=value"))
        assertEquals(Filters.regex("string", "value"), service.filter("sip.string=~value"))

        // Number filters
        assertEquals(Filters.eq("number", 1.0), service.filter("sip.number=1"))
        assertEquals(Filters.ne("number", 1.0), service.filter("sip.number!=1"))
        assertEquals(Filters.gt("number", 1.0), service.filter("sip.number>1"))
        assertEquals(Filters.lt("number", 1.0), service.filter("sip.number<1"))
        assertEquals(Filters.lt("number", 1000.0), service.filter("sip.number<1s"))
        assertEquals(Filters.lt("number", 60000.0), service.filter("sip.number<1m"))

        // Field name with prefix
        assertEquals(Filters.ne("prefix.number", 1.0), service.filter("sip.number!=1") { "prefix.$it" })
        assertEquals(Filters.gt("prefix.number", 1.0), service.filter("sip.number>1") { "prefix.$it" })
        assertEquals(Filters.lt("prefix.number", 1.0), service.filter("sip.number<1") { "prefix.$it" })
        assertEquals(Filters.regex("prefix.string", "value"), service.filter("sip.string=~value") { "prefix.$it" })
        assertEquals(Filters.eq("prefix.number", 1.0), service.filter("sip.number=1") { "prefix.$it" })
    }
}

@Component("TEST")
open class TestSearchService : SearchService() {

    override fun search(request: SearchRequest): Iterator<SearchResponse> {
        return Collections.emptyIterator()
    }
}
