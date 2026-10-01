package com.partimo.data.remote.duffel

import com.partimo.data.cache.CacheKey
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.network.Fetched
import com.partimo.data.network.NetworkJson
import com.partimo.data.network.mapNotNullSafely
import com.partimo.data.source.FlightOffersDataSource
import com.partimo.data.source.StayOffersDataSource
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.stay.AccommodationOffer
import com.partimo.domain.model.stay.AccommodationSearchQuery
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/**
 * Client minimale di Duffel API v2.
 *
 * Nota di sicurezza: il token Duffel è una credenziale server-side. In produzione le chiamate vanno
 * instradate da un backend proprio (BFF); qui il token resta in `local.properties` per lo sviluppo.
 */
internal class DuffelApi(
    private val client: HttpClient,
    private val accessToken: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    suspend fun createOfferRequest(body: DuffelOfferRequestBody): String =
        client.post("${baseUrl}air/offer_requests") {
            parameter("return_offers", true)
            parameter("supplier_timeout", SUPPLIER_TIMEOUT_MILLIS)
            duffelHeaders()
            setBody(DuffelEnvelope(body))
        }.bodyAsText()

    suspend fun searchStays(body: DuffelStaysSearchBody): String =
        client.post("${baseUrl}stays/search") {
            duffelHeaders()
            setBody(DuffelEnvelope(body))
        }.bodyAsText()

    private fun HttpRequestBuilder.duffelHeaders() {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
        header(VERSION_HEADER, API_VERSION)
        contentType(ContentType.Application.Json)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.duffel.com/"
        const val VERSION_HEADER = "Duffel-Version"
        const val API_VERSION = "v2"
        private const val SUPPLIER_TIMEOUT_MILLIS = 15_000
    }
}

/** Offerte volo da Duffel con cache locale (le offerte scadono lato provider dopo pochi minuti). */
class DuffelFlightDataSource internal constructor(
    private val api: DuffelApi,
    private val cache: ResponseCache,
) : FlightOffersDataSource {

    override suspend fun searchOffers(query: FlightSearchQuery, forceRefresh: Boolean): Fetched<List<FlightOffer>> {
        val body = DuffelOfferRequestBody(
            slices = buildList {
                add(DuffelSliceRequest(query.originIata, query.destinationIata, query.departureDate.toString()))
                query.returnDate?.let { add(DuffelSliceRequest(query.destinationIata, query.originIata, it.toString())) }
            },
            passengers = List(query.travellers.adults) { DuffelPassengerRequest() } +
                query.travellers.childAges.map { age -> DuffelPassengerRequest(type = null, age = age) },
            cabinClass = query.cabinClass.name.lowercase(),
            maxConnections = query.maxConnections,
        )
        return cache.getOrFetch(
            key = CacheKey.of("flights", NetworkJson.encodeToString(DuffelOfferRequestBody.serializer(), body)),
            ttl = CachePolicy.FLIGHTS,
            forceRefresh = forceRefresh,
            fetch = { api.createOfferRequest(body) },
            parse = { response ->
                NetworkJson.decodeFromString(DuffelEnvelope.serializer(DuffelOfferRequestDto.serializer()), response)
                    .data
                    .offers
                    .mapNotNullSafely { it.toDomain() }
            },
        )
    }
}

/** Alloggi da Duffel Stays: ricerca per coordinate e raggio, con la tariffa più conveniente per struttura. */
class DuffelStayDataSource internal constructor(
    private val api: DuffelApi,
    private val cache: ResponseCache,
) : StayOffersDataSource {

    override suspend fun searchStays(query: AccommodationSearchQuery, forceRefresh: Boolean): Fetched<List<AccommodationOffer>> {
        val body = DuffelStaysSearchBody(
            rooms = query.rooms,
            guests = List(query.travellers.adults) { DuffelGuestRequest() } +
                query.travellers.childAges.map { age -> DuffelGuestRequest(type = "child", age = age) },
            checkInDate = query.checkIn.toString(),
            checkOutDate = query.checkOut.toString(),
            location = DuffelStaysLocation(
                radius = query.radiusKm,
                geographicCoordinates = DuffelCoordinates(query.location.latitude, query.location.longitude),
            ),
        )
        return cache.getOrFetch(
            key = CacheKey.of("stays", NetworkJson.encodeToString(DuffelStaysSearchBody.serializer(), body)),
            ttl = CachePolicy.STAYS,
            forceRefresh = forceRefresh,
            fetch = { api.searchStays(body) },
            parse = { response ->
                NetworkJson.decodeFromString(DuffelEnvelope.serializer(DuffelStaysSearchResultDto.serializer()), response)
                    .data
                    .results
                    .mapNotNullSafely { it.toDomain(query.nights) }
            },
        )
    }
}
