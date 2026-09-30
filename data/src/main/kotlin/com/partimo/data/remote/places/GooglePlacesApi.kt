package com.partimo.data.remote.places

import com.partimo.data.config.AndroidAppIdentity
import com.partimo.data.network.androidAppHeaders
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** Client minimale di Google Places API (New). Restituisce il body grezzo per consentirne la cache. */
internal class GooglePlacesApi(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    /** Package e certificato dell'app, per le chiavi limitate alle app Android. */
    private val androidApp: AndroidAppIdentity? = null,
) {

    suspend fun searchText(request: PlacesTextSearchRequest, fieldMask: String): String =
        client.post("${baseUrl}places:searchText") {
            header(API_KEY_HEADER, apiKey)
            androidAppHeaders(androidApp)
            // Il field mask è obbligatorio e limita i campi (e quindi il costo) della risposta.
            header(FIELD_MASK_HEADER, fieldMask)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.bodyAsText()

    /**
     * URL della foto servita da Places (redirect all'immagine), caricabile direttamente da Coil.
     * Con una chiave limitata alle app Android, le intestazioni dell'app le aggiunge il client delle immagini.
     */
    fun photoUrl(photoName: String, maxWidthPx: Int = PHOTO_MAX_WIDTH_PX): String =
        "$baseUrl$photoName/media?maxWidthPx=$maxWidthPx&key=$apiKey"

    companion object {
        const val DEFAULT_BASE_URL = "https://places.googleapis.com/v1/"
        const val API_KEY_HEADER = "X-Goog-Api-Key"
        const val FIELD_MASK_HEADER = "X-Goog-FieldMask"
        private const val PHOTO_MAX_WIDTH_PX = 640
    }
}
