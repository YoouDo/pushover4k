package de.kleinkop.pushover4k

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.findAll
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.github.tomakehurst.wiremock.stubbing.Scenario
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import de.kleinkop.pushover4k.client.Message
import de.kleinkop.pushover4k.client.Priority
import de.kleinkop.pushover4k.client.PushoverClient
import de.kleinkop.pushover4k.client.http.PushoverHttpClient
import de.kleinkop.pushover4k.client.toLocalDateTimeUTC
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import mu.KotlinLogging
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

@WireMockTest
class PushoverClientTest {

    @Test
    fun `Fetch sounds`() {
        pushoverClient.getSounds().apply {
            status shouldBe 1
            request shouldBe "A"
        }

        invalidClient.getSounds().apply {
            status shouldBe 0
            sounds shouldBe null
            token shouldBe "invalid"
            errors
                .shouldNotBeNull()
                .shouldHaveSize(1)
                .first() shouldBe "application token is invalid"
            request shouldBe "error-request"
        }
    }

    @Test
    fun `Create invalid messages`() {
        // emergency msg without retry and expiration
        shouldThrow<IllegalArgumentException> {
            Message(
                message = "msg",
                priority = Priority.EMERGENCY,
            )
        }

        // emergency msg without expiration
        shouldThrow<IllegalArgumentException> {
            Message(
                message = "msg",
                priority = Priority.EMERGENCY,
                retry = 30,
            )
        }

        // emergency msg without retry
        shouldThrow<IllegalArgumentException> {
            Message(
                message = "msg",
                priority = Priority.EMERGENCY,
                expire = 60,
            )
        }

        // Message with options html and monospace
        shouldThrow<IllegalArgumentException> {
            Message(
                message = "msg",
                html = true,
                monospace = true
            )
        }
    }

    @Suppress("UastIncorrectHttpHeaderInspection")
    @Test
    fun `Send a simple message`() {
        stubFor(
            post("/1/messages.json")
                .willReturn(
                    okJson(
                        """
                            {
                                "status": 1,
                                "request": "B"
                            }                            
                        """.trimIndent()
                    )
                        .withHeader("X-Limit-App-Limit", "10000")
                        .withHeader("X-Limit-App-Remaining", "1234")
                        .withHeader("X-Limit-App-Reset", "1393653600")
                )
        )

        pushoverClient.sendMessage(
            Message(
                message = "Testing",
            )
        ).apply {
            status shouldBe 1
            request shouldBe "B"
            applicationUsage.shouldNotBeNull().also {
                it.limit shouldBe 10_000
                it.remaining shouldBe 1234
                it.reset shouldBe 1393653600L.toLocalDateTimeUTC()
            }
        }

        findAll(postRequestedFor(urlPathEqualTo("/1/messages.json")))
            .filter { it.isMultipart }
            .shouldHaveSize(1)
            .first()
            .apply {
                partAsString("token") shouldBe "app-token"
                partAsString("user") shouldBe "user-token"
                partAsString("message") shouldBe "Testing"
            }
    }

    @Test
    fun `Send an emergency message`() {
        stubsForEmergencyMessage()

        pushoverClient.sendMessage(
            Message(
                message = "Testing emergency",
                priority = Priority.EMERGENCY,
                retry = 100,
                expire = 200,
                tags = listOf("TAG"),
                timestamp = LocalDateTime.now(),
                image = File(PushoverClientTest::class.java.getResource("/image.png")!!.file),

            )
        ).apply {
            status shouldBe 1
            request shouldBe "C"
            receipt shouldBe "R"
        }

        findAll(postRequestedFor(urlPathEqualTo("/1/messages.json")))
            .filter { it.isMultipart }
            .shouldHaveSize(1)
            .first()
            .apply {
                partAsString("retry") shouldBe "100"
                partAsString("expire") shouldBe "200"
                partAsString("priority") shouldBe "2"
                partAsString("tags") shouldBe "TAG"
            }

        pushoverClient.getEmergencyState("R").apply {
            status shouldBe 1
            request shouldBe "D"
            lastDeliveredAt shouldBe deliveredAt
            expiresAt shouldBe expiresAt
            acknowledged shouldBe false
            acknowledgedBy shouldBe null
            acknowledgedByDevice shouldBe null
            calledBackAt shouldBe null
        }

        pushoverClient.getEmergencyState("R1").apply {
            status shouldBe 1
            request shouldBe "D"
            lastDeliveredAt shouldBe deliveredAt
            expiresAt shouldBe expiresAt
            acknowledged shouldBe true
            acknowledgedBy shouldBe "user1"
            acknowledgedByDevice shouldBe "device"
            acknowledgedAt shouldBe deliveredAt
            calledBackAt shouldBe null
        }

        pushoverClient.cancelEmergencyMessage("R").request shouldBe "E"

        pushoverClient.cancelEmergencyMessageByTag("TAG").request shouldBe "F"
    }

    @Test
    fun `Using metrics`(runtimeInfo: WireMockRuntimeInfo) {
        val registry = SimpleMeterRegistry()

        val myPushoverClient = PushoverHttpClient(
            "app-token",
            "user-token",
            apiHost = "http://localhost:${runtimeInfo.httpPort}",
            registry = registry
        )

        val iterations = 10

        (1..iterations).forEach { _ ->
            myPushoverClient.getSounds().apply { status shouldBe 1 }
        }

        registry.meters
            .first { it.id.name == "http.client.request.count" }
            .shouldNotBeNull()
            .measure()
            .first()
            .value shouldBe iterations.toDouble()

        logger.info { registry.metersAsString }
    }

    @Test
    fun `Check if five retries are executed`() {
        stubFor(
            post("/1/messages.json")
                .inScenario("Retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(
                    aResponse().withStatus(500)
                )
                .willSetStateTo("STEP-1")
        )

        for (i in 1..3) {
            stubFor(
                post("/1/messages.json")
                    .inScenario("Retry")
                    .whenScenarioStateIs("STEP-$i")
                    .willReturn(
                        aResponse().withStatus(500)
                    )
                    .willSetStateTo("STEP-${i + 1}")
            )
        }

        stubFor(
            post("/1/messages.json")
                .inScenario("Retry")
                .whenScenarioStateIs("STEP-4")
                .willReturn(
                    okJson(
                        """
                            {
                                "status": 1,
                                "request": "B"
                            }                            
                        """.trimIndent()
                    )
                )
        )

        pushoverClient.sendMessage(
            Message(message = "Testing")
        ).apply {
            status shouldBe 1
            request shouldBe "B"
        }

        findAll(postRequestedFor(urlPathEqualTo("/1/messages.json")))
            .filter { it.isMultipart }
            .shouldHaveSize(5)
            .last()
            .apply {
                partAsString("token") shouldBe "app-token"
                partAsString("user") shouldBe "user-token"
                partAsString("message") shouldBe "Testing"
            }

        verify(5, postRequestedFor(urlEqualTo("/1/messages.json")))
    }

    @Test
    fun `Check exception is thrown after five retries`() {
        stubFor(
            post("/1/messages.json")
                .inScenario("Retry2")
                .willReturn(
                    aResponse().withStatus(500)
                )
        )

        shouldThrow<RuntimeException> {
            pushoverClient.sendMessage(
                Message(message = "Testing")
            )
        }.also {
            logger.info { it }
            it.message shouldStartWith "Call to Pushover API failed"
        }
    }

    private fun stubsForEmergencyMessage() {
        stubFor(
            post("/1/messages.json")
                .willReturn(
                    okJson(
                        """
                            {
                                "receipt": "R",
                                "status": 1,
                                "request": "C"
                            }
                        """.trimIndent()
                    )
                )
        )

        stubFor(
            get("/1/receipts/R.json?token=app-token")
                .willReturn(
                    okJson(
                        """
                        {
                            "status": 1,
                            "acknowledged": 0,
                            "acknowledged_at": 0,
                            "acknowledged_by": "",
                            "acknowledged_by_device": "",
                            "last_delivered_at": ${deliveredAt.toInstant(ZoneOffset.UTC).epochSecond},
                            "expired": 1,
                            "expires_at": ${expiresAt.toInstant(ZoneOffset.UTC).epochSecond},
                            "called_back": 0,
                            "called_back_at": 0,
                            "request": "D"
                        }
                        """.trimIndent()
                    )
                )
        )

        stubFor(
            get("/1/receipts/R1.json?token=app-token")
                .willReturn(
                    okJson(
                        """
                        {
                            "status": 1,
                            "acknowledged": 1,
                            "acknowledged_at": ${deliveredAt.toInstant(ZoneOffset.UTC).epochSecond},
                            "acknowledged_by": "user1",
                            "acknowledged_by_device": "device",
                            "last_delivered_at": ${deliveredAt.toInstant(ZoneOffset.UTC).epochSecond},
                            "expired": 1,
                            "expires_at": ${expiresAt.toInstant(ZoneOffset.UTC).epochSecond},
                            "called_back": 0,
                            "called_back_at": 0,
                            "request": "D"
                        }
                        """.trimIndent()
                    )
                )
        )
    }

    private fun LoggedRequest.partAsString(name: String): String = this.parts.first { it.name == name }.body.asString()

    companion object {
        val logger = KotlinLogging.logger { }

        private lateinit var pushoverClient: PushoverClient
        private lateinit var invalidClient: PushoverClient

        val deliveredAt: LocalDateTime = LocalDateTime.parse("2023-01-07T10:00:00")
        val expiresAt: LocalDateTime = LocalDateTime.parse("2023-01-07T11:00:00")

        @JvmStatic
        @BeforeAll
        fun beforeAll(runtimeInfo: WireMockRuntimeInfo) {
            pushoverClient = PushoverHttpClient(
                "app-token",
                "user-token",
                baseRetryInterval = 10L,
                backoffMultiplier = 1.1,
                apiHost = "http://localhost:${runtimeInfo.httpPort}",
            )
            invalidClient = PushoverHttpClient(
                "invalid-token",
                "user-token",
                apiHost = "http://localhost:${runtimeInfo.httpPort}",
            )
        }
    }
}
