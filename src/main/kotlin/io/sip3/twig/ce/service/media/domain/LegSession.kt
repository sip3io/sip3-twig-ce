package io.sip3.twig.ce.service.media.domain

class LegSession {

    var createdAt: Long = Long.MAX_VALUE
    var terminatedAt: Long = Long.MAX_VALUE
    var duration: Int = 0

    lateinit var callId: String

    lateinit var srcAddr: String
    var srcPort: Int = 0
    var srcHost: String? = null
    lateinit var dstAddr: String
    var dstPort: Int = 0
    var dstHost: String? = null

    val codecs = mutableSetOf<Codec>()

    val out = mutableListOf<MediaSession>()
    val `in` = mutableListOf<MediaSession>()

    data class Codec(val name: String?, val payloadType: Int)
}