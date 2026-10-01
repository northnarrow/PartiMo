package com.partimo.data.remote.duffel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTO di Duffel API v2 (header "Duffel-Version: v2"):
// voli  → https://duffel.com/docs/api/v2/offer-requests
// hotel → https://duffel.com/docs/api/v2/search

/** Duffel incapsula sia le richieste sia le risposte in un oggetto `data`. */
@Serializable
internal data class DuffelEnvelope<T>(val data: T)

// ---- Richieste --------------------------------------------------------------------------------

@Serializable
internal data class DuffelOfferRequestBody(
    val slices: List<DuffelSliceRequest>,
    val passengers: List<DuffelPassengerRequest>,
    @SerialName("cabin_class") val cabinClass: String,
    @SerialName("max_connections") val maxConnections: Int,
)

@Serializable
internal data class DuffelSliceRequest(
    val origin: String,
    val destination: String,
    @SerialName("departure_date") val departureDate: String,
)

/** Passeggero di un volo: un adulto oppure, per i minori, solo l'età (Duffel ne ricava la tariffa). */
@Serializable
internal data class DuffelPassengerRequest(val type: String? = "adult", val age: Int? = null)

/** Ospite di un alloggio: adulto o bambino con l'età. */
@Serializable
internal data class DuffelGuestRequest(val type: String = "adult", val age: Int? = null)

@Serializable
internal data class DuffelStaysSearchBody(
    val rooms: Int,
    val guests: List<DuffelGuestRequest>,
    @SerialName("check_in_date") val checkInDate: String,
    @SerialName("check_out_date") val checkOutDate: String,
    val location: DuffelStaysLocation,
)

@Serializable
internal data class DuffelStaysLocation(
    /** Raggio di ricerca in chilometri. */
    val radius: Int,
    @SerialName("geographic_coordinates") val geographicCoordinates: DuffelCoordinates,
)

@Serializable
internal data class DuffelCoordinates(val latitude: Double, val longitude: Double)

// ---- Risposte voli ----------------------------------------------------------------------------

@Serializable
internal data class DuffelOfferRequestDto(
    val id: String? = null,
    val offers: List<DuffelOfferDto> = emptyList(),
)

@Serializable
internal data class DuffelOfferDto(
    val id: String,
    @SerialName("total_amount") val totalAmount: String,
    @SerialName("total_currency") val totalCurrency: String,
    val owner: DuffelAirlineDto? = null,
    val slices: List<DuffelSliceDto> = emptyList(),
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("total_emissions_kg") val totalEmissionsKg: String? = null,
    val conditions: DuffelConditionsDto? = null,
)

@Serializable
internal data class DuffelAirlineDto(
    val name: String? = null,
    @SerialName("iata_code") val iataCode: String? = null,
    @SerialName("logo_symbol_url") val logoSymbolUrl: String? = null,
)

@Serializable
internal data class DuffelSliceDto(
    val origin: DuffelPlaceDto? = null,
    val destination: DuffelPlaceDto? = null,
    /** Durata ISO 8601 (es. PT1H35M). */
    val duration: String? = null,
    val segments: List<DuffelSegmentDto> = emptyList(),
)

@Serializable
internal data class DuffelPlaceDto(
    @SerialName("iata_code") val iataCode: String? = null,
    val name: String? = null,
    @SerialName("city_name") val cityName: String? = null,
)

@Serializable
internal data class DuffelSegmentDto(
    /** Orario locale dell'aeroporto, senza offset (es. 2026-12-12T08:35:00). */
    @SerialName("departing_at") val departingAt: String,
    @SerialName("arriving_at") val arrivingAt: String,
    val duration: String? = null,
    val origin: DuffelPlaceDto? = null,
    val destination: DuffelPlaceDto? = null,
    @SerialName("marketing_carrier") val marketingCarrier: DuffelAirlineDto? = null,
    @SerialName("marketing_carrier_flight_number") val marketingCarrierFlightNumber: String? = null,
)

@Serializable
internal data class DuffelConditionsDto(
    @SerialName("refund_before_departure") val refundBeforeDeparture: DuffelConditionDto? = null,
)

@Serializable
internal data class DuffelConditionDto(val allowed: Boolean? = null)

// ---- Risposte alloggi -------------------------------------------------------------------------

@Serializable
internal data class DuffelStaysSearchResultDto(val results: List<DuffelStaysResultDto> = emptyList())

@Serializable
internal data class DuffelStaysResultDto(
    val id: String,
    @SerialName("cheapest_rate_total_amount") val cheapestRateTotalAmount: String? = null,
    @SerialName("cheapest_rate_currency") val cheapestRateCurrency: String? = null,
    val accommodation: DuffelAccommodationDto,
)

@Serializable
internal data class DuffelAccommodationDto(
    val id: String,
    val name: String,
    /** Classificazione in stelle. */
    val rating: Double? = null,
    /** Punteggio recensioni ospiti su scala 0–10. */
    @SerialName("review_score") val reviewScore: Double? = null,
    val photos: List<DuffelPhotoDto> = emptyList(),
    val location: DuffelAccommodationLocationDto? = null,
)

@Serializable
internal data class DuffelPhotoDto(val url: String)

@Serializable
internal data class DuffelAccommodationLocationDto(
    @SerialName("geographic_coordinates") val geographicCoordinates: DuffelCoordinates? = null,
    val address: DuffelAddressDto? = null,
)

@Serializable
internal data class DuffelAddressDto(
    @SerialName("line_one") val lineOne: String? = null,
    @SerialName("city_name") val cityName: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
)
