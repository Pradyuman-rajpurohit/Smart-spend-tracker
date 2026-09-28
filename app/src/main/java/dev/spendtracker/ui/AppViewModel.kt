package dev.spendtracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.spendtracker.SpendApp
import dev.spendtracker.data.AppContainer

/**
 * Creates a ViewModel with access to the app container, scoped to the current
 * navigation destination like a normal `viewModel()` call.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(noinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as SpendApp).container
    return viewModel(
        factory = viewModelFactory {
            initializer { create(container) }
        }
    )
}
