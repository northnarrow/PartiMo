package com.partimo.app.ui.place

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.partimo.domain.model.GeoPoint

/** Collegamenti verso app esterne: Google Maps per raggiungere il luogo, il browser per le fonti. */
object ExternalLinks {

    /**
     * Apre le indicazioni fino a [destination] nell'app Google Maps; se non è installata, le stesse
     * indicazioni si aprono nel browser. `false` se nessuna app può gestirle.
     */
    fun openNavigation(context: Context, destination: GeoPoint): Boolean {
        val uri = Uri.parse(googleMapsDirectionsUrl(destination))
        return startView(context, uri, GOOGLE_MAPS_PACKAGE) || startView(context, uri, packageName = null)
    }

    /** Apre una pagina web (es. la voce di Wikipedia); `false` se nessuna app può aprirla. */
    fun openWebPage(context: Context, url: String): Boolean = startView(context, Uri.parse(url), packageName = null)

    private fun startView(context: Context, uri: Uri, packageName: String?): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
