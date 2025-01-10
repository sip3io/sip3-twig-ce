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

package io.sip3.twig.ce.util

import gov.nist.javax.sip.parser.StringMsgParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SipMessageUtilTest {

    init {
        StringMsgParser.setComputeContentLengthFromMessage(true)
    }

    val request = StringMsgParser().parseSIPMessage(
        """
        INVITE sip:000155917690@ss63.invite.demo.sip3.io:5060 SIP/2.0
        Via: SIP/2.0/UDP 10.177.131.211:6333;branch=z9hG4bKmqffet30b03pp5mv5jj0.1
        From: <sip:000260971282@demo.sip3.io>;tag=82-2zyzysoabqjb3
        To: <sip:000155917690@demo.sip3.io:5060>
        Call-ID: 2dnuu30ktosoky1uad3nzzk3nkk3nzz3-wdsrwt7@UAC-e-e
        CSeq: 1 INVITE
        Contact: <sip:signode-82-gxp92pqazkbzz@10.177.131.211:6333;transport=udp>
        Allow: INVITE,ACK,CANCEL,BYE,INFO,REFER,SUBSCRIBE,NOTIFY
        Allow-Events: keep-alive
        Supported: timer
        Session-Expires: 7200
        Expires: 300
        Min-SE: 900
        Max-Forwards: 63
        User-Agent: ITLCS 3.8.1
        Content-Type: application/sdp
        Content-Length: 179

        v=0
        o=- 677480114 3140674329 IN IP4 10.177.131.228
        s=centrex-mediagateway
        t=0 0
        m=audio 35176 RTP/AVP 8
        c=IN IP4 10.177.131.228
        a=rtpmap:8 PCMA/8000
        a=sendrecv
        a=ptime:20

    """.trimIndent().toByteArray(), true, false, null
    )

    val response = StringMsgParser().parseSIPMessage(
        """
        SIP/2.0 183 Session Progress
        Supported: 100rel,precondition,timer
        Content-Type: application/sdp
        Content-Disposition: session;handling=required
        Allow: ACK,BYE,CANCEL,INFO,INVITE,OPTIONS,PRACK
        Contact: <sip:000155917690@10.177.141.80:5060>;expires=180
        From: <sip:000260971282@demo.sip3.io>;tag=82-2zyzysoabqjb3
        To: <sip:000155917690@demo.sip3.io:5060>;tag=56B5324631353641B4C0D0A8
        Call-ID: 2dnuu30ktosoky1uad3nzzk3nkk3nzz3-wdsrwt7@UAC-e-e
        CSeq: 1 INVITE
        Via: SIP/2.0/UDP 10.177.131.211:6333;branch=z9hG4bKmqffet30b03pp5mv5jj0.1;received=10.177.131.211
        Content-Length: 153

        v=0
        o=- 0 0 IN IP4 10.177.116.41
        s=-
        c=IN IP4 10.177.116.41
        t=0 0
        m=audio 36046 RTP/AVP 8
        b=AS:80
        a=rtpmap:8 PCMA/8000
        a=ptime:20
        a=maxptime:20

    """.trimIndent().toByteArray(), true, false, null
    )

    @Test
    fun `check requestUri() method`() {
        assertEquals("sip:000155917690@ss63.invite.demo.sip3.io:5060", request.requestUri())
        assertNull(response.requestUri())
    }

    @Test
    fun `check fromUri() method`() {
        assertEquals("sip:000260971282@demo.sip3.io", request.fromUri())
        assertEquals("sip:000260971282@demo.sip3.io", response.fromUri())
    }

    @Test
    fun `check toUri() method`() {
        assertEquals("sip:000155917690@demo.sip3.io:5060", request.toUri())
        assertEquals("sip:000155917690@demo.sip3.io:5060", response.toUri())
    }

    @Test
    fun `check method() method`() {
        assertEquals("INVITE", request.method())
        assertNull(response.method())
    }

    @Test
    fun `check callId() method`() {
        assertEquals("2dnuu30ktosoky1uad3nzzk3nkk3nzz3-wdsrwt7@UAC-e-e", request.callId())
        assertEquals("2dnuu30ktosoky1uad3nzzk3nkk3nzz3-wdsrwt7@UAC-e-e", response.callId())
    }
}
