package de.kleinkop.pushover4k.manual

import de.kleinkop.pushover4k.client.Message

class FailedPushoverCalls {

    companion object : ManualPushoverCall() {
        @JvmStatic
        fun main(vararg args: String) {
            val response = failingPushover().sendMessage(
                Message("Whatever"),
            )
            logger.info { response }
        }
    }
}
