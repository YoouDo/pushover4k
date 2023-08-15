package de.kleinkop.pushover4k

import de.kleinkop.pushover4k.client.Message
import de.kleinkop.pushover4k.client.Priority
import de.kleinkop.pushover4k.client.http.PushoverHttpClient
import de.kleinkop.pushover4k.client.toLocalDateTimeUTC
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import mu.KotlinLogging
import java.io.File
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

class SimplePushoverCall {
    companion object {
        private val logger = KotlinLogging.logger { }

        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        @JvmStatic
        fun main(vararg arg: String) {
            val device = System.getenv("PUSHOVER_DEVICE").shouldNotBeNull()
            val sound = System.getenv("PUSHOVER_SOUND").shouldNotBeNull()

            val pushoverClient = PushoverHttpClient(
                System.getenv("PUSHOVER_TOKEN"),
                System.getenv("PUSHOVER_USER")
            )

            // check sound magic is available
            pushoverClient.getSounds().sounds.shouldNotBeNull()
                .onEach { (k, v) -> logger.info { "$k: $v" } }
                .shouldContainKey(sound)

            // Send message with image and sound
            val file = File(SimplePushoverCall::class.java.getResource("/image.png").file)

            file.exists() shouldBe true

            val pushoverResponse = pushoverClient
                .sendMessage(
                    Message(
                        "Testnachricht: äöüß \uD83E\uDD37",
                        devices = listOf(device),
                        title = "A title (äöüß)",
                        html = true,
                        sound = sound,
                        url = "https://www.example.com",
                        urlTitle = "This is an example URL.",
                        priority = Priority.NORMAL,
                        timestamp = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toLocalDateTimeUTC(),
                        image = file,
                    )
                )
            logger.info { pushoverResponse }
        }
    }
}
