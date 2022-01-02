/*
 * Copyright 2018-2022 SIP3.IO, Corp.
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

package io.sip3.twig.ce.service.participant

import io.sip3.twig.ce.domain.AddressMapping
import io.sip3.twig.ce.domain.Event
import io.sip3.twig.ce.domain.Host
import io.sip3.twig.ce.service.host.HostService
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.internal.util.MockUtil.resetMock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest
class ParticipantServiceTest {

    companion object {

        val HOST_1 = Host(
            "id1",
            "host1",
            listOf("10.10.10.0:5060", "10.10.10.0/28"),
            listOf(AddressMapping("10.0.0.1", "10.0.0.1"))
        )
        val HOST_2 = Host(
            "id2",
            "host2",
            listOf("10.10.20.0:5060", "10.10.20.0/28"),
            listOf(AddressMapping("10.0.0.1", "10.0.0.1"))
        )
        val HOST_3 = Host(
            "id3",
            "host3",
            listOf("10.242.2.7"),
            emptyList()
        )

        val EVENT_1 = Event(System.currentTimeMillis(), HOST_1.name, "10.20.30.40", "SIP", "2dnuu30ktosoky1uad3nzzk3nkk3nzz3-wdsrwt7@UAC-e-e:z9hG4bKmqffet30b03pp5mv5jj0.1:1", Document().apply {
            put("type", "SIP")
            put("src", HOST_1.name)
            put("dst", "10.20.30.40")

            put(
                "raw_data", """
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
                a=candidate:2342127660 1 udp 2122260223 172.18.32.1 61818 typ host generation 0 network-id 1
                a=candidate:1621512748 1 udp 2122194687 10.242.2.7 61819 typ host generation 0 network-id 2
                a=candidate:1897852119 1 udp 2122129151 192.168.1.28 61820 typ host generation 0 network-id 3
                a=candidate:3306812636 1 tcp 1518280447 172.18.32.1 9 typ host tcptype active generation 0 network-id 1
                a=candidate:774221532 1 tcp 1518214911 10.242.2.7 9 typ host tcptype active generation 0 network-id 2
                a=candidate:1067257895 1 tcp 1518149375 192.168.1.28 9 typ host tcptype active generation 0 network-id 3
                a=candidate:2265879811 1 udp 1685921535 8.8.8.8 41999 typ srflx raddr 192.168.1.28 rport 61820 generation 0 network-id 3 
                """.trimIndent()
            )
        })

        val EVENT_2 = Event(System.currentTimeMillis(), "10.20.30.40", HOST_1.name, "SIP", "2dnuu30ktosoky1uad3nzzk3nkk3nzz3-wdsrwt7@UAC-e-e:z9hG4bKmqffet30b03pp5mv5jj0.1:1", Document().apply {
            put("type", "SIP")
            put("src", "10.20.30.40")
            put("dst", HOST_1.name)

            put(
                "raw_data", """
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
                """.trimIndent()
            )
        })

        val EVENT_3 = Event(System.currentTimeMillis(), "20.20.30.40", HOST_2.name, "SIP", "2dnuu30ktosoky1uad3nzzk3nkk3nzz3-wdsrwt7@UAC-e-e:z9hG4bKmqffet30b03pp5mv5jj0.1:1", Document().apply {
            put("type", "SIP")
            put("src", "20.20.30.40")
            put("dst", HOST_2.name)

            put(
                "raw_data", """
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
                o=- 0 0 IN IP4 20.177.116.41
                s=-
                c=IN IP4 20.177.116.41
                t=0 0
                m=audio 36046 RTP/AVP 8
                b=AS:80
                a=rtpmap:8 PCMA/8000
                a=ptime:20
                a=maxptime:20
                """.trimIndent()
            )
        })

        val EVENT_4 = Event(System.currentTimeMillis(), HOST_3.name, "10.177.116.41", "RTPR", null, Document().apply {
            put("src", HOST_3.name)
            put("dst", "10.177.116.41")
       })

        val EVENT_5 = Event(System.currentTimeMillis(), HOST_1.name, "10.20.30.40", "SIP", "ZDg3ZGU1ZTA1YjZkMThlNzEzOTA0Y2JkZmQ0YWU2ODU.:z9hG4bK-d8754z-240d73239a6da57b-1---d8754z-:143", Document().apply {
            put("type", "SIP")
            put("src", HOST_1.name)
            put("dst", "10.20.30.40")

            put(
                "raw_data", """
                        REGISTER sip:192.168.10.5:5060 SIP/2.0
                        Via: SIP/2.0/UDP 192.168.10.123:55399;branch=z9hG4bK-d8754z-240d73239a6da57b-1---d8754z-;rport
                        Max-Forwards: 70
                        Contact: <sip:1010@192.168.10.123:55399;rinstance=13bf343a521442b5>
                        To: "1010"<sip:1010@192.168.10.5:5060>
                        From: "1010"<sip:1010@192.168.10.5:5060>;tag=bd285f07
                        Call-ID: ZDg3ZGU1ZTA1YjZkMThlNzEzOTA0Y2JkZmQ0YWU2ODU.
                        CSeq: 143 REGISTER
                        Expires: 120
                        Allow: INVITE, ACK, CANCEL, OPTIONS, BYE, REGISTER, SUBSCRIBE, NOTIFY, REFER, INFO, MESSAGE
                        Supported: replaces
                        User-Agent: 3CXPhone 6.0.26523.0
                        Authorization: Digest username="1010",realm="asterisk",nonce="1589932693/afdc97bf8bb891c6c3c8072baeb2d3d6",uri="sip:192.168.10.5:5060",response="1337bca977875e158b7e1b96094521ee",cnonce="557bd23e12dc3db950db3c7e77aa91ad",nc=00000002,qop=auth,algorithm=md5,opaque="5b7b35877f215628"
                        Content-Length: 0
                        """.trimIndent()
            )
        })

        val EVENT_6 = Event(System.currentTimeMillis(), HOST_1.name, "10.20.30.40", "SIP", "2dnuu30ktosoky1uad3nzzk3nkk3nzz3-wdsrwt7@UAC-e-e:z9hG4bKmqffet30b03pp5mv5jj0.1:1", Document().apply {
            put("type", "SIP")
            put("src", HOST_1.name)
            put("dst", "10.20.30.40")

            put(
                "raw_data", """
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
                a=rtcp:59282 IN IP4
                a=sendrecv
                a=ptime:20
                a=candidate:2342127660 1 udp 2122260223 172.18.32.1 61818 typ host generation 0 network-id 1
                a=candidate:1621512748 1 udp 2122194687 10.242.2.7 61819 typ host generation 0 network-id 2
                a=candidate:1897852119 1 udp 2122129151 192.168.1.28 61820 typ host generation 0 network-id 3
                a=candidate:3306812636 1 tcp 1518280447 172.18.32.1 9 typ host tcptype active generation 0 network-id 1
                a=candidate:774221532 1 tcp 1518214911 10.242.2.7 9 typ host tcptype active generation 0 network-id 2
                a=candidate:1067257895 1 tcp 1518149375 192.168.1.28 9 typ host tcptype active generation 0 network-id 3
                a=candidate:2265879811 1 udp 1685921535 8.8.8.8 41999 typ srflx raddr 192.168.1.28 rport 61820 generation 0 network-id 3 
                """.trimIndent()
            )
        })
    }

    @MockBean
    private lateinit var hostService: HostService

    @Autowired
    private lateinit var participantService: ParticipantService

    @BeforeEach
    fun init() {
        given(hostService.findByNameIgnoreCase(HOST_1.name)).willReturn(HOST_1)
        given(hostService.findByNameIgnoreCase(HOST_2.name)).willReturn(HOST_2)
        given(hostService.findByAddr("10.242.2.7")).willReturn(HOST_3)
    }

    @Test
    fun `Verify participants from SDP`() {
        val participants = participantService.collectParticipants(listOf(EVENT_1, EVENT_2, EVENT_3, EVENT_4))

        assertEquals(6, participants.size)
        // Media from candidate matched with IP from RTPR event
        assertEquals(HOST_3.name, participants[0].name)
        // SIP Source from `EVENT_1`
        assertEquals(HOST_1.name, participants[1].name)
        // SIP Destination from `EVENT_1`
        assertEquals("10.20.30.40", participants[2].name)
        // Media address from SDP from `EVENT_2`
        assertEquals("10.177.116.41", participants[3].name)

        // Only SIP addresses from `EVENT_3` (No media)
        assertEquals("20.20.30.40", participants[4].name)
        assertEquals(HOST_2.name, participants[5].name)
    }

    @Test
    fun `Verify participants from REGISTER`() {
        val participants = participantService.collectParticipants(listOf(EVENT_5))

        assertEquals(2, participants.size)
        // SIP Source from `EVENT_5`
        assertEquals(HOST_1.name, participants[0].name)
        // SIP Destination from `EVENT_5`
        assertEquals("10.20.30.40", participants[1].name)
    }

    @Test
    fun `Verify participants from INVITE with bad SDP`() {
        val participants = participantService.collectParticipants(listOf(EVENT_6))

        assertEquals(2, participants.size)
        // SIP Source from `EVENT_6`
        assertEquals(HOST_1.name, participants[0].name)
        // SIP Destination from `EVENT_6`
        assertEquals("10.20.30.40", participants[1].name)
    }

    @AfterEach
    fun reset() {
        resetMock(hostService)
    }
}