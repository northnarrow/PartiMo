package com.partimo.data.local.preferences

import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.budget.Expense
import com.partimo.domain.model.budget.ExpenseCategory
import com.partimo.domain.model.budget.TripBudget
import com.partimo.domain.model.deal.PricePoint
import com.partimo.domain.model.deal.PriceWatch
import com.partimo.domain.model.place.Airport
import com.partimo.domain.model.place.AirportSize
import com.partimo.domain.model.place.DeparturePoint
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.WikipediaPage
import com.partimo.domain.model.saved.Favorite
import com.partimo.domain.model.saved.FavoriteKind
import com.partimo.domain.model.saved.SavedTrip
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
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

@Serializable
internal data class FavoriteDto(
    val id: String,
    val kind: String,
    val name: String,
    val subtitle: String? = null,
    val location: GeoPointDto? = null,
    val photoUrl: String? = null,
    val url: String? = null,
    val wikipediaLanguage: String? = null,
    val wikipediaTitle: String? = null,
    val category: String? = null,
    val description: String? = null,
)

@Serializable
internal data class SavedTripDto(
    val destination: DestinationDto,
    val period: String,
    val savedAtMillis: Long,
    val favorites: List<FavoriteDto> = emptyList(),
)

@Serializable
internal data class ExpenseDto(
    val id: String,
    val amount: String,
    val currency: String,
    val category: String,
    /** Data ISO (es. "2026-12-11"). */
    val date: String,
    val note: String = "",
)

@Serializable
internal data class TripBudgetDto(
    val limit: String? = null,
    val expenses: List<ExpenseDto> = emptyList(),
)

/** Codifica e decodifica delle preferenze. Un valore illeggibile viene ignorato, mai propagato come crash. */
internal object StoredJson {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val watchListSerializer = ListSerializer(PriceWatchDto.serializer())
    private val tripListSerializer = ListSerializer(SavedTripDto.serializer())

    fun encodeSavedTrips(trips: List<SavedTrip>): String = json.encodeToString(tripListSerializer, trips.map { it.toDto() })

    /** Viaggi e preferiti non più interpretabili vengono scartati uno a uno. */
    fun decodeSavedTrips(value: String?): List<SavedTrip> {
        if (value.isNullOrBlank()) return emptyList()
        val dtos = runCatching { json.decodeFromString(tripListSerializer, value) }.getOrDefault(emptyList())
        return dtos.mapNotNull { dto -> runCatching { dto.toDomain() }.getOrNull() }
    }

    fun encodeDeparture(departure: DeparturePoint): String = json.encodeToString(DeparturePointDto.serializer(), departure.toDto())

    fun decodeDeparture(value: String): DeparturePoint? =
        runCatching { json.decodeFromString(DeparturePointDto.serializer(), value).toDomain() }.getOrNull()

    fun encodeBudget(budget: TripBudget): String = json.encodeToString(TripBudgetDto.serializer(), budget.toDto())

    /** Le spese non più interpretabili vengono scartate una a una; il budget resta. */
    fun decodeBudget(tripId: String, value: String?): TripBudget {
        if (value.isNullOrBlank()) return TripBudget(tripId)
        val dto = runCatching { json.decodeFromString(TripBudgetDto.serializer(), value) }.getOrNull() ?: return TripBudget(tripId)
        return TripBudget(
            tripId = tripId,
            limit = dto.limit?.let { runCatching { BigDecimal(it) }.getOrNull() }?.takeIf { it.signum() > 0 },
            expenses = dto.expenses.mapNotNull { expense -> runCatching { expense.toDomain() }.getOrNull() },
        )
    }

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

private fun Favorite.toDto() = FavoriteDto(
    id = id,
    kind = kind.name,
    name = name,
    subtitle = subtitle,
    location = location?.toDto(),
    photoUrl = photoUrl,
    url = url,
    wikipediaLanguage = wikipediaPage?.language,
    wikipediaTitle = wikipediaPage?.title,
    category = category?.name,
    description = description,
)

private fun FavoriteDto.toDomain(): Favorite? {
    val favoriteKind = FavoriteKind.entries.firstOrNull { it.name == kind } ?: return null
    return Favorite(
        id = id,
        kind = favoriteKind,
        name = name,
        subtitle = subtitle,
        location = location?.let { runCatching { it.toDomain() }.getOrNull() },
        photoUrl = photoUrl,
        url = url,
        wikipediaPage = if (wikipediaLanguage != null && wikipediaTitle != null) WikipediaPage(wikipediaLanguage, wikipediaTitle) else null,
        category = PoiCategory.entries.firstOrNull { it.name == category },
        description = description,
    )
}

private fun SavedTrip.toDto() = SavedTripDto(
    destination = destination.toDto(),
    period = period.key,
    savedAtMillis = savedAt.toEpochMilli(),
    favorites = favorites.map { it.toDto() },
)

private fun SavedTripDto.toDomain() = SavedTrip(
    destination = destination.toDomain(),
    period = requireNotNull(TravelPeriod.fromKey(period)) { "Periodo non valido: $period" },
    savedAt = Instant.ofEpochMilli(savedAtMillis),
    favorites = favorites.mapNotNull { it.toDomain() },
)

private fun TripBudget.toDto() = TripBudgetDto(limit = limit?.toPlainString(), expenses = expenses.map { it.toDto() })

private fun Expense.toDto() = ExpenseDto(
    id = id,
    amount = amount.toPlainString(),
    currency = currency,
    category = category.name,
    date = date.toString(),
    note = note,
)

private fun ExpenseDto.toDomain() = Expense(
    id = id,
    amount = BigDecimal(amount),
    currency = currency,
    category = ExpenseCategory.entries.firstOrNull { it.name == category } ?: ExpenseCategory.OTHER,
    date = LocalDate.parse(date),
    note = note,
)

