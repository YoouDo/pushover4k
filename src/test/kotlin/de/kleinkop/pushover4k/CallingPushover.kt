package de.kleinkop.pushover4k

import de.kleinkop.pushover4k.client.Message
import de.kleinkop.pushover4k.client.Priority
import de.kleinkop.pushover4k.client.PushoverRestClient
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import mu.KotlinLogging
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Testing code against the real Pushover API
 */
class CallingPushover {
    companion object {
        private val logger = KotlinLogging.logger { }

        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        @JvmStatic
        fun main(vararg arg: String) {
            val device = System.getenv("PUSHOVER_DEVICE").shouldNotBeNull()

            val pushoverClient = PushoverRestClient(
                System.getenv("PUSHOVER_TOKEN"),
                System.getenv("PUSHOVER_USER")
            )

            // check sound magic is available
            pushoverClient.getSounds().sounds.shouldNotBeNull()
                .onEach { (k, v) -> logger.info { "$k: $v" } }
                .shouldContainKey("magic")

            waiting(1L)

            // Send message with image and sound
            val file = File(CallingPushover::class.java.getResource("/image.png").file)

            val pushoverResponse = pushoverClient
                .sendMessage(
                    Message(
                        "Testnachricht: äöüß",
                        devices = listOf(device),
                        title = "A title (äöüß)",
                        html = true,
                        sound = "magic",
                        url = "https://www.example.com",
                        urlTitle = "This is an example URL.",
                        priority = Priority.NORMAL,
                        timestamp = LocalDateTime.parse("2022-12-31T14:16:00"),
                        image = file,
                    )
                )
            logger.info { pushoverResponse }

            waiting(2L)

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

            waiting(2L)

            // Fetch state:
            val emergencyState = pushoverClient.getEmergencyState(receipt)
            logger.info { emergencyState }
            emergencyState.status shouldBe 1
            emergencyState.expired shouldBe false
            waiting(2L)

            // Cancel emergency by tag
            val cancel = pushoverClient.cancelEmergencyMessageByTag("aTag")
            logger.info { "Cancel result: $cancel" }
            cancel.status shouldBe 1

            pushoverClient.getEmergencyState(receipt).also {
                it.expired shouldBe true
                logger.info { it }
            }
        }

        private fun waiting(timeInSeconds: Long) = TimeUnit.SECONDS.sleep(timeInSeconds)
    }
}
