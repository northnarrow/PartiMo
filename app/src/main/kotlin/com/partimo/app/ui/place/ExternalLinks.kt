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
    fun openNavigation(context: Context, destination: GeoPoint): Boolean = openLink(context, googleMapsDirectionsUrl(destination))

    /** Apre una pagina web (es. la voce di Wikipedia); `false` se nessuna app può aprirla. */
    fun openWebPage(context: Context, url: String): Boolean = startView(context, Uri.parse(url), packageName = null)

    /**
     * Apre un collegamento: le pagine di Google Maps nell'app Maps se installata, le altre (es. siti
     * di prenotazione) nel browser o nell'app del sito. `false` se nessuna app può aprirlo.
     */
    fun openLink(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        return (isGoogleMapsUrl(url) && startView(context, uri, GOOGLE_MAPS_PACKAGE)) || startView(context, uri, packageName = null)
    }

    private fun startView(context: Context, uri: Uri, packageName: String?): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
