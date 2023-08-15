package de.kleinkop.pushover4k

import de.kleinkop.pushover4k.client.Message
import de.kleinkop.pushover4k.client.Priority
import de.kleinkop.pushover4k.client.http.PushoverHttpClient
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import mu.KotlinLogging
import java.util.concurrent.TimeUnit

class EmergencyPushoverCall {
    companion object {
        private val logger = KotlinLogging.logger { }

        @JvmStatic
        fun main(vararg arg: String) {
            val device = System.getenv("PUSHOVER_DEVICE").shouldNotBeNull()

            val pushoverClient = PushoverHttpClient(
                System.getenv("PUSHOVER_TOKEN"),
                System.getenv("PUSHOVER_USER")
            )

            // send emergency message
            val response = pushoverClient.sendMessage(
                Message(
                    message = "This is just a test. Don't care.",
                    title = "Test ony",
                    priority = Priority.EMERGENCY,
                    devices = listOf(device),
                    tags = listOf("aTag", "testing", "ignore"),
                    retry = 30,
                    expire = 120,
                )
            )
            response.status shouldBe 1
            val receipt = response.receipt.shouldNotBeNull()
            logger.info { "Receipt id is $receipt" }

            waiting(6L)

            // Fetch state:
            val emergencyState = pushoverClient.getEmergencyState(receipt)
            logger.info { "Emergency state is $emergencyState" }
            emergencyState.status shouldBe 1
            emergencyState.expired shouldBe false
            waiting(6L)

            // Cancel emergency by receipt id
            logger.info { "Try to cancel emergency by receipt $receipt " }
            val cancel = pushoverClient.cancelEmergencyMessage(receipt)
            logger.info { "Cancel result: $cancel" }
            cancel.status shouldBe 1

            waiting(7L)
            pushoverClient.getEmergencyState(receipt).also {
                it.expired shouldBe true
                logger.info { it }
            }
        }

        private fun waiting(timeInSeconds: Long) {
            logger.info { "Waiting $timeInSeconds seconds..." }
            TimeUnit.SECONDS.sleep(timeInSeconds)
            logger.info { "... done!" }
        }
    }
}
