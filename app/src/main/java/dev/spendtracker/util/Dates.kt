package dev.spendtracker.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val shortDayFmt = DateTimeFormatter.ofPattern("d MMM, EEE", Locale.ENGLISH)
    private val dayMonthFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val fullFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val csvFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)

    fun monthStart(ym: YearMonth): Long =
        ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Last millisecond of the month. */
    fun monthEnd(ym: YearMonth): Long =
        ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun dayStart(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun toMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun toLocalDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    fun toLocalTime(millis: Long): LocalTime = toLocalDateTime(millis).toLocalTime()

    fun monthLabel(ym: YearMonth): String = ym.format(monthFmt)

    fun fullDate(date: LocalDate): String = date.format(fullFmt)

    fun time(millis: Long): String = toLocalDateTime(millis).format(timeFmt)

    /** "26 Sep". */
    fun shortDate(millis: Long): String = toLocalDate(millis).format(dayMonthFmt)

    fun csv(millis: Long): String = toLocalDateTime(millis).format(csvFmt)

    /** "Today", "Yesterday", or "26 Sep, Fri". */
    fun dayHeading(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(shortDayFmt)
    }

    /** "Today, 1:12 PM" or "26 Sep, 3:00 PM". */
    fun rowTime(millis: Long, today: LocalDate = LocalDate.now()): String {
        val dt = toLocalDateTime(millis)
        val day = when (dt.toLocalDate()) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> dt.toLocalDate().format(dayMonthFmt)
        }
        return day + ", " + dt.format(timeFmt)
    }

    fun daysLeftInMonth(today: LocalDate = LocalDate.now()): Int =
        today.lengthOfMonth() - today.dayOfMonth + 1
}
