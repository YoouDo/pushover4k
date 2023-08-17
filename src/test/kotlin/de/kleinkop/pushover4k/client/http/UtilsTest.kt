package de.kleinkop.pushover4k.client.http

import de.kleinkop.pushover4k.client.nullable
import de.kleinkop.pushover4k.client.toLocalDateTimeInUTC
import de.kleinkop.pushover4k.client.toLocalDateTimeOrNull
import de.kleinkop.pushover4k.client.toLocalDateTimeUTC
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class UtilsTest {
    @Test
    fun `Check utility functions`() {
        OffsetDateTime
            .parse("2023-08-17T17:38:00+02:00")
            .toLocalDateTimeInUTC() shouldBe LocalDateTime.parse("2023-08-17T15:38:00")

        "".nullable() shouldBe null

        "a".nullable() shouldBe "a"

        val expected = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        val epochSecond = expected.toEpochSecond(ZoneOffset.UTC)

        epochSecond.toLocalDateTimeUTC() shouldBe expected

        epochSecond.toLocalDateTimeOrNull() shouldBe expected

        (0L).toLocalDateTimeOrNull() shouldBe null
    }
}
