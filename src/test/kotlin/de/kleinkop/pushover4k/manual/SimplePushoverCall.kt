package de.kleinkop.pushover4k.manual

import de.kleinkop.pushover4k.client.Message
import de.kleinkop.pushover4k.client.Priority
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

class SimplePushoverCall {
    companion object : ManualPushoverCall() {
        @JvmStatic
        fun main(vararg arg: String) {
            // check sound magic is available
            pushover().getSounds().sounds.shouldNotBeNull()
                .onEach { (k, v) -> logger.info { "$k: $v" } }
                .shouldContainKey(sound)

            // Send message with image and sound
            val file = file("/image.png")

            file.exists() shouldBe true

            val pushoverResponse = pushover()
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
                        timestamp = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                        image = file,
                    ),
                )
            logger.info { pushoverResponse }
        }
    }
}
