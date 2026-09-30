package com.partimo.app.ui.place

import com.partimo.domain.model.GeoPoint
import java.net.URLEncoder
import java.util.Locale

/** Pacchetto dell'app Google Maps, aperta direttamente quando è installata. */
const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

/**
 * Indicazioni di Google Maps dalla posizione attuale fino al luogo, con le "Maps URLs" ufficiali:
 * funzionano nell'app, nel browser e su qualunque piattaforma, senza chiave API. La modalità di
 * viaggio non è indicata: Maps propone quella che l'utente usa di solito (a piedi, mezzi, auto).
 * https://developers.google.com/maps/documentation/urls/get-started#directions-action
 */
fun googleMapsDirectionsUrl(destination: GeoPoint): String {
    val coordinates = String.format(Locale.ROOT, "%.6f,%.6f", destination.latitude, destination.longitude)
    return "https://www.google.com/maps/dir/?api=1&destination=" + URLEncoder.encode(coordinates, Charsets.UTF_8.name())
}
