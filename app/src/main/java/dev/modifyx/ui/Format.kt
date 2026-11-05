package dev.modifyx.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} ${units[unit]}" else String.format(Locale.US, "%.1f %s", value, units[unit])
}

fun formatDate(millis: Long): String {
    if (millis <= 0L) return "unknown"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
}

fun formatDateTime(millis: Long): String {
    if (millis <= 0L) return "unknown"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
}

fun formatAgo(millis: Long, now: Long = System.currentTimeMillis()): String {
    if (millis <= 0L) return "unknown"
    val delta = abs(now - millis)
    val minutes = delta / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 30 -> "${days}d ago"
        else -> formatDate(millis)
    }
}

fun formatDuration(millis: Long): String = when {
    millis < 1_000 -> "${millis}ms"
    millis < 60_000 -> String.format(Locale.US, "%.1fs", millis / 1000.0)
    else -> "${millis / 60_000}m ${(millis % 60_000) / 1000}s"
}
