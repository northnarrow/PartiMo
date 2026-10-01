package com.partimo.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.partimo.app.R
import com.partimo.domain.model.GeoPoint

/**
 * "Dove sono": chiede il permesso di posizione (basta quella approssimativa), poi legge la posizione
 * attuale con il LocationManager di Android, senza servizi Google. La posizione resta sul telefono.
 * Restituisce l'azione da collegare al pulsante.
 */
@Composable
fun rememberMyLocation(onLocation: (GeoPoint) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnLocation by rememberUpdatedState(onLocation)
    val unavailable = stringResource(R.string.map_location_unavailable)
    val denied = stringResource(R.string.map_location_denied)
    val fetch = {
        requestCurrentLocation(context) { location ->
            if (location != null) {
                currentOnLocation(GeoPoint(location.latitude, location.longitude))
            } else {
                Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) fetch() else Toast.makeText(context, denied, Toast.LENGTH_LONG).show()
    }
    return {
        if (hasLocationPermission(context)) {
            fetch()
        } else {
            permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/** Posizione attuale (al massimo ~30 secondi di attesa), altrimenti l'ultima nota; `null` se non c'è. */
@SuppressLint("MissingPermission") // Il permesso è verificato da hasLocationPermission().
private fun requestCurrentLocation(context: Context, onResult: (Location?) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java)
    val provider = manager?.let { bestProvider(context, it) }
    if (manager == null || provider == null || !hasLocationPermission(context)) {
        onResult(null)
        return
    }
    LocationManagerCompat.getCurrentLocation(manager, provider, null as CancellationSignal?, ContextCompat.getMainExecutor(context)) { location ->
        onResult(location ?: runCatching { manager.getLastKnownLocation(provider) }.getOrNull())
    }
}

/**
 * Fonte della posizione: quella "fused" di Android 12+ (funziona anche con la posizione approssimativa),
 * altrimenti la rete o, con il permesso preciso, il GPS. `null` se la localizzazione è spenta.
 */
private fun bestProvider(context: Context, manager: LocationManager): String? {
    if (!LocationManagerCompat.isLocationEnabled(manager)) return null
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val candidates = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (fine) add(LocationManager.GPS_PROVIDER)
    }
    return candidates.firstOrNull { LocationManagerCompat.hasProvider(manager, it) && manager.isProviderEnabled(it) }
}
