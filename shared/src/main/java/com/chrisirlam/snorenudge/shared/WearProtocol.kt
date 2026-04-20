package com.chrisirlam.snorenudge.shared

/** Paths and payload values shared by phone and watch for low-latency message commands. */
object WearProtocol {
    object Paths {
        const val PREFIX = "/snore"
        const val VIBRATE = "/snore/vibrate"
        const val STOP_VIBRATE = "/snore/stop_vibrate"
        const val TEST_VIBRATE = "/snore/test_vibrate"
        const val STATUS = "/snore/status"
    }

    object Payload {
        const val STRONG = "strong"
        const val MEDIUM = "medium"
    }
}
