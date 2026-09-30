package com.partimo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.partimo.app.navigation.DashboardDestination
import com.partimo.app.navigation.PartiMoNavHost
import com.partimo.app.notifications.DealNotifier
import com.partimo.app.ui.theme.PartiMoTheme

class MainActivity : ComponentActivity() {

    /** Viaggio da aprire perché l'utente ha toccato la notifica di un'offerta. */
    private var pendingDashboard by mutableStateOf<DashboardDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Dopo una ricreazione (es. rotazione) il back stack è già ripristinato: niente doppia apertura.
        if (savedInstanceState == null) pendingDashboard = DealNotifier.dashboardRouteFrom(intent)
        val container = (application as PartiMoApp).container
        setContent {
            PartiMoTheme {
                PartiMoNavHost(
                    container = container,
                    pendingDashboard = pendingDashboard,
                    onPendingDashboardOpened = { pendingDashboard = null },
                )
            }
        }
    }

    /** App già aperta (launchMode singleTop): la notifica arriva qui invece di creare una nuova activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DealNotifier.dashboardRouteFrom(intent)?.let { pendingDashboard = it }
    }
}
