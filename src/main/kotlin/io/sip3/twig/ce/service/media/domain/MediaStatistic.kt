package io.sip3.twig.ce.service.media.domain

import com.fasterxml.jackson.annotation.JsonProperty

open class MediaStatistic {

    var duration = 0
    var mos: Double = 0.0

    @get:JsonProperty("r_factor")
    var rFactor: Double = 0.0

    val packets = Packets()
    val jitter = Jitter()

    class Packets {

        var expected: Int = 0
        var lost: Int = 0
        var received: Int = 0
        var rejected: Int = 0
    }

    class Jitter {

        var min: Double = 0.0
        var max: Double = 0.0
        var avg: Double = 0.0
    }
}