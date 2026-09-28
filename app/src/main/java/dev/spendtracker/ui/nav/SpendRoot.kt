package dev.spendtracker.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.spendtracker.ui.budgets.BudgetsScreen
import dev.spendtracker.ui.edit.EditTransactionScreen
import dev.spendtracker.ui.edit.QuickAddScreen
import dev.spendtracker.ui.home.HomeScreen
import dev.spendtracker.ui.pending.PendingScreen
import dev.spendtracker.ui.pending.PendingTab
import dev.spendtracker.ui.profile.ProfileScreen
import dev.spendtracker.ui.settings.CategoriesScreen
import dev.spendtracker.ui.settings.RatesScreen
import dev.spendtracker.ui.theme.SpendColors
import dev.spendtracker.ui.transactions.TransactionsScreen
import dev.spendtracker.ui.transactions.TxFilter

/** Where a notification tap wants to land. */
sealed interface OpenRequest {
    data class Transaction(val id: Long) : OpenRequest
    data object Autopay : OpenRequest
}

object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions?filter={filter}"
    const val PENDING = "pending?tab={tab}"
    const val PROFILE = "profile"
    const val BUDGETS = "budgets"
    const val CATEGORIES = "categories"
    const val RATES = "rates"
    const val ADD = "add"
    const val EDIT = "edit/{id}"

    fun transactions(filter: TxFilter = TxFilter.ALL) = "transactions?filter=${filter.name}"
    fun pending(tab: PendingTab = PendingTab.SETTLE) = "pending?tab=${tab.name}"
    fun edit(id: Long) = "edit/$id"

    val topLevel = setOf(HOME, TRANSACTIONS, PENDING, PROFILE)
}

@Composable
fun SpendRoot(openRequest: OpenRequest? = null, onOpenHandled: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = currentRoute in Routes.topLevel

    val navigateTop: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val openEdit: (Long) -> Unit = { id -> navController.navigate(Routes.edit(id)) }
    val openAdd: () -> Unit = { navController.navigate(Routes.ADD) }

    LaunchedEffect(openRequest) {
        when (openRequest) {
            is OpenRequest.Transaction -> openEdit(openRequest.id)
            OpenRequest.Autopay -> navigateTop(Routes.pending(PendingTab.AUTOPAY))
            null -> return@LaunchedEffect
        }
        onOpenHandled()
    }

    Scaffold(
        containerColor = SpendColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                AppBottomBar(currentRoute = currentRoute, onNavigate = navigateTop, onAdd = openAdd)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(120)) },
            exitTransition = { fadeOut(tween(80)) },
            popEnterTransition = { fadeIn(tween(120)) },
            popExitTransition = { fadeOut(tween(80)) }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenTransactions = { navigateTop(Routes.transactions()) },
                    onOpenReview = { navigateTop(Routes.transactions(TxFilter.REVIEW)) },
                    onOpenBudgets = { navController.navigate(Routes.BUDGETS) },
                    onOpenPending = { navigateTop(Routes.pending()) },
                    onOpenAutopay = { navigateTop(Routes.pending(PendingTab.AUTOPAY)) },
                    onOpenTransaction = openEdit
                )
            }
            composable(
                Routes.TRANSACTIONS,
                arguments = listOf(navArgument("filter") {
                    type = NavType.StringType
                    defaultValue = TxFilter.ALL.name
                })
            ) { entry ->
                val filterName = entry.arguments?.getString("filter") ?: TxFilter.ALL.name
                val filter = TxFilter.entries.firstOrNull { it.name == filterName } ?: TxFilter.ALL
                TransactionsScreen(initialFilter = filter, onOpenTransaction = openEdit)
            }
            composable(
                Routes.PENDING,
                arguments = listOf(navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = PendingTab.SETTLE.name
                })
            ) { entry ->
                val tabName = entry.arguments?.getString("tab") ?: PendingTab.SETTLE.name
                val tab = PendingTab.entries.firstOrNull { it.name == tabName } ?: PendingTab.SETTLE
                PendingScreen(initialTab = tab, onOpenTransaction = openEdit)
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenBudgets = { navController.navigate(Routes.BUDGETS) },
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenRates = { navController.navigate(Routes.RATES) },
                    onOpenReview = { navigateTop(Routes.transactions(TxFilter.REVIEW)) }
                )
            }
            composable(Routes.BUDGETS) {
                BudgetsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.RATES) {
                RatesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ADD) {
                QuickAddScreen(onDone = { navController.popBackStack() })
            }
            composable(
                Routes.EDIT,
                arguments = listOf(navArgument("id") {
                    type = NavType.LongType
                    defaultValue = 0L
                })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                EditTransactionScreen(txId = id, onDone = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(SpendColors.Background)) {
        HorizontalDivider(color = SpendColors.Divider)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(Icons.Outlined.Home, "Home", currentRoute == Routes.HOME) { onNavigate(Routes.HOME) }
            NavItem(Icons.AutoMirrored.Outlined.ReceiptLong, "Transactions", currentRoute == Routes.TRANSACTIONS) {
                onNavigate(Routes.transactions())
            }
            FilledIconButton(
                onClick = onAdd,
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = SpendColors.Violet,
                    contentColor = SpendColors.Background
                )
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add transaction", modifier = Modifier.size(26.dp))
            }
            NavItem(Icons.Outlined.PendingActions, "Pending", currentRoute == Routes.PENDING) { onNavigate(Routes.pending()) }
            NavItem(Icons.Outlined.AccountCircle, "Profile", currentRoute == Routes.PROFILE) { onNavigate(Routes.PROFILE) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) SpendColors.Text else SpendColors.Inactive,
            modifier = Modifier.size(24.dp)
        )
    }
}
