package com.partimo.data.remote.duffel

import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.Money
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSlice
import com.partimo.domain.model.stay.AccommodationOffer
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.math.roundToInt

internal fun DuffelOfferDto.toDomain(): FlightOffer? {
    val domainSlices = slices.mapNotNull { it.toDomain() }
    if (domainSlices.isEmpty()) return null
    return FlightOffer(
        id = id,
        carrierName = owner?.name ?: owner?.iataCode ?: UNKNOWN_CARRIER,
        carrierIata = owner?.iataCode,
        carrierLogoUrl = owner?.logoSymbolUrl,
        totalPrice = Money.of(totalAmount, totalCurrency),
        slices = domainSlices,
        expiresAt = expiresAt?.let(::parseDuffelInstant),
        co2EmissionsKg = totalEmissionsKg?.toDoubleOrNull()?.roundToInt(),
        refundable = conditions?.refundBeforeDeparture?.allowed,
    )
}

internal fun DuffelSliceDto.toDomain(): FlightSlice? {
    val first = segments.firstOrNull() ?: return null
    val last = segments.last()
    val departure = LocalDateTime.parse(first.departingAt)
    val arrival = LocalDateTime.parse(last.arrivingAt)
    return FlightSlice(
        originIata = origin?.iataCode ?: first.origin?.iataCode.orEmpty(),
        destinationIata = destination?.iataCode ?: last.destination?.iataCode.orEmpty(),
        departureTime = departure,
        arrivalTime = arrival,
        // La durata del provider tiene conto dei fusi orari; la differenza tra orari locali è solo un ripiego.
        duration = duration?.let(Duration::parse) ?: Duration.between(departure, arrival),
        stops = segments.size - 1,
        flightNumbers = segments.mapNotNull { segment ->
            segment.marketingCarrierFlightNumber?.let { number -> segment.marketingCarrier?.iataCode.orEmpty() + number }
        },
    )
}

internal fun DuffelStaysResultDto.toDomain(nights: Int): AccommodationOffer? {
    val amount = cheapestRateTotalAmount ?: return null
    val currency = cheapestRateCurrency ?: return null
    val place = accommodation
    return AccommodationOffer(
        id = id,
        name = place.name,
        totalPrice = Money.of(amount, currency),
        nights = nights,
        starRating = place.rating?.roundToInt(),
        reviewScore = place.reviewScore,
        location = place.location?.geographicCoordinates?.let { runCatching { GeoPoint(it.latitude, it.longitude) }.getOrNull() },
        address = place.location?.address?.let { address ->
            listOfNotNull(address.lineOne, address.cityName).joinToString(", ").ifBlank { null }
        },
        photoUrl = place.photos.firstOrNull()?.url,
    )
}

private const val UNKNOWN_CARRIER = "Compagnia aerea"

private fun parseDuffelInstant(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
