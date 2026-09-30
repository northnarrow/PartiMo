package com.partimo.data.remote.geocoding

import com.partimo.data.cache.CacheKey
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.network.Fetched
import com.partimo.data.network.NetworkJson
import com.partimo.data.network.mapNotNullSafely
import com.partimo.data.source.CitySearchDataSource
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.place.CityPlace
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Ricerca di città in tutto il mondo con Open-Meteo Geocoding (dati GeoNames): gratuita,
 * senza chiave API, con nomi localizzati (es. "Parigi, Francia") e fuso orario della città.
 */
class OpenMeteoGeocodingDataSource(
    private val client: HttpClient,
    private val cache: ResponseCache,
    private val languageCode: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : CitySearchDataSource {

    override suspend fun searchCities(query: String, limit: Int): Fetched<List<CityPlace>> {
        val count = limit.coerceIn(1, MAX_RESULTS)
        return cache.getOrFetch(
            key = CacheKey.of("geocoding", query.lowercase(), count, languageCode),
            ttl = CachePolicy.GEOCODING,
            fetch = {
                client.get("${baseUrl}search") {
                    parameter("name", query)
                    parameter("count", count)
                    parameter("language", languageCode)
                    parameter("format", "json")
                }.bodyAsText()
            },
            parse = { body ->
                NetworkJson.decodeFromString(GeocodingResponse.serializer(), body)
                    .results
                    .mapNotNullSafely { it.toDomain() }
            },
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://geocoding-api.open-meteo.com/v1/"
        private const val MAX_RESULTS = 100
    }
}

@Serializable
internal data class GeocodingResponse(val results: List<GeocodingResultDto> = emptyList())

@Serializable
internal data class GeocodingResultDto(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** Codice GeoNames: PPL* indica un centro abitato (PPLC = capitale). */
    @SerialName("feature_code") val featureCode: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val timezone: String? = null,
    val population: Int? = null,
    val country: String? = null,
    val admin1: String? = null,
)

/** Solo centri abitati con paese noto; il fuso orario non valido ripiega su UTC. */
internal fun GeocodingResultDto.toDomain(): CityPlace? {
    if (featureCode != null && !featureCode.startsWith("PPL")) return null
    val code = countryCode?.takeIf { it.length == 2 }?.uppercase() ?: return null
    return CityPlace(
        id = "geonames:$id",
        name = name,
        countryCode = code,
        location = GeoPoint(latitude, longitude),
        timeZone = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC,
        country = country,
        region = admin1?.takeIf { !it.equals(name, ignoreCase = true) },
        population = population,
    )
}
