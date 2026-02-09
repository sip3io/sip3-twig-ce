/*
 * Copyright 2018-2026 SIP3.IO, Corp.
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

import gov.nist.javax.sip.message.Content
import gov.nist.javax.sip.message.SIPMessage
import gov.nist.javax.sip.message.SIPRequest
import org.restcomm.media.sdp.SessionDescription

fun SIPMessage.callId(): String? {
    return callId?.callId
}

fun SIPMessage.branchId(): String? {
    return topmostVia?.branch
}

fun SIPMessage.cseqNumber(): Long? {
    return cSeq?.seqNumber
}

fun SIPMessage.requestUri(): String? {
    return (this as? SIPRequest)?.requestLine
        ?.uri
        ?.toString()
}

fun SIPMessage.fromUri(): String? {
    return from?.address
        ?.uri
        ?.toString()
}

fun SIPMessage.toUri(): String? {
    return to?.address
        ?.uri
        ?.toString()
}

fun SIPMessage.method(): String? {
    return (this as? SIPRequest)?.requestLine?.method
}

fun SIPMessage.transactionId(): String {
    return "${callId()}:${branchId()}:${cseqNumber()}"
}

fun SIPMessage.hasSdp(): Boolean {
    contentTypeHeader?.let { contentType ->
        if (contentType.mediaSubType == "sdp") {
            return true
        } else {
            multipartMimeContent?.contents?.forEach { mimeContent ->
                if (mimeContent.matches("sdp")) {
                    return true
                }
            }
        }
    }

    return false
}

fun SIPMessage.sessionDescription(): SessionDescription? {
    if (this.contentTypeHeader?.mediaSubType == "sdp") {
        return SessionDescriptionParser.parse(this.messageContent)
    } else {
        this.multipartMimeContent?.contents?.forEach { mimeContent ->
            if (mimeContent.matches("sdp")) {
                return SessionDescriptionParser.parse(mimeContent.content.toString())
            }
        }
    }

    return null
}

fun Content.matches(proto: String): Boolean {
    return contentTypeHeader?.contentSubType?.lowercase()?.contains(proto.lowercase()) ?: false
}
