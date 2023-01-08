package de.kleinkop.pushover4k.client

import java.time.LocalDateTime
import java.time.ZoneOffset

fun String.nullable(): String? = when (this) {
    "" -> null
    else -> this
}

fun Long.toLocalDateTimeUTC(): LocalDateTime = LocalDateTime.ofEpochSecond(this, 0, ZoneOffset.UTC)

fun Long.toLocalDateTimeOrNull(): LocalDateTime? = when (this) {
    0L -> null
    else -> this.toLocalDateTimeUTC()
}
