package com.partimo.data.demo

import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.Money
import com.partimo.domain.model.dining.PriceLevel
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.dining.RestaurantSearchQuery
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.flight.FlightSlice
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiQuery
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.poi.Season
import com.partimo.domain.model.stay.AccommodationOffer
import com.partimo.domain.model.stay.AccommodationSearchQuery
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
import java.time.Month
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Catalogo dimostrativo usato quando una chiave API non è configurata. Funziona per qualunque città:
 * - voli: durata e prezzo dipendono dalla distanza reale tra gli aeroporti ([airportLocator]);
 * - alloggi e ristoranti: nomi di fantasia, posizionati attorno al centro richiesto;
 * - luoghi: luoghi reali per Vienna, generici (con eventi stagionali) per le altre città;
 * - trasporti: percorsi generici dall'aeroporto al centro, con durate proporzionali alla distanza.
 *
 * Il catalogo include anche elementi che i casi d'uso devono scartare (fuori stagione, fuori
 * budget), così la demo mostra i filtri di dominio in azione. I prezzi di voli e alloggi seguono un
 * mercato simulato ([marketFactor]): cambiano nel tempo e ogni tanto compare un'offerta last minute.
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
            val fare = (template.baseFare + distanceKm * template.farePerKm) * cityFactor * tripFactor * market * query.adults
            FlightOffer(
                id = "demo-${template.carrierIata.lowercase()}-${query.destinationIata.lowercase()}-${query.departureDate}",
                carrierName = template.carrierName,
                carrierIata = template.carrierIata,
                totalPrice = Money.of(BigDecimal(fare).setScale(0, RoundingMode.HALF_UP), "EUR"),
                slices = listOfNotNull(outbound, inbound),
                co2EmissionsKg = (distanceKm * CO2_KG_PER_KM * query.adults * (if (inbound != null) 2 else 1)).toInt(),
                refundable = template.refundable,
            )
        }
    }

    fun stays(query: AccommodationSearchQuery): List<AccommodationOffer> {
        val nights = query.nights.coerceAtLeast(1)
        val area = "%.2f,%.2f".format(Locale.ROOT, query.location.latitude, query.location.longitude)
        return STAY_TEMPLATES.map { stay ->
            val nightly = stay.nightlyPrice.multiply(BigDecimal(marketFactor("stay|${stay.id}|$area|${query.checkIn}")))
                .setScale(0, RoundingMode.HALF_UP)
            AccommodationOffer(
                id = "demo-${stay.id}",
                name = stay.name,
                totalPrice = Money.of(nightly * BigDecimal(nights * query.rooms), "EUR"),
                nights = nights,
                starRating = stay.stars,
                reviewScore = stay.reviewScore,
                reviewCount = stay.reviewCount,
                location = query.location.offset(stay.offsetLat, stay.offsetLon),
                address = stay.address,
                photoUrl = demoPhoto(stay.id),
                freeCancellation = stay.freeCancellation,
            )
        }
    }

    /** Luoghi reali per Vienna; per le altre città luoghi generici attorno al centro. */
    fun pointsOfInterest(query: PoiQuery): List<PointOfInterest> =
        if (query.location.distanceTo(VIENNA_CENTER) <= CURATED_CITY_RADIUS_METERS) {
            VIENNA_POIS
        } else {
            genericPointsOfInterest(query.location)
        }

    fun restaurants(query: RestaurantSearchQuery): List<Restaurant> = RESTAURANT_TEMPLATES.map { template ->
        Restaurant(
            id = "demo-${template.id}",
            name = template.name,
            priceLevel = template.priceLevel,
            rating = template.rating,
            reviewCount = template.reviewCount,
            cuisine = template.cuisine,
            address = template.address,
            location = query.location.offset(template.offsetLat, template.offsetLon),
            photoUrl = demoPhoto(template.id),
            isOpenNow = true,
        )
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

    private fun genericPointsOfInterest(center: GeoPoint): List<PointOfInterest> {
        val hemisphere = center.hemisphere
        return GENERIC_POIS.map { template ->
            val activeMonths = when (template.season) {
                null -> template.activeMonths
                else -> template.season.months(hemisphere)
            }
            PointOfInterest(
                id = "demo-${template.id}",
                name = template.name,
                category = template.category,
                location = center.offset(template.offsetLat, template.offsetLon),
                rating = template.rating,
                reviewCount = template.reviewCount,
                description = template.description,
                photoUrl = demoPhoto(template.id),
                activeMonths = activeMonths,
            )
        }
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

    private data class StayTemplate(
        val id: String,
        val name: String,
        val stars: Int,
        val reviewScore: Double,
        val reviewCount: Int,
        val nightlyPrice: BigDecimal,
        val offsetLat: Double,
        val offsetLon: Double,
        val address: String,
        val freeCancellation: Boolean,
    )

    private data class RestaurantTemplate(
        val id: String,
        val name: String,
        val priceLevel: PriceLevel,
        val rating: Double,
        val reviewCount: Int,
        val cuisine: String,
        val offsetLat: Double,
        val offsetLon: Double,
        val address: String = "Centro città",
    )

    private data class PoiTemplate(
        val id: String,
        val name: String,
        val category: PoiCategory,
        val rating: Double,
        val reviewCount: Int,
        val description: String,
        val offsetLat: Double,
        val offsetLon: Double,
        /** Mesi fissi di calendario (es. Natale), validi in entrambi gli emisferi. */
        val activeMonths: Set<Month> = emptySet(),
        /** Stagione astronomica: i mesi dipendono dall'emisfero della città. */
        val season: Season? = null,
    )

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
        const val CURATED_CITY_RADIUS_METERS = 40_000.0

        const val AIRPORT_STOP = "Aeroporto"
        const val CENTRAL_STATION_STOP = "Stazione Centrale"
        const val CITY_CENTER_STOP = "Centro"

        val VIENNA_CENTER = GeoPoint(48.2082, 16.3738)

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

        val STAY_TEMPLATES = listOf(
            StayTemplate("hotel-centrale", "Hotel Centrale", 4, 8.7, 1_240, BigDecimal("142"), 0.004, -0.003, "Viale principale 7", freeCancellation = true),
            StayTemplate("bb-piazza", "B&B La Piazza", 3, 9.1, 410, BigDecimal("96"), -0.006, -0.004, "Piazza del mercato 4", freeCancellation = true),
            StayTemplate("grand-palazzo", "Grand Hotel Palazzo", 5, 9.3, 2_100, BigDecimal("390"), -0.002, 0.003, "Corso monumentale 16", freeCancellation = false),
            StayTemplate("design-hostel", "Design Hostel", 2, 8.2, 980, BigDecimal("48"), 0.007, 0.005, "Via dei canali 23", freeCancellation = true),
            StayTemplate("loft-centro", "Appartamento Loft in Centro", 3, 8.9, 156, BigDecimal("118"), -0.01, -0.012, "Vicolo degli artisti 12", freeCancellation = false),
            StayTemplate("boutique-giardino", "Boutique Hotel Giardino", 4, 9.0, 640, BigDecimal("168"), -0.015, 0.007, "Viale dei giardini 9", freeCancellation = true),
        )

        val RESTAURANT_TEMPLATES = listOf(
            RestaurantTemplate("trattoria-centro", "Trattoria del Centro", PriceLevel.MODERATE, 4.6, 1_830, "Cucina tradizionale", 0.001, -0.004),
            RestaurantTemplate("street-food-mercato", "Street Food del Mercato", PriceLevel.INEXPENSIVE, 4.4, 920, "Street food", 0.003, 0.001),
            RestaurantTemplate("bistrot-piazza", "Bistrot della Piazza", PriceLevel.MODERATE, 4.3, 310, "Bistrot", -0.001, -0.007),
            RestaurantTemplate("caffe-storico", "Caffè Storico", PriceLevel.MODERATE, 4.2, 2_400, "Caffetteria", -0.012, -0.005),
            RestaurantTemplate("ristorante-panorama", "Ristorante Panorama", PriceLevel.VERY_EXPENSIVE, 4.8, 1_100, "Alta cucina", -0.003, 0.005),
            RestaurantTemplate("chiosco-quartiere", "Chiosco di Quartiere", PriceLevel.INEXPENSIVE, 4.7, 640, "Cucina di strada", -0.01, -0.01),
            RestaurantTemplate("osteria-locale", "Osteria Locale", PriceLevel.MODERATE, 4.5, 2_950, "Cucina tipica", 0.008, 0.003),
            RestaurantTemplate("sapori-di-mare", "Sapori di Mare", PriceLevel.EXPENSIVE, 4.5, 780, "Pesce", 0.012, 0.016),
            RestaurantTemplate("tavola-calda", "Tavola Calda Express", PriceLevel.INEXPENSIVE, 4.1, 410, "Cucina veloce", -0.015, -0.003),
            RestaurantTemplate("cucina-di-casa", "Cucina di Casa", PriceLevel.MODERATE, 4.6, 35, "Cucina casalinga", 0.05, -0.026),
        )

        val GENERIC_POIS = listOf(
            PoiTemplate("centro-storico", "Centro storico", PoiCategory.NEIGHBORHOOD, 4.6, 12_000,
                "Il cuore della città: piazze, vicoli e palazzi storici da scoprire a piedi.", 0.001, 0.001),
            PoiTemplate("belvedere", "Belvedere panoramico", PoiCategory.VIEWPOINT, 4.7, 8_500,
                "Il punto più panoramico sullo skyline, magico al tramonto.", 0.02, -0.03),
            PoiTemplate("museo", "Museo d'arte e storia", PoiCategory.MUSEUM, 4.6, 15_000,
                "Le collezioni più importanti della città, ideali anche con il maltempo.", -0.004, -0.012),
            PoiTemplate("mercato-coperto", "Mercato coperto", PoiCategory.MARKET, 4.4, 9_000,
                "Prodotti locali e street food: la cucina del posto in un solo luogo.", -0.01, -0.011),
            PoiTemplate("parco", "Grande parco urbano", PoiCategory.PARK, 4.6, 7_000,
                "Il polmone verde della città, perfetto per passeggiate e picnic.", 0.001, 0.04),
            PoiTemplate("cattedrale", "Cattedrale", PoiCategory.RELIGIOUS_SITE, 4.7, 20_000,
                "Il principale luogo di culto, con interni monumentali e una torre panoramica.", 0.0, -0.001),
            PoiTemplate("lungofiume", "Passeggiata sull'acqua", PoiCategory.NEIGHBORHOOD, 4.5, 5_000,
                "Una passeggiata panoramica tra ponti, locali e scorci da fotografare.", 0.009, 0.02),
            PoiTemplate("mercatino-natale", "Mercatino di Natale", PoiCategory.SEASONAL_EVENT, 4.5, 6_000,
                "Luci, bancarelle e dolci tipici nella piazza principale.", 0.002, -0.015,
                activeMonths = setOf(Month.NOVEMBER, Month.DECEMBER)),
            PoiTemplate("festival-estivo", "Festival estivo all'aperto", PoiCategory.SEASONAL_EVENT, 4.4, 3_000,
                "Concerti e spettacoli serali nei parchi della città.", -0.02, 0.01, season = Season.SUMMER),
            PoiTemplate("giardino-fiorito", "Giardino botanico in fiore", PoiCategory.PARK, 4.6, 4_000,
                "Fioriture spettacolari: il momento migliore per visitarlo.", 0.015, 0.006, season = Season.SPRING),
        )

        val VIENNA_POIS = listOf(
            poi("christkindlmarkt", "Wiener Christkindlmarkt al Rathausplatz", PoiCategory.SEASONAL_EVENT, 48.2108, 16.3573, 4.5, 48_000,
                "Il più celebre mercatino di Natale di Vienna, davanti al municipio illuminato.",
                activeMonths = setOf(Month.NOVEMBER, Month.DECEMBER)),
            poi("schoenbrunn-xmas", "Mercatino di Natale di Schönbrunn", PoiCategory.SEASONAL_EVENT, 48.1845, 16.3122, 4.6, 21_000,
                "Bancarelle artigianali e concerti davanti alla reggia imperiale.",
                activeMonths = setOf(Month.NOVEMBER, Month.DECEMBER, Month.JANUARY)),
            poi("eistraum", "Wiener Eistraum: pattinaggio al Rathausplatz", PoiCategory.SEASONAL_EVENT, 48.2106, 16.3580, 4.4, 9_500,
                "Piste di pattinaggio sul ghiaccio tra gli alberi illuminati del Rathauspark.",
                activeMonths = setOf(Month.JANUARY, Month.FEBRUARY, Month.MARCH)),
            poi("donauinsel", "Donauinsel: spiagge sul Danubio", PoiCategory.BEACH, 48.2296, 16.4125, 4.5, 15_000,
                "Isola di 21 km con spiagge libere, piste ciclabili e tramonti sul fiume."),
            poi("rosengarten", "Giardino delle rose del Volksgarten", PoiCategory.PARK, 48.2087, 16.3620, 4.6, 8_000,
                "Oltre 3.000 rose in fiore nel parco di fronte all'Hofburg.",
                activeMonths = setOf(Month.MAY, Month.JUNE, Month.JULY, Month.AUGUST, Month.SEPTEMBER)),
            poi("kahlenberg", "Kahlenberg", PoiCategory.VIEWPOINT, 48.2767, 16.3339, 4.6, 9_800,
                "Belvedere sul Bosco Viennese con vista su tutta la città e sul Danubio."),
            poi("riesenrad", "Riesenrad, la ruota panoramica del Prater", PoiCategory.ATTRACTION, 48.2166, 16.3958, 4.4, 52_000,
                "La storica ruota panoramica del 1897, simbolo del Prater."),
            poi("hundertwasserhaus", "Hundertwasserhaus", PoiCategory.MONUMENT, 48.2071, 16.3942, 4.5, 60_000,
                "Facciate colorate e forme irregolari: uno degli edifici più fotografati di Vienna."),
            poi("schoenbrunn", "Reggia di Schönbrunn", PoiCategory.MONUMENT, 48.1845, 16.3122, 4.7, 160_000,
                "Residenza degli Asburgo: sale imperiali, giardini barocchi e la Gloriette panoramica.",
                isIndoor = true),
            poi("khm", "Kunsthistorisches Museum", PoiCategory.MUSEUM, 48.2038, 16.3616, 4.8, 45_000,
                "Una delle grandi pinacoteche del mondo: Bruegel, Vermeer, Caravaggio."),
            poi("belvedere", "Belvedere Superiore", PoiCategory.MUSEUM, 48.1915, 16.3809, 4.7, 38_000,
                "Palazzo barocco che custodisce Il Bacio di Klimt."),
            poi("stephansdom", "Duomo di Santo Stefano", PoiCategory.RELIGIOUS_SITE, 48.2085, 16.3731, 4.8, 95_000,
                "Cattedrale gotica nel cuore della città; dalla torre sud si gode un panorama unico."),
            poi("naschmarkt", "Naschmarkt", PoiCategory.MARKET, 48.1986, 16.3625, 4.3, 40_000,
                "Mercato all'aperto con street food, spezie e bancarelle da tutto il mondo."),
            poi("donauturm", "Donauturm", PoiCategory.VIEWPOINT, 48.2402, 16.4103, 4.5, 18_000,
                "Torre panoramica di 252 metri con ristorante girevole."),
            poi("karlskirche", "Karlskirche", PoiCategory.RELIGIOUS_SITE, 48.1982, 16.3718, 4.7, 30_000,
                "Chiesa barocca che si specchia nella vasca di Karlsplatz, splendida al tramonto."),
            poi("museumsquartier", "MuseumsQuartier", PoiCategory.NEIGHBORHOOD, 48.2033, 16.3584, 4.6, 25_000,
                "Cortili, musei e locali: il quartiere culturale più vivace della città."),
        )

        @Suppress("LongParameterList")
        fun poi(
            id: String,
            name: String,
            category: PoiCategory,
            latitude: Double,
            longitude: Double,
            rating: Double,
            reviewCount: Int,
            description: String,
            activeMonths: Set<Month> = emptySet(),
            isIndoor: Boolean = category.isIndoorByDefault,
        ) = PointOfInterest(
            id = "demo-$id",
            name = name,
            category = category,
            location = GeoPoint(latitude, longitude),
            rating = rating,
            reviewCount = reviewCount,
            description = description,
            photoUrl = demoPhoto(id),
            isIndoor = isIndoor,
            activeMonths = activeMonths,
        )

        /** Spostamento in gradi (limitato per restare entro coordinate valide). */
        fun GeoPoint.offset(deltaLat: Double, deltaLon: Double): GeoPoint = GeoPoint(
            latitude = (latitude + deltaLat).coerceIn(-89.9, 89.9),
            longitude = ((longitude + deltaLon + 540.0) % 360.0) - 180.0,
        )

        /** Immagini segnaposto deterministiche (Lorem Picsum), per mostrare il caricamento con Coil. */
        fun demoPhoto(seed: String): String = "https://picsum.photos/seed/partimo-$seed/640/400"
    }
}
