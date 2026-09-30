package com.partimo.domain.testing

import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.deal.PricePoint
import com.partimo.domain.model.deal.PriceWatch
import com.partimo.domain.model.dining.PriceLevel
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSlice
import com.partimo.domain.model.place.Airport
import com.partimo.domain.model.place.AirportSize
import com.partimo.domain.model.place.CatalogDestination
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.place.DeparturePoint
import com.partimo.domain.model.place.TravelExperience
import com.partimo.domain.model.poi.ArticleSection
import com.partimo.domain.model.poi.ImageCredit
import com.partimo.domain.model.poi.PoiArticle
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.poi.WikipediaPage
import com.partimo.domain.model.stay.AccommodationOffer
import com.partimo.domain.model.stay.Lodging
import com.partimo.domain.model.stay.LodgingType
import com.partimo.domain.model.transit.TransitLeg
import com.partimo.domain.model.transit.TransitLine
import com.partimo.domain.model.transit.TransitMode
import com.partimo.domain.model.transit.TransitRoute
import com.partimo.domain.model.transit.TransitStop
import com.partimo.domain.model.weather.WeatherCondition
import com.partimo.domain.model.weather.WeatherSnapshot
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId

/** Dati e builder deterministici per i test (orologio fisso al 30/09/2026 ore 10:00, Roma). */
object TestData {

    val ZONE: ZoneId = ZoneId.of("Europe/Rome")
    val TODAY: LocalDate = LocalDate.of(2026, Month.SEPTEMBER, 30)
    val NOW: Instant = TODAY.atTime(10, 0).atZone(ZONE).toInstant()
    val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZONE)

    val VIENNA_CENTER = GeoPoint(48.2082, 16.3738)
    val VIENNA_HUB = GeoPoint(48.1852, 16.3761)
    val SYDNEY_CENTER = GeoPoint(-33.8688, 151.2093)

    fun flightOffer(
        id: String,
        price: String,
        durationMinutes: Long = 90,
        stops: Int = 0,
        currency: String = "EUR",
        refundable: Boolean? = null,
        departure: LocalDateTime = LocalDateTime.of(2026, Month.DECEMBER, 12, 8, 0),
    ): FlightOffer = FlightOffer(
        id = id,
        carrierName = "Carrier $id",
        carrierIata = "XX",
        totalPrice = Money.of(price, currency),
        slices = listOf(
            FlightSlice(
                originIata = "MXP",
                destinationIata = "VIE",
                departureTime = departure,
                arrivalTime = departure.plusMinutes(durationMinutes),
                duration = Duration.ofMinutes(durationMinutes),
                stops = stops,
            ),
        ),
        refundable = refundable,
    )

    fun stayOffer(
        id: String,
        totalPrice: String,
        nights: Int = 4,
        reviewScore: Double? = 8.5,
        reviewCount: Int? = 500,
        stars: Int? = 3,
        currency: String = "EUR",
        freeCancellation: Boolean? = null,
    ): AccommodationOffer = AccommodationOffer(
        id = id,
        name = "Hotel $id",
        totalPrice = Money.of(totalPrice, currency),
        nights = nights,
        starRating = stars,
        reviewScore = reviewScore,
        reviewCount = reviewCount,
        freeCancellation = freeCancellation,
    )

    fun poi(
        id: String,
        name: String = id,
        category: PoiCategory = PoiCategory.ATTRACTION,
        rating: Double? = 4.5,
        reviewCount: Int? = 1_000,
        description: String? = null,
        isIndoor: Boolean = category.isIndoorByDefault,
        activeMonths: Set<Month> = emptySet(),
        tags: Set<PoiTag> = emptySet(),
        location: GeoPoint = VIENNA_CENTER,
        photoUrl: String? = null,
        popularity: Double? = null,
        wikipediaPage: WikipediaPage? = null,
    ): PointOfInterest = PointOfInterest(
        id = id,
        name = name,
        category = category,
        location = location,
        rating = rating,
        reviewCount = reviewCount,
        description = description,
        photoUrl = photoUrl,
        isIndoor = isIndoor,
        activeMonths = activeMonths,
        tags = tags,
        popularity = popularity,
        wikipediaPage = wikipediaPage,
    )

    /** Voce enciclopedica di esempio, con introduzione e storia divisa in capitoli. */
    fun article(
        title: String = "Duomo di Santo Stefano",
        introduction: List<String> = listOf(
            "Il duomo di Santo Stefano è la cattedrale di Vienna, capolavoro del gotico austriaco.",
            "Con la sua torre sud di 136 metri domina il centro storico della città.",
        ),
        history: List<ArticleSection> = listOf(
            ArticleSection(title = "Origini", paragraphs = listOf("La prima chiesa fu consacrata nel 1147 fuori dalle mura della città.")),
            ArticleSection(title = "Età moderna", paragraphs = listOf("Durante l'assedio del 1683 la torre sud fu il posto di comando della difesa.")),
        ),
        imageUrl: String? = "https://upload.test/stephansdom.jpg",
        imageCredit: ImageCredit? = ImageCredit(author = "Mario Rossi", license = "CC BY-SA 4.0", sourceUrl = "https://commons.test/File:Stephansdom.jpg"),
        language: String = "it",
    ): PoiArticle = PoiArticle(
        title = title,
        language = language,
        url = "https://$language.wikipedia.org/wiki/" + title.replace(' ', '_'),
        shortDescription = "cattedrale di Vienna",
        introduction = introduction,
        history = history,
        imageUrl = imageUrl,
        imageCredit = imageCredit,
    )

    fun lodging(
        id: String,
        type: LodgingType = LodgingType.HOTEL,
        location: GeoPoint = VIENNA_CENTER,
        stars: Int? = null,
    ): Lodging = Lodging(id = id, name = "Struttura $id", type = type, location = location, starRating = stars)

    fun restaurant(
        id: String,
        priceLevel: PriceLevel?,
        rating: Double?,
        reviewCount: Int? = 200,
        openNow: Boolean? = true,
    ): Restaurant = Restaurant(
        id = id,
        name = "Ristorante $id",
        priceLevel = priceLevel,
        rating = rating,
        reviewCount = reviewCount,
        isOpenNow = openNow,
    )

    fun weather(
        condition: WeatherCondition = WeatherCondition.CLEAR,
        temperature: Double = 18.0,
        precipitationMm: Double = 0.0,
        windKmh: Double = 10.0,
    ): WeatherSnapshot = WeatherSnapshot(
        temperatureCelsius = temperature,
        condition = condition,
        precipitationMm = precipitationMm,
        windSpeedKmh = windKmh,
    )

    fun walk(start: Instant, minutes: Long): TransitLeg = TransitLeg(
        mode = TransitMode.WALK,
        departureTime = start,
        arrivalTime = start.plus(Duration.ofMinutes(minutes)),
    )

    fun ride(
        mode: TransitMode,
        start: Instant,
        minutes: Long,
        line: String = "U1",
        from: String = "Karlsplatz",
        to: String = "Stephansplatz",
    ): TransitLeg = TransitLeg(
        mode = mode,
        departureTime = start,
        arrivalTime = start.plus(Duration.ofMinutes(minutes)),
        departureStop = TransitStop(from),
        arrivalStop = TransitStop(to),
        line = TransitLine(name = line, shortName = line),
        stopCount = 3,
    )

    fun city(
        name: String = "Vienna",
        location: GeoPoint = VIENNA_CENTER,
        countryCode: String = "AT",
        population: Int? = 1_900_000,
        id: String = "city-$name",
        timeZone: ZoneId = ZoneId.of("Europe/Vienna"),
    ): CityPlace = CityPlace(
        id = id,
        name = name,
        countryCode = countryCode,
        location = location,
        timeZone = timeZone,
        country = "Paese di $name",
        population = population,
    )

    fun airport(
        iata: String = "VIE",
        location: GeoPoint = GeoPoint(48.1103, 16.5697),
        size: AirportSize = AirportSize.LARGE,
        name: String = "Aeroporto $iata",
    ): Airport = Airport(
        iata = iata,
        name = name,
        city = null,
        countryCode = "AT",
        location = location,
        size = size,
    )

    fun catalogDestination(
        name: String,
        pleasantMonths: Set<Month> = emptySet(),
        experiences: List<TravelExperience> = emptyList(),
        location: GeoPoint = VIENNA_CENTER,
    ): CatalogDestination = CatalogDestination(
        city = city(name = name, location = location),
        pleasantMonths = pleasantMonths,
        experiences = experiences,
        tagline = "Scopri $name",
    )

    val MALPENSA = GeoPoint(45.6306, 8.7281)
    val DECEMBER_2026: TravelPeriod = TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER))

    fun departure(cityName: String = "Milano", iata: String = "MXP", location: GeoPoint = MALPENSA): DeparturePoint =
        DeparturePoint(cityName = cityName, airport = airport(iata = iata, location = location, size = AirportSize.HUB))

    fun destination(name: String = "Vienna", airportIata: String = "VIE", center: GeoPoint = VIENNA_CENTER): Destination = Destination(
        name = name,
        countryCode = "AT",
        airportIata = airportIata,
        center = center,
        arrivalHub = GeoPoint(48.1103, 16.5697),
        arrivalHubName = "Aeroporto $airportIata",
        timeZone = ZoneId.of("Europe/Vienna"),
    )

    /** Avviso con lo storico dei prezzi indicato (importi in euro, dal più vecchio al più recente). */
    fun priceWatch(
        flightPrices: List<String> = emptyList(),
        stayPrices: List<String> = emptyList(),
        period: TravelPeriod = DECEMBER_2026,
        destination: Destination = destination(),
        departure: DeparturePoint = departure(),
    ): PriceWatch = PriceWatch(
        departure = departure,
        destination = destination,
        period = period,
        createdAt = NOW,
        flightPrices = flightPrices.map { PricePoint(Money.of(it, "EUR"), NOW) },
        stayPrices = stayPrices.map { PricePoint(Money.of(it, "EUR"), NOW) },
    )

    /** Percorso semplice: a piedi → mezzo → a piedi. */
    fun simpleRoute(start: Instant = NOW, mode: TransitMode = TransitMode.METRO, rideMinutes: Long = 10): TransitRoute {
        val first = walk(start, 3)
        val ride = ride(mode, first.arrivalTime.plus(Duration.ofMinutes(2)), rideMinutes)
        return TransitRoute(listOf(first, ride, walk(ride.arrivalTime, 4)))
    }
}
