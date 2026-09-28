package dev.spendtracker.util

data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val name: String,
    /** Rupees per one unit, used until the user edits the rate in Profile. */
    val defaultRate: Double,
)

object Currencies {

    const val INR = "INR"

    val all: List<CurrencyInfo> = listOf(
        CurrencyInfo("INR", "₹", "Indian rupee", 1.0),
        CurrencyInfo("USD", "$", "US dollar", 88.0),
        CurrencyInfo("EUR", "€", "Euro", 102.0),
        CurrencyInfo("GBP", "£", "British pound", 118.0),
        CurrencyInfo("AED", "AED ", "UAE dirham", 24.0),
    )

    val foreign: List<CurrencyInfo> get() = all.filter { it.code != INR }

    fun get(code: String?): CurrencyInfo = all.firstOrNull { it.code == code } ?: all.first()
}
