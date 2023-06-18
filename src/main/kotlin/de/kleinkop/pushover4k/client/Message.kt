package de.kleinkop.pushover4k.client

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDateTime

data class Message(
    val message: String,
    val title: String? = null,
    val priority: Priority = Priority.NORMAL,
    val url: String? = null,
    val urlTitle: String? = null,
    val devices: List<String> = emptyList(),
    val timestamp: LocalDateTime? = null,
    val html: Boolean = true,
    val sound: String? = null,
    val image: File? = null,
    val monospace: Boolean = false,
    val retry: Int? = null,
    val expire: Int? = null,
    val tags: List<String> = emptyList(),
) {
    init {
        if (priority == Priority.EMERGENCY) {
            requireNotNull(retry) { "Retry value required for emergency messages" }
            requireNotNull(expire) { "Expiration value required for emergency messages" }
        }

        if (html && monospace) {
            throw IllegalArgumentException("Don't use options html and monospace together")
        }
    }
}

@Serializable
data class PushoverResponse(
    val status: Int,
    val request: String,
    val user: String? = null,
    val errors: List<String>? = null,
    val receipt: String? = null,
)

@Serializable
data class SoundResponse(
    val status: Int,
    val request: String,
    val sounds: Map<String, String>? = null,
    val errors: List<String>? = null,
    val token: String? = null,
)

@Serializable
data class ReceiptResponse(
    val status: Int,
    val request: String,
    @Contextual val lastDeliveredAt: LocalDateTime,
    @Contextual val expiresAt: LocalDateTime,
    val acknowledged: Boolean = false,
    @Contextual val acknowledgedAt: LocalDateTime? = null,
    val acknowledgedBy: String? = null,
    val acknowledgedByDevice: String? = null,
    val expired: Boolean = false,
    val calledBack: Boolean = false,
    @Contextual val calledBackAt: LocalDateTime? = null,
)

@Suppress("PropertyName")
@Serializable
data class RawReceiptResponse(
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

@Suppress("unused")
@Serializable
enum class Priority(val value: Int) {
    LOWEST(-2),
    LOW(-1),
    NORMAL(0),
    HIGH(1),
    EMERGENCY(2),
}
