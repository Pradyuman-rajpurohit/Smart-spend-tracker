package dev.spendtracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector
import dev.spendtracker.data.db.AccountType

/** Icon keys stored on categories, mapped to vectors. Keys are stable; never rename them. */
object CategoryIcons {

    val options: List<Pair<String, ImageVector>> = listOf(
        "food" to Icons.Outlined.Restaurant,
        "grocery" to Icons.Outlined.LocalGroceryStore,
        "transport" to Icons.Outlined.DirectionsCar,
        "shopping" to Icons.Outlined.ShoppingBag,
        "bills" to Icons.Outlined.Bolt,
        "home" to Icons.Outlined.Home,
        "health" to Icons.Outlined.MedicalServices,
        "fun" to Icons.Outlined.Movie,
        "people" to Icons.Outlined.Group,
        "travel" to Icons.Outlined.Flight,
        "other" to Icons.Outlined.MoreHoriz,
        "salary" to Icons.Outlined.Work,
        "refund" to Icons.Outlined.Replay,
        "person" to Icons.Outlined.Person,
        "other_in" to Icons.Outlined.Savings,
        "cafe" to Icons.Outlined.LocalCafe,
        "fuel" to Icons.Outlined.LocalGasStation,
        "bike" to Icons.Outlined.TwoWheeler,
        "train" to Icons.Outlined.Train,
        "education" to Icons.Outlined.School,
        "fitness" to Icons.Outlined.FitnessCenter,
        "pets" to Icons.Outlined.Pets,
        "clothes" to Icons.Outlined.Checkroom,
        "gadgets" to Icons.Outlined.Devices,
        "gift" to Icons.Outlined.CardGiftcard,
        "games" to Icons.Outlined.SportsEsports,
        "subscription" to Icons.Outlined.Subscriptions,
        "invest" to Icons.AutoMirrored.Outlined.TrendingUp,
        "receipt" to Icons.Outlined.Receipt,
        "wallet" to Icons.Outlined.AccountBalanceWallet,
    )

    private val byKey: Map<String, ImageVector> = options.toMap()

    fun get(key: String?): ImageVector = byKey[key] ?: Icons.Outlined.Category

    val transfer: ImageVector = Icons.Outlined.SwapHoriz
    val review: ImageVector = Icons.AutoMirrored.Outlined.HelpOutline
}

object AccountVisuals {

    fun icon(type: AccountType): ImageVector = when (type) {
        AccountType.BANK -> Icons.Outlined.AccountBalance
        AccountType.CARD -> Icons.Outlined.CreditCard
        AccountType.CASH -> Icons.Outlined.Payments
        AccountType.WALLET -> Icons.Outlined.AccountBalanceWallet
        AccountType.CRYPTO -> Icons.Outlined.CurrencyBitcoin
        AccountType.OTHER -> Icons.Outlined.Savings
    }

    fun label(type: AccountType): String = when (type) {
        AccountType.BANK -> "Bank account"
        AccountType.CARD -> "Credit card"
        AccountType.CASH -> "Cash"
        AccountType.WALLET -> "Wallet"
        AccountType.CRYPTO -> "Crypto"
        AccountType.OTHER -> "Other"
    }

    fun shortLabel(type: AccountType): String = when (type) {
        AccountType.BANK -> "Bank"
        AccountType.CARD -> "Card"
        AccountType.CASH -> "Cash"
        AccountType.WALLET -> "Wallet"
        AccountType.CRYPTO -> "Crypto"
        AccountType.OTHER -> "Other"
    }
}
