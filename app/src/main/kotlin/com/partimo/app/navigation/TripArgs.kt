package com.partimo.app.navigation

import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.TripContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Viaggio aperto dalla dashboard nelle schermate dell'assistente: destinazione e date esatte, così
 * itinerario e domande riguardano proprio il viaggio mostrato. Viaggia nella rotta come JSON.
 */
@Serializable
data class TripArgs(
    val cityName: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
    val airportIata: String,
    val airportName: String,
    val airportLatitude: Double,
    val airportLongitude: Double,
    /** Date ISO (es. "2026-12-10"). */
    val from: String,
    val to: String,
    /** Chiave del [TravelPeriod] della dashboard: identifica il viaggio tra quelli salvati. */
    val period: String? = null,
) {
    fun destination(): Destination = Destination(
        name = cityName,
        countryCode = countryCode,
        airportIata = airportIata,
        center = GeoPoint(latitude, longitude),
        arrivalHub = GeoPoint(airportLatitude, airportLongitude),
        arrivalHubName = airportName,
        timeZone = runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneOffset.UTC),
    )

    fun fromDate(): LocalDate = LocalDate.parse(from)

    fun toDate(): LocalDate = LocalDate.parse(to)

    fun travelPeriod(): TravelPeriod? = period?.let(TravelPeriod::fromKey)

    fun toJson(): String = ArgsJson.encodeToString(serializer(), this)

    companion object {
        private val ArgsJson = Json { ignoreUnknownKeys = true }

        fun from(trip: TripContext, period: TravelPeriod? = null): TripArgs {
            val destination = trip.destination
            return TripArgs(
                cityName = destination.name,
                countryCode = destination.countryCode,
                latitude = destination.center.latitude,
                longitude = destination.center.longitude,
                timeZone = destination.timeZone.id,
                airportIata = destination.airportIata,
                airportName = destination.arrivalHubName,
                airportLatitude = destination.arrivalHub.latitude,
                airportLongitude = destination.arrivalHub.longitude,
                from = trip.departureDate.toString(),
                to = trip.returnDate.toString(),
                period = period?.key,
            )
        }

        /** `null` se il testo non descrive un viaggio valido. */
        fun fromJson(json: String): TripArgs? = runCatching {
            ArgsJson.decodeFromString(serializer(), json).also { args ->
                require(!args.toDate().isBefore(args.fromDate()))
                args.destination()
            }
        }.getOrNull()
    }
}

/** Itinerario giorno per giorno proposto dall'assistente. [trip] è un [TripArgs] in JSON. */
@Serializable
data class ItineraryDestination(val trip: String)

/** "Chiedi a PartiMo": domande all'assistente sul viaggio. [trip] è un [TripArgs] in JSON. */
@Serializable
data class AssistantChatDestination(val trip: String)

/** Guida del viaggio: meteo, paese, valuta, emergenze e Wikivoyage. [trip] è un [TripArgs] in JSON. */
@Serializable
data class GuideDestination(val trip: String)

/** Preferiti di un viaggio salvato. [trip] è un [TripArgs] in JSON, con il periodo. */
@Serializable
data class FavoritesDestination(val trip: String)

/**
 * Mappa del viaggio con luoghi, eventi, ristoranti e alloggi. [trip] è un [TripArgs] in JSON;
 * [favoritesOnly] la apre con i soli preferiti (dalla schermata dei preferiti).
 */
@Serializable
data class MapDestination(val trip: String, val favoritesOnly: Boolean = false)

/** Traduttore del viaggio, con la lingua del paese della meta. [trip] è un [TripArgs] in JSON. */
@Serializable
data class TranslatorDestination(val trip: String)

/** Budget e spese del viaggio. [trip] è un [TripArgs] in JSON, con il periodo. */
@Serializable
data class BudgetDestination(val trip: String)
