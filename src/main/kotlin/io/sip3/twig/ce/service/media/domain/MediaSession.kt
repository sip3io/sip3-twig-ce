package io.sip3.twig.ce.service.media.domain

class MediaSession(blockCount: Int) : MediaStatistic() {

    var createdAt: Long = Long.MAX_VALUE
    var terminatedAt: Long = Long.MAX_VALUE

    val blocks = ArrayList<MediaStatistic>(blockCount)
}