package id.whitesecurity.antivirus.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The server returns two date shapes: ISO-8601 instants from the CLI
 * ("2026-11-13T10:39:37.334Z") and SQLite's datetime('now') in UTC without
 * a zone ("2026-11-14 04:15:00"). Both are UTC.
 */
object ServerDates {
    private val SQLITE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val DISPLAY_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("id", "ID"))
    private val DISPLAY_DATETIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("id", "ID"))

    fun parse(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value, SQLITE).toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    fun formatDate(value: String?): String? =
        parse(value)?.let { DISPLAY_DATE.withZone(ZoneId.systemDefault()).format(it) }

    fun formatDateTime(value: String?): String? =
        parse(value)?.let { DISPLAY_DATETIME.withZone(ZoneId.systemDefault()).format(it) }

    /** "baru saja", "5 menit lalu", "3 hari lalu" … */
    fun ago(value: String?): String {
        val t = parse(value) ?: return "belum pernah"
        val minutes = (System.currentTimeMillis() - t.toEpochMilli()) / 60_000
        return when {
            minutes < 2 -> "baru saja"
            minutes < 60 -> "$minutes menit lalu"
            minutes < 1440 -> "${minutes / 60} jam lalu"
            else -> "${minutes / 1440} hari lalu"
        }
    }
}
