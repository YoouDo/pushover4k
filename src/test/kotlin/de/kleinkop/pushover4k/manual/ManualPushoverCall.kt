package de.kleinkop.pushover4k.manual

import de.kleinkop.pushover4k.client.Message
import de.kleinkop.pushover4k.client.http.PushoverHttpClient
import io.kotest.matchers.nulls.shouldNotBeNull
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.TimeUnit

open class ManualPushoverCall {
    val logger = KotlinLogging.logger { }

    val device = System.getenv("PUSHOVER_DEVICE").shouldNotBeNull()
    val sound = System.getenv("PUSHOVER_SOUND").shouldNotBeNull()

    private val pushoverClient = PushoverHttpClient(
        System.getenv("PUSHOVER_TOKEN"),
        System.getenv("PUSHOVER_USER"),
    )

    private val failingPushoverClient = PushoverHttpClient(
        "wrongtoken",
        "wronguser",
    )

    fun pushover() = pushoverClient

    fun failingPushover() = failingPushoverClient

    fun logResponse(msg: Message) = logger.info { pushover().sendMessage(msg) }

    fun file(filename: String): File = File(ManualPushoverCall::class.java.getResource(filename)!!.file)

    fun waiting(timeInSeconds: Long) {
        logger.info { "Waiting $timeInSeconds seconds..." }
        TimeUnit.SECONDS.sleep(timeInSeconds)
        logger.info { "... done!" }
    }
}
