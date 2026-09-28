package dev.spendtracker.util

import dev.spendtracker.data.db.TxType
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Amounts are stored as minor units (paise, cents) in a Long, together with a currency code.
 * Totals across the app use the rupee value ([toInr]) so mixed currencies add up.
 */
object Money {

    private const val MINUS = "−"

    /** 12345678 -> "1,23,45,678" (Indian lakh/crore grouping). */
    fun groupIndian(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val sb = StringBuilder()
        while (rest.length > 2) {
            sb.insert(0, "," + rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        return rest + sb + "," + last3
    }

    /** 1234567 -> "1,234,567". */
    fun groupWestern(n: Long): String {
        val s = n.toString()
        val sb = StringBuilder()
        var count = 0
        for (i in s.length - 1 downTo 0) {
            sb.append(s[i])
            count++
            if (count % 3 == 0 && i > 0) sb.append(',')
        }
        return sb.reverse().toString()
    }

    /** "₹42,380", "$12.50", "−₹1,250.50". */
    fun format(minor: Long, currency: String = Currencies.INR, showMinor: Boolean = false): String {
        val info = Currencies.get(currency)
        val absValue = abs(minor)
        val whole = absValue / 100
        val frac = absValue % 100
        val grouped = if (currency == Currencies.INR) groupIndian(whole) else groupWestern(whole)
        val body = if (showMinor || frac != 0L) grouped + "." + frac.toString().padStart(2, '0') else grouped
        return (if (minor < 0) MINUS else "") + info.symbol + body
    }

    /** "−₹486" for expenses, "+₹1,199" for income, "₹5,000" for transfers. */
    fun signed(minor: Long, currency: String, type: TxType): String = when (type) {
        TxType.EXPENSE -> MINUS + format(minor, currency)
        TxType.INCOME -> "+" + format(minor, currency)
        TxType.TRANSFER -> format(minor, currency)
    }

    /** Converts to rupee paise with the given rates map (rupees per unit). INR passes through. */
    fun toInr(minor: Long, currency: String, rates: Map<String, Double>): Long {
        if (currency == Currencies.INR) return minor
        val rate = rates[currency] ?: Currencies.get(currency).defaultRate
        return (minor * rate).roundToLong()
    }

    /** Converts rupee paise into minor units of [currency]. INR passes through. */
    fun fromInr(inrPaise: Long, currency: String, rates: Map<String, Double>): Long {
        if (currency == Currencies.INR) return inrPaise
        val rate = rates[currency] ?: Currencies.get(currency).defaultRate
        return (inrPaise / rate).roundToLong()
    }

    private val strip = Regex("[₹$€£,\\s]")

    /** Parses "1,250.50", "₹ 1250", "1250" into minor units. Null when not a positive amount. */
    fun parseToPaise(input: String): Long? {
        val cleaned = input.replace(strip, "")
        if (cleaned.isEmpty()) return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value <= BigDecimal.ZERO) return null
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /** Like [parseToPaise] but accepts zero and an empty field (both give 0). Null on bad input or negatives. */
    fun parseNonNegativePaise(input: String): Long? {
        val cleaned = input.replace(strip, "")
        if (cleaned.isEmpty()) return 0L
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value < BigDecimal.ZERO) return null
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /** Parses a rate like "88.25". Null when not positive. */
    fun parseRate(input: String): Double? {
        val value = input.trim().replace(",", "").toDoubleOrNull() ?: return null
        return if (value > 0.0) value else null
    }

    private val amountInput = Regex("[0-9]*\\.?[0-9]{0,2}")

    /** True for partial typing that can still become a valid amount: digits, one dot, two decimals. */
    fun isValidInput(text: String): Boolean = text.length <= 12 && text.matches(amountInput)

    /** Value for an input field: "1250" or "1250.50". */
    fun toInput(minor: Long): String {
        if (minor == 0L) return ""
        val whole = minor / 100
        val frac = minor % 100
        return if (frac == 0L) whole.toString() else whole.toString() + "." + frac.toString().padStart(2, '0')
    }
}
