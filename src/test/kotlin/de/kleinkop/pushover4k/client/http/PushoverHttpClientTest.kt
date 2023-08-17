package de.kleinkop.pushover4k.client.http

import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class PushoverHttpClientTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Can serialize and deserialize raw response messages`() {
        val rawPushoverResponse = RawPushoverResponse(
            1,
            "abc",
            "me",
            listOf("error"),
            "receipt",
            0,
        )

        val expected = """
            {
                "status": ${rawPushoverResponse.status},
                "request": "${rawPushoverResponse.request}",
                "user": "${rawPushoverResponse.user}",
                "errors": ["${rawPushoverResponse.errors!![0]}"],
                "receipt": "${rawPushoverResponse.receipt}",
                "canceled": ${rawPushoverResponse.canceled}                
            }
        """.trimIndent().replace(Regex("[\\n ]"), "")

        json.encodeToString(rawPushoverResponse) shouldBe expected

        json.decodeFromString<RawPushoverResponse>(expected) shouldBe rawPushoverResponse
    }

    @Test
    fun `Can serialize and deserialize raw sound messages`() {
        val rawSoundResponse = RawSoundResponse(
            1,
            "request",
            mapOf("a" to "b"),
            listOf("error"),
            "token",
        )

        val expected = """
            {
                "status" : ${rawSoundResponse.status},
                "request": "${rawSoundResponse.request}",
                "sounds": {"a":"b"},
                "errors": ["${rawSoundResponse.errors!![0]}"],
                "token": "${rawSoundResponse.token}"
            }
        """.trimIndent().replace(Regex("[\\n ]"), "")

        json.encodeToString(rawSoundResponse) shouldBe expected

        json.decodeFromString<RawSoundResponse>(expected) shouldBe rawSoundResponse
    }

    @Test
    fun `Can serialize and deserialize raw receipt messages`() {
        val rawReceiptMessage = RawReceiptResponse(
            1,
            "abc",
            1234567890L,
            2234567890L,
            1,
            1234567890L,
            "me",
            "mobile",
            1,
            1,
            1234567890L,
        )

        val expected = """
            {
               "status": 1,
                "request": "${rawReceiptMessage.request}",
                "last_delivered_at": ${rawReceiptMessage.last_delivered_at},
                "expires_at": ${rawReceiptMessage.expires_at},
                "acknowledged": ${rawReceiptMessage.acknowledged},
                "acknowledged_at": ${rawReceiptMessage.acknowledged_at},
                "acknowledged_by": "${rawReceiptMessage.acknowledged_by}",
                "acknowledged_by_device": "${rawReceiptMessage.acknowledged_by_device}",
                "expired": ${rawReceiptMessage.expired},
                "called_back": ${rawReceiptMessage.called_back},
                "called_back_at": ${rawReceiptMessage.called_back_at}               
            }
        """.trimIndent().replace(Regex("[\\n ]"), "")

        json.encodeToString(rawReceiptMessage) shouldBe expected

        json.decodeFromString<RawReceiptResponse>(expected) shouldBe rawReceiptMessage
    }
}
