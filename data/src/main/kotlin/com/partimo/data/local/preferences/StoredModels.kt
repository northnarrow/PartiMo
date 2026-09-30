package com.partimo.data.local.preferences

import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.deal.PricePoint
import com.partimo.domain.model.deal.PriceWatch
import com.partimo.domain.model.place.Airport
import com.partimo.domain.model.place.AirportSize
import com.partimo.domain.model.place.DeparturePoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

// Formato di salvataggio delle preferenze (JSON dentro DataStore). I DTO sono separati dai modelli
// di dominio: il formato su disco può evolvere senza toccare il dominio e i campi nuovi hanno un
// valore predefinito, così i dati salvati dalle versioni precedenti restano leggibili.

@Serializable
internal data class MoneyDto(val amount: String, val currency: String)

@Serializable
internal data class GeoPointDto(val latitude: Double, val longitude: Double)

@Serializable
internal data class AirportDto(
    val iata: String,
    val name: String,
    val city: String? = null,
    val countryCode: String,
    val location: GeoPointDto,
    val size: String,
)

@Serializable
internal data class DeparturePointDto(val cityName: String, val airport: AirportDto)

@Serializable
internal data class DestinationDto(
    val name: String,
    val countryCode: String,
    val airportIata: String,
    val center: GeoPointDto,
    val arrivalHub: GeoPointDto,
    val arrivalHubName: String,
    val timeZone: String,
)

@Serializable
internal data class PricePointDto(val price: MoneyDto, val observedAtMillis: Long)

@Serializable
internal data class PriceWatchDto(
    val departure: DeparturePointDto,
    val destination: DestinationDto,
    val period: String,
    val createdAtMillis: Long,
    val flightPrices: List<PricePointDto> = emptyList(),
    val stayPrices: List<PricePointDto> = emptyList(),
    val lastNotifiedFlight: MoneyDto? = null,
    val lastNotifiedStay: MoneyDto? = null,
)

/** Codifica e decodifica delle preferenze. Un valore illeggibile viene ignorato, mai propagato come crash. */
internal object StoredJson {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val watchListSerializer = ListSerializer(PriceWatchDto.serializer())

    fun encodeDeparture(departure: DeparturePoint): String = json.encodeToString(DeparturePointDto.serializer(), departure.toDto())

    fun decodeDeparture(value: String): DeparturePoint? =
        runCatching { json.decodeFromString(DeparturePointDto.serializer(), value).toDomain() }.getOrNull()

    fun encodeWatches(watches: List<PriceWatch>): String = json.encodeToString(watchListSerializer, watches.map { it.toDto() })

    /** Gli avvisi non più interpretabili (es. periodo sconosciuto) vengono scartati singolarmente. */
    fun decodeWatches(value: String?): List<PriceWatch> {
        if (value.isNullOrBlank()) return emptyList()
        val dtos = runCatching { json.decodeFromString(watchListSerializer, value) }.getOrDefault(emptyList())
        return dtos.mapNotNull { dto -> runCatching { dto.toDomain() }.getOrNull() }
    }
}

// ---- Mapping --------------------------------------------------------------------------------------

private fun Money.toDto() = MoneyDto(amount = amount.toPlainString(), currency = currencyCode)

private fun MoneyDto.toDomain() = Money.of(BigDecimal(amount), currency)

private fun GeoPoint.toDto() = GeoPointDto(latitude, longitude)

private fun GeoPointDto.toDomain() = GeoPoint(latitude, longitude)

private fun Airport.toDto() = AirportDto(iata, name, city, countryCode, location.toDto(), size.name)

private fun AirportDto.toDomain() = Airport(
    iata = iata,
    name = name,
    city = city,
    countryCode = countryCode,
    location = location.toDomain(),
    size = AirportSize.entries.firstOrNull { it.name == size } ?: AirportSize.MEDIUM,
)

private fun DeparturePoint.toDto() = DeparturePointDto(cityName, airport.toDto())

private fun DeparturePointDto.toDomain() = DeparturePoint(cityName, airport.toDomain())

private fun Destination.toDto() = DestinationDto(
    name = name,
    countryCode = countryCode,
    airportIata = airportIata,
    center = center.toDto(),
    arrivalHub = arrivalHub.toDto(),
    arrivalHubName = arrivalHubName,
    timeZone = timeZone.id,
)

private fun DestinationDto.toDomain() = Destination(
    name = name,
    countryCode = countryCode,
    airportIata = airportIata,
    center = center.toDomain(),
    arrivalHub = arrivalHub.toDomain(),
    arrivalHubName = arrivalHubName,
    timeZone = runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneOffset.UTC),
)

private fun PricePoint.toDto() = PricePointDto(price.toDto(), observedAt.toEpochMilli())

private fun PricePointDto.toDomain() = PricePoint(price.toDomain(), Instant.ofEpochMilli(observedAtMillis))

private fun PriceWatch.toDto() = PriceWatchDto(
    departure = departure.toDto(),
    destination = destination.toDto(),
    period = period.key,
    createdAtMillis = createdAt.toEpochMilli(),
    flightPrices = flightPrices.map { it.toDto() },
    stayPrices = stayPrices.map { it.toDto() },
    lastNotifiedFlight = lastNotifiedFlight?.toDto(),
    lastNotifiedStay = lastNotifiedStay?.toDto(),
)

private fun PriceWatchDto.toDomain() = PriceWatch(
    departure = departure.toDomain(),
    destination = destination.toDomain(),
    period = requireNotNull(TravelPeriod.fromKey(period)) { "Periodo non valido: $period" },
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    flightPrices = flightPrices.map { it.toDomain() },
    stayPrices = stayPrices.map { it.toDomain() },
    lastNotifiedFlight = lastNotifiedFlight?.toDomain(),
    lastNotifiedStay = lastNotifiedStay?.toDomain(),
)
