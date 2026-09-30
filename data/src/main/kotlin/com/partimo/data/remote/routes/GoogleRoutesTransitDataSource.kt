package com.partimo.data.remote.routes

import com.partimo.data.cache.CacheKey
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.config.AndroidAppIdentity
import com.partimo.data.network.Fetched
import com.partimo.data.network.NetworkJson
import com.partimo.data.network.androidAppHeaders
import com.partimo.data.network.mapNotNullSafely
import com.partimo.data.source.TransitDataSource
import com.partimo.domain.model.transit.TransitRoute
import com.partimo.domain.model.transit.TransitRouteQuery
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.time.temporal.ChronoUnit

/** Client minimale di Google Routes API (computeRoutes). */
internal class GoogleRoutesApi(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    /** Package e certificato dell'app, per le chiavi limitate alle app Android. */
    private val androidApp: AndroidAppIdentity? = null,
) {

    suspend fun computeRoutes(request: ComputeRoutesRequest): String =
        client.post("${baseUrl}directions/v2:computeRoutes") {
            header(API_KEY_HEADER, apiKey)
            androidAppHeaders(androidApp)
            header(FIELD_MASK_HEADER, FIELD_MASK)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.bodyAsText()

    companion object {
        const val DEFAULT_BASE_URL = "https://routes.googleapis.com/"
        const val API_KEY_HEADER = "X-Goog-Api-Key"
        const val FIELD_MASK_HEADER = "X-Goog-FieldMask"
        val FIELD_MASK = listOf(
            "routes.duration",
            "routes.distanceMeters",
            "routes.legs.steps.travelMode",
            "routes.legs.steps.staticDuration",
            "routes.legs.steps.distanceMeters",
            "routes.legs.steps.transitDetails",
        ).joinToString(",")
    }
}

/** Percorsi multimodali con orari aggiornati (metro, bus, tram, treni) da Google Routes API. */
class GoogleRoutesTransitDataSource internal constructor(
    private val api: GoogleRoutesApi,
    private val cache: ResponseCache,
    private val languageCode: String,
) : TransitDataSource {

    override suspend fun routes(query: TransitRouteQuery, forceRefresh: Boolean): Fetched<List<TransitRoute>> {
        // Orario arrotondato al minuto: richieste ravvicinate riusano la stessa entry (TTL di 2 minuti).
        val departure = query.departureTime.truncatedTo(ChronoUnit.MINUTES)
        val preferences = RoutesTransitPreferences(
            allowedTravelModes = query.allowedModes.toAllowedTravelModes(),
            routingPreference = query.preference.toRoutingPreference(),
        )
        val request = ComputeRoutesRequest(
            origin = query.origin.toWaypoint(),
            destination = query.destination.toWaypoint(),
            departureTime = departure.toString(),
            languageCode = languageCode,
            transitPreferences = preferences.takeIf { it.allowedTravelModes != null || it.routingPreference != null },
        )
        return cache.getOrFetch(
            key = CacheKey.of("transit", NetworkJson.encodeToString(ComputeRoutesRequest.serializer(), request)),
            ttl = CachePolicy.TRANSIT,
            forceRefresh = forceRefresh,
            fetch = { api.computeRoutes(request) },
            parse = { body ->
                NetworkJson.decodeFromString(ComputeRoutesResponse.serializer(), body)
                    .routes
                    .mapNotNullSafely { route -> RoutesMapper.toDomain(route, departure) }
            },
        )
    }
}
