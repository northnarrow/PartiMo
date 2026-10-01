package com.partimo.data.demo

import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.Money
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.flight.FlightSlice
import com.partimo.domain.model.transit.TransitLeg
import com.partimo.domain.model.transit.TransitLine
import com.partimo.domain.model.transit.TransitMode
import com.partimo.domain.model.transit.TransitRoute
import com.partimo.domain.model.transit.TransitRouteQuery
import com.partimo.domain.model.transit.TransitStop
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Catalogo dimostrativo usato quando una chiave API non è configurata. Funziona per qualunque città:
 * - voli: durata e prezzo dipendono dalla distanza reale tra gli aeroporti ([airportLocator]);
 * - trasporti: percorsi generici dall'aeroporto al centro, con durate proporzionali alla distanza.
 *
 * Luoghi, ristoranti e alloggi non hanno una versione demo: arrivano sempre da fonti reali senza
 * chiave (Wikipedia e OpenStreetMap). I prezzi dei voli seguono un mercato simulato ([marketFactor]):
 * cambiano nel tempo e ogni tanto compare un'offerta last minute.
 */
internal class DemoCatalog(
    private val airportLocator: suspend (String) -> GeoPoint? = { null },
    private val clock: Clock = Clock.systemUTC(),
) {

    suspend fun flights(query: FlightSearchQuery): List<FlightOffer> {
        val origin = airportLocator(query.originIata)
        val destination = airportLocator(query.destinationIata)
        val distanceKm = if (origin != null && destination != null) origin.distanceTo(destination) / METERS_PER_KM else DEFAULT_FLIGHT_KM
        val cityFactor = priceFactor(query.destinationIata)
        // Il ritorno costa circa quanto l'andata; per un solo tratto si applica una tariffa ridotta.
        val tripFactor = if (query.returnDate != null) 1.0 else ONE_WAY_FACTOR

        return FlightBand.of(distanceKm).templates.map { template ->
            val minutes = template.durationMinutes(distanceKm)
            val outbound = template.slice(query.originIata, query.destinationIata, query.departureDate, template.outboundTime, minutes)
            val inbound = query.returnDate?.let {
                template.slice(query.destinationIata, query.originIata, it, template.returnTime, minutes)
            }
            val market = marketFactor("flight|${template.carrierIata}|${query.originIata}|${query.destinationIata}|${query.departureDate}")
            val passengers = query.travellers.seatedPassengers
            val fare = (template.baseFare + distanceKm * template.farePerKm) * cityFactor * tripFactor * market * passengers
            FlightOffer(
                id = "demo-${template.carrierIata.lowercase()}-${query.destinationIata.lowercase()}-${query.departureDate}",
                carrierName = template.carrierName,
                carrierIata = template.carrierIata,
                totalPrice = Money.of(BigDecimal(fare).setScale(0, RoundingMode.HALF_UP), "EUR"),
                slices = listOfNotNull(outbound, inbound),
                co2EmissionsKg = (distanceKm * CO2_KG_PER_KM * passengers * (if (inbound != null) 2 else 1)).toInt(),
                refundable = template.refundable,
                passengers = passengers,
            )
        }
    }

    /** Percorsi generici dal nodo di arrivo (aeroporto) al centro, con orari a partire da adesso. */
    fun transitRoutes(query: TransitRouteQuery): List<TransitRoute> {
        val distanceKm = query.origin.distanceTo(query.destination) / METERS_PER_KM
        val trainMinutes = (TRAIN_BASE_MINUTES + distanceKm * TRAIN_MINUTES_PER_KM).roundToLong()
        val busMinutes = (BUS_BASE_MINUTES + distanceKm * BUS_MINUTES_PER_KM).roundToLong()
        val metroMinutes = (METRO_BASE_MINUTES + distanceKm * METRO_MINUTES_PER_KM).roundToLong()
        val start = query.departureTime
        return listOf(
            route(
                start,
                walk(minutes = 5),
                ride(TransitMode.TRAIN, AIRPORT_TRAIN, "Stazione Centrale", AIRPORT_STOP, CENTRAL_STATION_STOP, waitMinutes = 4, minutes = trainMinutes, stops = 2),
                walk(minutes = 4),
                ride(TransitMode.METRO, METRO_1, "Centro", CENTRAL_STATION_STOP, CITY_CENTER_STOP, waitMinutes = 3, minutes = 4, stops = 2),
                walk(minutes = 3),
            ),
            route(
                start,
                walk(minutes = 3),
                ride(TransitMode.BUS, AIRPORT_BUS, "Centro", AIRPORT_STOP, CITY_CENTER_STOP, waitMinutes = 6, minutes = busMinutes, stops = 8),
                walk(minutes = 5),
            ),
            route(
                start,
                walk(minutes = 6),
                ride(TransitMode.METRO, METRO_2, "Centro", AIRPORT_STOP, CITY_CENTER_STOP, waitMinutes = 2, minutes = metroMinutes, stops = 12),
                walk(minutes = 4),
            ),
        )
    }

    /**
     * Mercato simulato: ogni [PRICE_SLOT_MINUTES] minuti i prezzi oscillano (±8%) e circa un'offerta su
     * [FLASH_SALE_ODDS] va in promozione last minute (−30%). Il valore è deterministico all'interno
     * dell'intervallo: "Aggiorna" trova prezzi nuovi solo quando il mercato si è mosso, come nella realtà.
     */
    private fun marketFactor(offerKey: String): Double {
        val slot = clock.millis() / Duration.ofMinutes(PRICE_SLOT_MINUTES).toMillis()
        val hash = "$offerKey#$slot".hashCode().let { if (it == Int.MIN_VALUE) 0 else abs(it) }
        val variation = (hash % VARIATION_STEPS - VARIATION_STEPS / 2) / 100.0
        val flashSale = (hash / VARIATION_STEPS) % FLASH_SALE_ODDS == 0
        return (1 + variation) * (if (flashSale) FLASH_SALE_FACTOR else 1.0)
    }

    /** Variazione di prezzo deterministica per destinazione (±15%), per rendere la demo più realistica. */
    private fun priceFactor(iata: String): Double = 0.9 + (abs(iata.uppercase().hashCode()) % 26) / 100.0

    // ---- Voli -------------------------------------------------------------------------------------

    private enum class FlightBand(val templates: List<FlightTemplate>) {
        SHORT(SHORT_HAUL),
        MEDIUM(MEDIUM_HAUL),
        LONG(LONG_HAUL),
        ;

        companion object {
            fun of(distanceKm: Double): FlightBand = when {
                distanceKm < SHORT_HAUL_MAX_KM -> SHORT
                distanceKm < MEDIUM_HAUL_MAX_KM -> MEDIUM
                else -> LONG
            }
        }
    }

    private data class FlightTemplate(
        val carrierName: String,
        val carrierIata: String,
        val flightNumber: Int,
        val stops: Int,
        val baseFare: Double,
        val farePerKm: Double,
        val outboundTime: LocalTime,
        val returnTime: LocalTime,
        val refundable: Boolean,
    ) {
        /** Crociera a ~820 km/h più rullaggio; uno scalo aggiunge percorso e attesa. */
        fun durationMinutes(distanceKm: Double): Long {
            val direct = TAXI_MINUTES + distanceKm / CRUISE_SPEED_KMH * 60
            return if (stops == 0) direct.roundToLong() else (direct * DETOUR_FACTOR + LAYOVER_MINUTES * stops).roundToLong()
        }

        fun slice(origin: String, destination: String, date: LocalDate, time: LocalTime, minutes: Long): FlightSlice {
            val departure = date.atTime(time)
            return FlightSlice(
                originIata = origin,
                destinationIata = destination,
                departureTime = departure,
                arrivalTime = departure.plusMinutes(minutes),
                duration = Duration.ofMinutes(minutes),
                stops = stops,
                flightNumbers = listOf("$carrierIata$flightNumber"),
            )
        }
    }

    // ---- Trasporti --------------------------------------------------------------------------------

    private sealed interface LegSpec

    private data class WalkSpec(val minutes: Long) : LegSpec

    private data class RideSpec(
        val mode: TransitMode,
        val line: TransitLine,
        val headsign: String,
        val from: String,
        val to: String,
        val waitMinutes: Long,
        val minutes: Long,
        val stops: Int,
    ) : LegSpec

    private fun walk(minutes: Long) = WalkSpec(minutes)

    @Suppress("LongParameterList")
    private fun ride(
        mode: TransitMode,
        line: TransitLine,
        headsign: String,
        from: String,
        to: String,
        waitMinutes: Long,
        minutes: Long,
        stops: Int,
    ) = RideSpec(mode, line, headsign, from, to, waitMinutes, minutes, stops)

    private fun route(start: Instant, vararg specs: LegSpec): TransitRoute {
        var cursor = start
        val legs = specs.map { spec ->
            when (spec) {
                is WalkSpec -> TransitLeg(
                    mode = TransitMode.WALK,
                    departureTime = cursor,
                    arrivalTime = cursor.plus(Duration.ofMinutes(spec.minutes)),
                    distanceMeters = (spec.minutes * WALKING_METERS_PER_MINUTE).toInt(),
                )

                is RideSpec -> {
                    val departure = cursor.plus(Duration.ofMinutes(spec.waitMinutes))
                    TransitLeg(
                        mode = spec.mode,
                        departureTime = departure,
                        arrivalTime = departure.plus(Duration.ofMinutes(spec.minutes)),
                        departureStop = TransitStop(spec.from),
                        arrivalStop = TransitStop(spec.to),
                        line = spec.line,
                        headsign = spec.headsign,
                        stopCount = spec.stops,
                    )
                }
            }.also { leg -> cursor = leg.arrivalTime }
        }
        return TransitRoute(legs)
    }

    // ---- Modelli dei dati generici ------------------------------------------------------------------

    private companion object {
        const val METERS_PER_KM = 1_000.0
        const val DEFAULT_FLIGHT_KM = 1_000.0
        const val SHORT_HAUL_MAX_KM = 1_500.0
        const val MEDIUM_HAUL_MAX_KM = 4_500.0
        const val CRUISE_SPEED_KMH = 820.0
        const val TAXI_MINUTES = 30.0
        const val DETOUR_FACTOR = 1.12
        const val LAYOVER_MINUTES = 95.0
        const val ONE_WAY_FACTOR = 0.6
        const val PRICE_SLOT_MINUTES = 10L

        /** 17 passi = variazione da −8% a +8%. */
        const val VARIATION_STEPS = 17
        const val FLASH_SALE_ODDS = 8
        const val FLASH_SALE_FACTOR = 0.7
        const val CO2_KG_PER_KM = 0.09
        const val WALKING_METERS_PER_MINUTE = 80
        const val TRAIN_BASE_MINUTES = 8.0
        const val TRAIN_MINUTES_PER_KM = 0.9
        const val BUS_BASE_MINUTES = 12.0
        const val BUS_MINUTES_PER_KM = 1.8
        const val METRO_BASE_MINUTES = 10.0
        const val METRO_MINUTES_PER_KM = 1.3

        const val AIRPORT_STOP = "Aeroporto"
        const val CENTRAL_STATION_STOP = "Stazione Centrale"
        const val CITY_CENTER_STOP = "Centro"

        val AIRPORT_TRAIN = TransitLine(name = "Treno aeroportuale", shortName = "Airport Express", colorHex = "#0072BC", textColorHex = "#FFFFFF")
        val AIRPORT_BUS = TransitLine(name = "Bus navetta aeroporto", shortName = "Bus 100", colorHex = "#F9A825", textColorHex = "#000000")
        val METRO_1 = TransitLine(name = "Metro linea 1", shortName = "M1", colorHex = "#E3000F", textColorHex = "#FFFFFF")
        val METRO_2 = TransitLine(name = "Metro linea 2", shortName = "M2", colorHex = "#2E7D32", textColorHex = "#FFFFFF")

        val SHORT_HAUL = listOf(
            FlightTemplate("ITA Airways", "AZ", 610, 0, 45.0, 0.11, LocalTime.of(6, 40), LocalTime.of(19, 10), refundable = true),
            FlightTemplate("Wizz Air", "W6", 2881, 0, 20.0, 0.05, LocalTime.of(6, 25), LocalTime.of(21, 55), refundable = false),
            FlightTemplate("easyJet", "U2", 3717, 0, 30.0, 0.06, LocalTime.of(9, 15), LocalTime.of(17, 30), refundable = false),
            FlightTemplate("Lufthansa", "LH", 1869, 1, 60.0, 0.10, LocalTime.of(12, 15), LocalTime.of(17, 5), refundable = true),
            FlightTemplate("Air France", "AF", 1231, 1, 55.0, 0.09, LocalTime.of(14, 30), LocalTime.of(11, 45), refundable = false),
            FlightTemplate("Ryanair", "FR", 4502, 0, 15.0, 0.045, LocalTime.of(17, 50), LocalTime.of(7, 10), refundable = false),
        )

        val MEDIUM_HAUL = listOf(
            FlightTemplate("ITA Airways", "AZ", 830, 0, 110.0, 0.11, LocalTime.of(10, 5), LocalTime.of(15, 40), refundable = true),
            FlightTemplate("Turkish Airlines", "TK", 1874, 1, 90.0, 0.09, LocalTime.of(7, 35), LocalTime.of(13, 20), refundable = true),
            FlightTemplate("Lufthansa", "LH", 1843, 1, 100.0, 0.095, LocalTime.of(12, 50), LocalTime.of(9, 30), refundable = true),
            FlightTemplate("Wizz Air", "W6", 4331, 0, 40.0, 0.06, LocalTime.of(6, 10), LocalTime.of(22, 45), refundable = false),
            FlightTemplate("Air France", "AF", 1567, 1, 95.0, 0.09, LocalTime.of(15, 25), LocalTime.of(11, 5), refundable = false),
            FlightTemplate("Pegasus", "PC", 1212, 1, 50.0, 0.055, LocalTime.of(21, 40), LocalTime.of(4, 55), refundable = false),
        )

        val LONG_HAUL = listOf(
            FlightTemplate("Emirates", "EK", 92, 1, 250.0, 0.055, LocalTime.of(15, 20), LocalTime.of(9, 5), refundable = true),
            FlightTemplate("Qatar Airways", "QR", 128, 1, 240.0, 0.056, LocalTime.of(16, 5), LocalTime.of(1, 40), refundable = true),
            FlightTemplate("Turkish Airlines", "TK", 1876, 1, 200.0, 0.05, LocalTime.of(19, 45), LocalTime.of(6, 30), refundable = false),
            FlightTemplate("ITA Airways", "AZ", 602, 0, 380.0, 0.07, LocalTime.of(10, 25), LocalTime.of(18, 15), refundable = true),
            FlightTemplate("Lufthansa", "LH", 404, 1, 260.0, 0.058, LocalTime.of(11, 50), LocalTime.of(16, 35), refundable = false),
            FlightTemplate("Etihad Airways", "EY", 84, 1, 230.0, 0.054, LocalTime.of(21, 10), LocalTime.of(3, 15), refundable = false),
        )

    }
}
