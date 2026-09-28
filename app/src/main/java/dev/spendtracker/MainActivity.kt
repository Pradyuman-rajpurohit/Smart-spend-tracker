package dev.spendtracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import dev.spendtracker.notify.Notifications
import dev.spendtracker.ui.nav.OpenRequest
import dev.spendtracker.ui.nav.SpendRoot
import dev.spendtracker.ui.theme.SpendTheme

class MainActivity : ComponentActivity() {

    /** Where a notification tap wants the app to go. Consumed by [SpendRoot]. */
    private val openRequest = mutableStateOf<OpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        openRequest.value = requestFrom(intent)
        setContent {
            val request by openRequest
            SpendTheme {
                SpendRoot(openRequest = request, onOpenHandled = { openRequest.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestFrom(intent)?.let { openRequest.value = it }
    }

    private fun requestFrom(intent: Intent?): OpenRequest? {
        if (intent == null) return null
        val txId = intent.getLongExtra(Notifications.EXTRA_OPEN_TX, 0L)
        if (txId > 0L) return OpenRequest.Transaction(txId)
        if (intent.getStringExtra(Notifications.EXTRA_OPEN_TAB) == Notifications.TAB_AUTOPAY) return OpenRequest.Autopay
        return null
    }
}
