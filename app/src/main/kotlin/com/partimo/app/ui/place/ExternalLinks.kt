package com.partimo.app.ui.place

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.partimo.domain.model.GeoPoint

/**
 * Collegamenti verso l'esterno: Google Maps per raggiungere i luoghi, il browser interno di PartiMo
 * per i siti (voli, alloggi, Wikipedia).
 */
object ExternalLinks {

    /**
     * Apre le indicazioni fino a [destination] nell'app Google Maps; se non è installata, le stesse
     * indicazioni si aprono nel browser interno. `false` se nessuna app può gestirle.
     */
    fun openNavigation(context: Context, destination: GeoPoint, @ColorInt toolbarColor: Int? = null): Boolean =
        openLink(context, googleMapsDirectionsUrl(destination), toolbarColor)

    /**
     * Apre un collegamento: le pagine di Google Maps nell'app Maps se installata; i siti (Google Voli,
     * Booking.com, Wikipedia…) nel browser interno, cioè una scheda del browser (Custom Tab) sopra
     * PartiMo, con la barra nei colori dell'app e la X per tornare indietro. Senza un browser
     * compatibile la pagina si apre nel browser predefinito. `false` se nessuna app può aprirla.
     */
    fun openLink(context: Context, url: String, @ColorInt toolbarColor: Int? = null): Boolean {
        val uri = Uri.parse(url)
        return (isGoogleMapsUrl(url) && startView(context, uri, GOOGLE_MAPS_PACKAGE)) || openInAppBrowser(context, uri, toolbarColor)
    }

    /** La scheda condivide accessi e cookie del browser (es. il consenso di Google già dato, l'account Booking). */
    private fun openInAppBrowser(context: Context, uri: Uri, @ColorInt toolbarColor: Int?): Boolean = try {
        val builder = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
        toolbarColor?.let { color ->
            builder.setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(color).build())
        }
        builder.build().launchUrl(context, uri)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun startView(context: Context, uri: Uri, packageName: String?): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
