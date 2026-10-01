package com.partimo.app.ui.place

import com.partimo.domain.model.GeoPoint
import java.net.URLEncoder
import java.util.Locale

/** Pacchetto dell'app Google Maps, aperta direttamente quando è installata. */
const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

private const val DIRECTIONS_URL = "https://www.google.com/maps/dir/?api=1"

/**
 * Indicazioni di Google Maps dalla posizione attuale fino al luogo, con le "Maps URLs" ufficiali:
 * funzionano nell'app, nel browser e su qualunque piattaforma, senza chiave API. La modalità di
 * viaggio non è indicata: Maps propone quella che l'utente usa di solito (a piedi, mezzi, auto).
 * https://developers.google.com/maps/documentation/urls/get-started#directions-action
 */
fun googleMapsDirectionsUrl(destination: GeoPoint): String =
    "$DIRECTIONS_URL&destination=" + encode(destination.coordinates())

/**
 * Percorso con i mezzi pubblici da [origin] a [destination]: Google Maps mostra linee, orari e
 * cambi reali della città con partenza adesso, senza chiave API.
 */
fun googleMapsTransitUrl(origin: GeoPoint, destination: GeoPoint): String =
    "$DIRECTIONS_URL&origin=" + encode(origin.coordinates()) +
        "&destination=" + encode(destination.coordinates()) +
        "&travelmode=transit"

/** Tappa di un percorso: le coordinate, se note, altrimenti il nome da cercare (es. "Café Central, Vienna"). */
sealed interface RouteStop {
    data class At(val point: GeoPoint) : RouteStop

    data class Named(val query: String) : RouteStop
}

/** Tappe intermedie accettate da Google Maps nell'app (nel browser del telefono solo 3). */
const val MAX_ROUTE_WAYPOINTS = 9

/**
 * Giro a piedi tra le tappe, nell'ordine dato: si parte dalla prima, si arriva all'ultima e le altre
 * sono tappe intermedie (oltre [MAX_ROUTE_WAYPOINTS] vengono omesse). `null` con meno di due tappe.
 * https://developers.google.com/maps/documentation/urls/get-started#directions-action
 */
fun googleMapsWalkingRouteUrl(stops: List<RouteStop>): String? {
    if (stops.size < 2) return null
    val waypoints = stops.subList(1, stops.size - 1).take(MAX_ROUTE_WAYPOINTS)
    return "$DIRECTIONS_URL&origin=" + encode(stops.first().text()) +
        "&destination=" + encode(stops.last().text()) +
        (if (waypoints.isEmpty()) "" else "&waypoints=" + encode(waypoints.joinToString(separator = "|") { it.text() })) +
        "&travelmode=walking"
}

private fun RouteStop.text(): String = when (this) {
    is RouteStop.At -> point.coordinates()
    is RouteStop.Named -> query
}

/** Ricerca testuale su Google Maps (es. "nome, indirizzo, città"): scheda del luogo con foto e recensioni. */
fun googleMapsSearchUrl(query: String): String = "https://www.google.com/maps/search/?api=1&query=" + encode(query)

/** `true` per le pagine di Google Maps, che conviene aprire nell'app Maps quando è installata. */
fun isGoogleMapsUrl(url: String): Boolean =
    url.startsWith("https://www.google.com/maps") || url.startsWith("https://maps.google.com") || url.startsWith("https://maps.app.goo.gl")

/** Il punto decimale non dipende dalla lingua del telefono. */
private fun GeoPoint.coordinates(): String = String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude)

private fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")
