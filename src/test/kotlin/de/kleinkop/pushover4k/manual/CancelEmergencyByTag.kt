package de.kleinkop.pushover4k.manual

import de.kleinkop.pushover4k.client.Message
import de.kleinkop.pushover4k.client.Priority
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class CancelEmergencyByTag {
    companion object : ManualPushoverTest() {
        @JvmStatic
        fun main(vararg arg: String) {
            // send emergency message
            val response = pushover().sendMessage(
                Message(
                    message = "This is just a test. Don't care.",
                    title = "Test ony",
                    priority = Priority.EMERGENCY,
                    devices = listOf(device),
                    tags = listOf("aTag", "testing", "ignore"),
                    retry = 30,
                    expire = 120,
                ),
            )
            response.status shouldBe 1
            val receipt = response.receipt.shouldNotBeNull()
            logger.info { "Receipt id is $receipt" }

            waiting(6L)

            // Fetch state:
            val emergencyState = pushover().getEmergencyState(receipt)
            logger.info { "Emergency state is $emergencyState" }
            emergencyState.status shouldBe 1
            emergencyState.expired shouldBe false
            waiting(6L)

            // Cancel emergency by tag
            val tag = "aTag"
            logger.info { "Try to cancel emergency by tag $tag " }
            val cancel = pushover().cancelEmergencyMessageByTag(tag)
            logger.info { "Cancel result: $cancel" }
            cancel.status shouldBe 1
            cancel.canceled.shouldNotBeNull() shouldBe 1

            waiting(7L)
            pushover().getEmergencyState(receipt).also {
                it.expired shouldBe true
                logger.info { it }
            }
        }
    }
}
