package de.kleinkop.pushover4k.client.http

import de.kleinkop.pushover4k.client.ApplicationUsage
import de.kleinkop.pushover4k.client.Message
import de.kleinkop.pushover4k.client.PushoverClient
import de.kleinkop.pushover4k.client.PushoverResponse
import de.kleinkop.pushover4k.client.ReceiptResponse
import de.kleinkop.pushover4k.client.SoundResponse
import de.kleinkop.pushover4k.client.nullable
import de.kleinkop.pushover4k.client.toLocalDateTimeOrNull
import de.kleinkop.pushover4k.client.toLocalDateTimeUTC
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.decorators.Decorators
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.ZoneOffset
import java.util.UUID

class PushoverHttpClient(
    private val appToken: String,
    private val userToken: String? = null,
    apiHost: String = "https://api.pushover.net",
    retryAttempts: Int = RETRY_ATTEMPTS,
    retryInterval: Long = DEFAULT_RETRY_INTERVAL,
    httpTimeout: Long = HTTP_TIMEOUT_IN_SECONDS,
    private val registry: MeterRegistry? = null,
    customHttpClient: HttpClient? = null,
) : PushoverClient {
    private val url = "$apiHost/1/messages.json"
    private val soundsUrl = "$apiHost/1/sounds.json"
    private val receiptUrl = "$apiHost/1/receipts/"
    private val cancelUrl = "$apiHost/1/receipts/{RECEIPT_ID}/cancel.json"
    private val cancelByTagUrl = "$apiHost/1/receipts/cancel_by_tag"

    private val json = Json { ignoreUnknownKeys = true }

    private val retry = Retry.of(
        "retry-pushover",
        RetryConfig.custom<RetryConfig>()
            .maxAttempts(retryAttempts)
            .intervalFunction(IntervalFunction.of(retryInterval))
            .build(),
    )

    private val httpClient: HttpClient = customHttpClient ?: HttpClient
        .newBuilder()
        .connectTimeout(Duration.ofSeconds(httpTimeout))
        .build()

    private fun httpRequest(request: HttpRequest): HttpResponse<String> {
        val supplier: () -> HttpResponse<String> = {
            registry?.counter("http.client.request.count")?.increment()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() >= 500) {
                throw RuntimeException("Http call failed with status code ${response.statusCode()}")
            }
            response
        }
        return Decorators.ofSupplier(supplier)
            .withRetry(retry)
            .withFallback { throwable: Throwable ->
                throw RuntimeException("Call to Pushover API failed", throwable)
            }
            .decorate()
            .get()
    }

    private fun defaultRequest(url: String): HttpRequest.Builder = HttpRequest.newBuilder()
        .uri(URI(url))
        .timeout(Duration.ofSeconds(15))
        .version(HttpClient.Version.HTTP_1_1)

    override fun sendMessage(msg: Message): PushoverResponse {
        val bodyData = HttpClientMultipartFormBody()
            .plus("token", appToken)
            .plus("user", requireNotNull(userToken))
            .plus("message", msg.message)
            .plus("priority", msg.priority.value.toString())
            .plusIfSet("title", msg.title)
            .plusIfSet("url", msg.url)
            .plusIfSet("url_title", msg.urlTitle)
            .plusIfSet("html", msg.html, "1")
            .plusIfSet("monospace", msg.monospace, "1")
            .plusIfSet("sound", msg.sound)
            .plusIfSet(
                "timestamp",
                msg.timestamp != null,
                msg.timestamp?.toEpochSecond(ZoneOffset.UTC)?.toString() ?: "",
            )
            .plusIfSet("device", msg.devices)
            .plusIfSet("retry", msg.retry)
            .plusIfSet("expire", msg.expire)
            .plusIfSet("tags", msg.tags)
            .plusImageIfSet("attachment", msg.image)
            .plusIfSet("ttl", msg.ttl)
            .build(UUID.randomUUID().toString())

        val request = defaultRequest(url)
            .header("Content-Type", bodyData.first)
            .POST(
                HttpRequest.BodyPublishers.ofByteArrays(bodyData.second),
            )
            .build()

        val response = httpRequest(request)
        return json
            .decodeFromString<RawPushoverResponse>(response.body())
            .toDomain(response.headers())
    }

    override fun getSounds(): SoundResponse {
        val request = defaultRequest("$soundsUrl?token=$appToken")
            .GET()
            .build()

        val response = httpRequest(request)
        return json.decodeFromString<RawSoundResponse>(response.body()).toDomain()
    }

    override fun getEmergencyState(receiptId: String): ReceiptResponse {
        val request = defaultRequest("$receiptUrl$receiptId.json?token=$appToken")
            .GET()
            .build()

        val response = httpRequest(request)
        println(response.body())
        return json.decodeFromString<RawReceiptResponse>(response.body()).toDomain()
    }

    override fun cancelEmergencyMessage(receiptId: String): PushoverResponse {
        val request = defaultRequest(cancelUrl.replace("{RECEIPT_ID}", receiptId))
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    BodyToken(appToken).toString(),
                ),
            )
            .build()

        val response = httpRequest(request)
        return json.decodeFromString<RawPushoverResponse>(response.body()).toDomain(response.headers())
    }

    override fun cancelEmergencyMessageByTag(tag: String): PushoverResponse {
        val request = defaultRequest("$cancelByTagUrl/$tag.json")
            .timeout(Duration.ofSeconds(15))
            .version(HttpClient.Version.HTTP_2)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    BodyToken(appToken).toString(),
                ),
            )
            .build()

        val response = httpRequest(request)
        return json.decodeFromString<RawPushoverResponse>(response.body()).toDomain(response.headers())
    }

    companion object {
        // default value for number of retries
        private const val RETRY_ATTEMPTS = 5

        // Minimum retry interval allowed by Pushover
        private const val DEFAULT_RETRY_INTERVAL = 5000L

        // Time-out for http client
        private const val HTTP_TIMEOUT_IN_SECONDS = 30L
    }
}

@Serializable
data class BodyToken(
    val token: String,
) {
    override fun toString(): String = "token=$token"
}

@Serializable
data class RawReceiptResponse(
    val status: Int,
    val request: String,
    @SerialName("last_delivered_at") val lastDeliveredAt: Long,
    @SerialName("expires_at") val expiresAt: Long,
    val acknowledged: Int,
    @SerialName("acknowledged_at") val acknowledgedAt: Long,
    @SerialName("acknowledged_by") val acknowledgedBy: String,
    @SerialName("acknowledged_by_device") val acknowledgedByDevice: String,
    val expired: Int,
    @SerialName("called_back") val calledBack: Int,
    @SerialName("called_back_at") val calledBackAt: Long,
) {
    fun toDomain(): ReceiptResponse =
        ReceiptResponse(
            status,
            request,
            lastDeliveredAt = lastDeliveredAt.toLocalDateTimeUTC(),
            expiresAt = expiresAt.toLocalDateTimeUTC(),
            acknowledged = acknowledged == 1,
            acknowledgedAt = acknowledgedAt.toLocalDateTimeOrNull(),
            acknowledgedBy = acknowledgedBy.nullable(),
            acknowledgedByDevice = acknowledgedByDevice.nullable(),
            expired = expired == 1,
            calledBack = calledBack == 1,
            calledBackAt = calledBackAt.toLocalDateTimeOrNull(),
        )
}

@Serializable
data class RawPushoverResponse(
    val status: Int,
    val request: String,
    val user: String? = null,
    val errors: List<String>? = null,
    val receipt: String? = null,
    val canceled: Int? = null,
) {
    fun toDomain(headers: HttpHeaders): PushoverResponse = PushoverResponse(
        status,
        request,
        user,
        errors,
        receipt,
        canceled,
        extractAppliationUsage(headers),
    )

    private fun extractAppliationUsage(headers: HttpHeaders): ApplicationUsage? {
        val validatedParams = mutableListOf(
            "X-Limit-App-Limit",
            "X-Limit-App-Remaining",
            "X-Limit-App-Reset",
        ).mapNotNull { headers.firstValue(it).orElse(null) }
            .mapIndexedNotNull { idx, value ->
                when (idx) {
                    in 0..1 -> value.toLongOrNull()?.toString()
                    else -> value
                }
            }

        return if (validatedParams.size == 3) {
            ApplicationUsage(
                validatedParams[0].toInt(),
                validatedParams[1].toInt(),
                validatedParams[2].toLong().toLocalDateTimeUTC(),
            )
        } else {
            null
        }
    }
}

@Serializable
data class RawSoundResponse(
    val status: Int,
    val request: String,
    val sounds: Map<String, String>? = null,
    val errors: List<String>? = null,
    val token: String? = null,
) {
    fun toDomain(): SoundResponse = SoundResponse(
        status,
        request,
        sounds,
        errors,
        token,
    )
}
