package de.kleinkop.pushover4k.client

import io.micrometer.core.instrument.MeterRegistry
import org.http4k.client.ApacheClient
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.MultipartFormBody
import org.http4k.core.Request
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.MicrometerMetrics
import org.http4k.format.Jackson.auto
import org.http4k.lens.MultipartFormFile
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.time.ZoneOffset

class PushoverRestClient(
    private val appToken: String,
    private val userToken: String? = null,
    apiHost: String = "https://api.pushover.net",
    registry: MeterRegistry? = null
) : PushoverClient {
    private val url = "$apiHost/1/messages.json"
    private val soundsUrl = "$apiHost/1/sounds.json"
    private val receiptUrl = "$apiHost/1/receipts/"
    private val cancelUrl = "$apiHost/1/receipts/{RECEIPT_ID}/cancel.json"
    private val cancelByTagUrl = "$apiHost/1/receipts/cancel_by_tag"

    private val responseLens = Body.auto<PushoverResponse>().toLens()
    private val soundsResponseLens = Body.auto<SoundResponse>().toLens()
    private val receiptResponseLens = Body.auto<RawReceiptResponse>().toLens()
    private val tokenBodyLens = Body.auto<TokenBody>().toLens()

    private val client = if (registry != null) {
        ClientFilters.MicrometerMetrics.RequestCounter(registry)
            .then(ClientFilters.MicrometerMetrics.RequestTimer(registry))
            .then(ApacheClient())
    } else {
        ApacheClient()
    }

    override fun sendMessage(msg: Message): PushoverResponse {
        val body = MultipartFormBody()
            .plus("token" to appToken)
            .plus("user" to requireNotNull(userToken))
            .plus("message" to msg.message)
            .plus("priority" to msg.priority.value.toString())
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

        val request = Request(Method.POST, url)
            .header("content-type", "multipart/form-data; boundary=${body.boundary}")
            .body(body)

        val response = client(request)

        return responseLens.extract(response)
    }

    override fun getSounds(): SoundResponse {
        val request = Request(Method.GET, soundsUrl)
            .query("token", appToken)
        return soundsResponseLens.extract(client(request))
    }

    override fun getEmergencyState(receiptId: String): ReceiptResponse {
        val request = Request(Method.GET, "$receiptUrl$receiptId.json")
            .query("token", appToken)
        return receiptResponseLens
            .extract(client(request))
            .toDomain()
    }

    override fun cancelEmergencyMessage(receiptId: String): PushoverResponse {
        val request = tokenBodyLens(
            TokenBody(appToken),
            Request(Method.POST, cancelUrl.replace("{RECEIPT_ID}", receiptId))
        )
        return responseLens.extract(client(request))
    }

    override fun cancelEmergencyMessageByTag(tag: String): PushoverResponse {
        val request = tokenBodyLens(TokenBody(appToken), Request(Method.POST, "$cancelByTagUrl/$tag.json"))
        return responseLens.extract(client(request))
    }

    data class TokenBody(
        val token: String
    )
}

@Suppress("PropertyName")
internal data class RawReceiptResponse(
    val status: Int,
    val request: String,
    val last_delivered_at: Long,
    val expires_at: Long,
    val acknowledged: Int,
    val acknowledged_at: Long,
    val acknowledged_by: String,
    val acknowledged_by_device: String,
    val expired: Int,
    val called_back: Int,
    val called_back_at: Long,
) {
    fun toDomain(): ReceiptResponse =
        ReceiptResponse(
            status,
            request,
            lastDeliveredAt = last_delivered_at.toLocalDateTimeUTC(),
            expiresAt = expires_at.toLocalDateTimeUTC(),
            acknowledged = acknowledged == 1,
            acknowledgedAt = acknowledged_at.toLocalDateTimeOrNull(),
            acknowledgedBy = acknowledged_by.nullable(),
            acknowledgedByDevice = acknowledged_by_device.nullable(),
            expired = expired == 1,
            calledBack = called_back == 1,
            calledBackAt = called_back_at.toLocalDateTimeOrNull()
        )
}

private fun MultipartFormBody.plusIfSet(name: String, isSet: Boolean, value: String): MultipartFormBody =
    if (isSet) {
        this.plus(name to value)
    } else {
        this
    }

private fun MultipartFormBody.plusIfSet(name: String, value: String?): MultipartFormBody =
    value
        ?.let { this.plus(name to it) }
        ?: this

private fun MultipartFormBody.plusIfSet(name: String, values: List<String>): MultipartFormBody =
    if (values.isNotEmpty()) {
        plus(name to values.joinToString(separator = ","))
    } else {
        this
    }

private fun MultipartFormBody.plusImageIfSet(name: String, file: File?): MultipartFormBody =
    file?.let {
        plus(
            name to MultipartFormFile(
                it.name,
                ContentType(Files.probeContentType(file.toPath())),
                FileInputStream(it)
            )
        )
    }
        ?: this
