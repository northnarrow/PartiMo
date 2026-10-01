package com.partimo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.partimo.app.navigation.IncomingRoutes
import com.partimo.app.navigation.PartiMoNavHost
import com.partimo.app.ui.theme.PartiMoTheme

class MainActivity : ComponentActivity() {

    /** Schermata da aprire: notifica toccata (offerta, promemoria), widget o contenuto condiviso con PartiMo. */
    private var pendingRoute by mutableStateOf<Any?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Dopo una ricreazione (es. rotazione) il back stack è già ripristinato: niente doppia apertura.
        if (savedInstanceState == null) pendingRoute = IncomingRoutes.from(intent)
        val container = (application as PartiMoApp).container
        setContent {
            PartiMoTheme {
                PartiMoNavHost(
                    container = container,
                    pendingRoute = pendingRoute,
                    onPendingRouteOpened = { pendingRoute = null },
                )
            }
        }
    }

    /** App già aperta (launchMode singleTop): notifica o condivisione arrivano qui invece di creare una nuova activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        IncomingRoutes.from(intent)?.let { pendingRoute = it }
    }
}
