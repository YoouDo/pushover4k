package de.kleinkop.pushover4k.client

import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.decorators.Decorators
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.ZoneOffset
import java.util.UUID

class PushoverHttpClient(
    private val appToken: String,
    private val userToken: String? = null,
    baseRetryInterval: Long = 500L,
    backoffMultiplier: Double = 2.0,
    apiHost: String = "https://api.pushover.net",
    private val registry: MeterRegistry? = null,
) : PushoverClient {
    private val url = "$apiHost/1/messages.json"
    private val soundsUrl = "$apiHost/1/sounds.json"
    private val receiptUrl = "$apiHost/1/receipts/"
    private val cancelUrl = "$apiHost/1/receipts/{RECEIPT_ID}/cancel.json"
    private val cancelByTagUrl = "$apiHost/1/receipts/cancel_by_tag"

    private val retry = Retry.of(
        "retry-pushover",
        RetryConfig.custom<RetryConfig>()
            .maxAttempts(5)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(baseRetryInterval, backoffMultiplier))
            .build()
    )

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    private val successfulStatusCodes = listOf(200, 202)

    private fun httpRequest(request: HttpRequest): HttpResponse<String> {
        val supplier: () -> HttpResponse<String> = {
            registry?.counter("http.client.request.count")?.increment()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in successfulStatusCodes) {
                throw RuntimeException("Http call failed with status code ${response.statusCode()}")
            }
            response
        }
        return Decorators.ofSupplier(supplier)
            .withRetry(retry)
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
                msg.timestamp?.toEpochSecond(ZoneOffset.UTC)?.toString() ?: ""
            )
            .plusIfSet("device", msg.devices)
            .plusIfSet("retry", msg.retry.toString())
            .plusIfSet("expire", msg.expire.toString())
            .plusIfSet("tags", msg.tags)
            .plusImageIfSet("attachment", msg.image)
            .build(UUID.randomUUID().toString())

        val request = defaultRequest(url)
            .header("Content-Type", bodyData.first)
            .POST(
                HttpRequest.BodyPublishers.ofByteArrays(bodyData.second)
            )
            .build()

        val response = httpRequest(request)
        return Json.decodeFromString<PushoverResponse>(response.body())
    }

    override fun getSounds(): SoundResponse {
        val request = defaultRequest("$soundsUrl?token=$appToken")
            .GET()
            .build()

        val response = httpRequest(request)
        return Json.decodeFromString<SoundResponse>(response.body())
    }

    override fun getEmergencyState(receiptId: String): ReceiptResponse {
        val request = defaultRequest("$receiptUrl$receiptId.json?token=$appToken")
            .GET()
            .build()

        val response = httpRequest(request)
        return Json.decodeFromString<RawReceiptResponse>(response.body()).toDomain()
    }

    override fun cancelEmergencyMessage(receiptId: String): PushoverResponse {
        val request = defaultRequest(cancelUrl.replace("{RECEIPT_ID}", receiptId))
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{token: \"$appToken\"}"
                )
            )
            .build()

        val response = httpRequest(request)
        return Json.decodeFromString<PushoverResponse>(response.body())
    }

    override fun cancelEmergencyMessageByTag(tag: String): PushoverResponse {
        val request = defaultRequest("$cancelByTagUrl/$tag.json")
            .timeout(Duration.ofSeconds(15))
            .version(HttpClient.Version.HTTP_2)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{token: \"$appToken\"}"
                )
            )
            .build()

        val response = httpRequest(request)
        return Json.decodeFromString<PushoverResponse>(response.body())
    }
}

/**
 * Create multipart form-data based on a publication on "Ralph's Blog"
 */
class HttpClientMultipartFormBody {
    private val content: MutableMap<String, Any> = mutableMapOf()

    fun plus(name: String, value: String): HttpClientMultipartFormBody {
        content[name] = value
        return this
    }

    fun plusIfSet(name: String, isSet: Boolean, value: String): HttpClientMultipartFormBody =
        if (isSet) {
            plus(name, value)
        } else {
            this
        }

    fun plusIfSet(name: String, value: String?): HttpClientMultipartFormBody =
        value
            ?.let { this.plus(name, it) }
            ?: this

    fun plusIfSet(name: String, values: List<String>): HttpClientMultipartFormBody =
        if (values.isNotEmpty()) {
            plus(name, values.joinToString(separator = ","))
        } else {
            this
        }

    fun plusImageIfSet(name: String, file: File?): HttpClientMultipartFormBody {
        file?.also {
            content[name] = it
        }
        return this
    }

    /**
     * Build multipart content
     * @return Pair of value of header "Content-Type" and lists of ByteArrays
     */
    fun build(boundary: String): Pair<String, List<ByteArray>> {
        val byteArrays = ArrayList<ByteArray>()
        val separator = "--$boundary\r\nContent-Disposition: form-data; name=".toByteArray(StandardCharsets.UTF_8)

        val data: Map<String, Any> = content.toMap()
        for (entry in data.entries) {
            byteArrays.add(separator)
            when (entry.value) {
                is File -> {
                    val file = entry.value as File
                    val path = Path.of(file.toURI())
                    val mimeType = Files.probeContentType(path)
                    byteArrays.add(
                        "\"${entry.key}\"; filename=\"${path.fileName}\"\r\nContent-Type: $mimeType\r\n\r\n".toByteArray(
                            StandardCharsets.UTF_8
                        )
                    )
                    byteArrays.add(Files.readAllBytes(path))
                    byteArrays.add("\r\n".toByteArray(StandardCharsets.UTF_8))
                }

                else -> byteArrays.add("\"${entry.key}\"\r\n\r\n${entry.value}\r\n".toByteArray(StandardCharsets.UTF_8))
            }
        }
        byteArrays.add("--$boundary--".toByteArray(StandardCharsets.UTF_8))
        return "multipart/form-data;boundary=$boundary" to byteArrays
    }
}
