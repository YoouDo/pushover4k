package de.kleinkop.pushover4k.client

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
    val ttl: Int? = null,
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

data class SoundResponse(
    val status: Int,
    val request: String,
    val sounds: Map<String, String>? = null,
    val errors: List<String>? = null,
    val token: String? = null,
)

data class ReceiptResponse(
    val status: Int,
    val request: String,
    val lastDeliveredAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val acknowledged: Boolean = false,
    val acknowledgedAt: LocalDateTime? = null,
    val acknowledgedBy: String? = null,
    val acknowledgedByDevice: String? = null,
    val expired: Boolean = false,
    val calledBack: Boolean = false,
    val calledBackAt: LocalDateTime? = null,
)

data class ApplicationUsage(
    val limit: Int,
    val remaining: Int,
    val reset: LocalDateTime,
)

@Suppress("unused")
enum class Priority(val value: Int) {
    LOWEST(-2),
    LOW(-1),
    NORMAL(0),
    HIGH(1),
    EMERGENCY(2),
}

data class PushoverResponse(
    val status: Int,
    val request: String,
    val user: String? = null,
    val errors: List<String>? = null,
    val receipt: String? = null,
    val canceled: Int? = null,
    val applicationUsage: ApplicationUsage? = null,
)
