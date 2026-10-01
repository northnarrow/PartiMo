package com.partimo.app.ui.dashboard

import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.chat.ChatUiState
import com.partimo.app.ui.departure.DeparturePickerUiState
import com.partimo.app.ui.itinerary.ItineraryUiState
import com.partimo.app.ui.place.PlaceDetailUiState
import com.partimo.app.ui.place.googleMapsSearchUrl
import com.partimo.app.ui.search.SearchUiState
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.Money
import com.partimo.domain.model.ScoredOffer
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.TripContext
import com.partimo.domain.model.dining.PriceLevel
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.event.EventTiming
import com.partimo.domain.model.event.TripEvent
import com.partimo.domain.model.event.TripEvents
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSlice
import com.partimo.domain.model.place.Airport
import com.partimo.domain.model.place.AirportOption
import com.partimo.domain.model.place.AirportSize
import com.partimo.domain.model.place.CatalogDestination
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.place.DestinationSuggestion
import com.partimo.domain.model.place.TravelExperience
import com.partimo.domain.model.place.TravelTheme
import com.partimo.domain.model.plan.ChatMessage
import com.partimo.domain.model.plan.ChatRole
import com.partimo.domain.model.plan.DayPart
import com.partimo.domain.model.plan.DayPlan
import com.partimo.domain.model.plan.PackingGroup
import com.partimo.domain.model.plan.PlanStop
import com.partimo.domain.model.plan.StopTarget
import com.partimo.domain.model.plan.TripInterest
import com.partimo.domain.model.plan.TripPlan
import com.partimo.domain.model.plan.TripPreferences
import com.partimo.domain.model.poi.HistoryChapter
import com.partimo.domain.model.poi.ImageCredit
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiDetails
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.poi.RecommendationReason
import com.partimo.domain.model.poi.Season
import com.partimo.domain.model.poi.SeasonalHighlights
import com.partimo.domain.model.poi.SeasonalRecommendation
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
import com.partimo.domain.service.SeasonalCalendar
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId

/** Dati statici per le @Preview di Compose e i test UI (nessuna dipendenza dal data layer). */
internal object PreviewData {

    val TODAY: LocalDate = LocalDate.of(2026, Month.SEPTEMBER, 30)
    private val DECEMBER: TravelPeriod = TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER))

    private val trip = TripContext(
        destination = SampleDestinations.VIENNA,
        departureDate = LocalDate.of(2026, Month.DECEMBER, 10),
        returnDate = LocalDate.of(2026, Month.DECEMBER, 14),
        departure = SampleDestinations.MILAN_DEPARTURE,
    )

    private fun slice(from: String, to: String, departure: LocalDateTime, minutes: Long, stops: Int) = FlightSlice(
        originIata = from,
        destinationIata = to,
        departureTime = departure,
        arrivalTime = departure.plusMinutes(minutes),
        duration = Duration.ofMinutes(minutes),
        stops = stops,
    )

    private val flights = listOf(
        ScoredOffer(
            FlightOffer(
                id = "os",
                carrierName = "Austrian Airlines",
                totalPrice = Money.of(168, "EUR"),
                slices = listOf(
                    slice("MXP", "VIE", LocalDateTime.of(2026, 12, 10, 8, 5), 90, 0),
                    slice("VIE", "MXP", LocalDateTime.of(2026, 12, 14, 18, 40), 90, 0),
                ),
                refundable = true,
            ),
            valueScore = 0.91,
        ),
        ScoredOffer(
            FlightOffer(
                id = "az",
                carrierName = "ITA Airways",
                totalPrice = Money.of("142.50", "EUR"),
                slices = listOf(slice("MXP", "VIE", LocalDateTime.of(2026, 12, 10, 10, 40), 245, 1)),
            ),
            valueScore = 0.64,
        ),
    )

    private val stays = listOf(
        ScoredOffer(
            AccommodationOffer(
                id = "mozart",
                name = "Pension Mozartgasse",
                totalPrice = Money.of(384, "EUR"),
                nights = 4,
                starRating = 3,
                reviewScore = 9.1,
                reviewCount = 410,
                freeCancellation = true,
            ),
            valueScore = 0.88,
        ),
        ScoredOffer(
            AccommodationOffer(
                id = "ring",
                name = "Hotel Ringstraße Classic",
                totalPrice = Money.of(568, "EUR"),
                nights = 4,
                starRating = 4,
                reviewScore = 8.7,
                reviewCount = 1_240,
            ),
            valueScore = 0.73,
        ),
    )

    private val highlights = SeasonalHighlights(
        season = Season.WINTER,
        travelMonth = Month.DECEMBER,
        currentWeather = WeatherSnapshot(temperatureCelsius = 7.0, condition = WeatherCondition.PARTLY_CLOUDY),
        weatherConsidered = false,
        recommendations = listOf(
            SeasonalRecommendation(
                poi = PointOfInterest(
                    id = "xmas",
                    name = "Wiener Christkindlmarkt al Rathausplatz",
                    category = PoiCategory.SEASONAL_EVENT,
                    location = GeoPoint(48.2108, 16.3573),
                    rating = 4.5,
                    reviewCount = 48_000,
                    description = "Il più celebre mercatino di Natale di Vienna, davanti al municipio illuminato.",
                    tags = setOf(PoiTag.SEASONAL_HIGHLIGHT, PoiTag.INSTAGRAMMABLE),
                ),
                score = 0.85,
                reasons = setOf(RecommendationReason.IN_SEASON, RecommendationReason.PHOTO_SPOT),
            ),
            SeasonalRecommendation(
                poi = PointOfInterest(
                    id = "kahlenberg",
                    name = "Kahlenberg",
                    category = PoiCategory.VIEWPOINT,
                    location = GeoPoint(48.2767, 16.3339),
                    rating = 4.6,
                    reviewCount = 9_800,
                    description = "Monte del Bosco Viennese con vista su tutta la città e sul Danubio.",
                    tags = setOf(PoiTag.PANORAMIC, PoiTag.INSTAGRAMMABLE, PoiTag.SUNSET_SPOT),
                ),
                score = 0.61,
                reasons = setOf(RecommendationReason.PHOTO_SPOT),
            ),
        ),
    )

    private val transit: List<TransitRoute> = run {
        val start = Instant.parse("2026-12-12T09:00:00Z")
        val walk = TransitLeg(TransitMode.WALK, start, start.plusSeconds(240))
        val metro = TransitLeg(
            mode = TransitMode.METRO,
            departureTime = start.plusSeconds(360),
            arrivalTime = start.plusSeconds(780),
            departureStop = TransitStop("Hauptbahnhof"),
            arrivalStop = TransitStop("Stephansplatz"),
            line = TransitLine(name = "U1", shortName = "U1", colorHex = "#E3000F"),
            stopCount = 4,
        )
        listOf(TransitRoute(listOf(walk, metro, TransitLeg(TransitMode.WALK, metro.arrivalTime, metro.arrivalTime.plusSeconds(120)))))
    }

    private val restaurants = listOf(
        Restaurant("1", "Beisl zum Goldenen Hirschen", PriceLevel.MODERATE, 4.6, 1_830, "Cucina viennese", isOpenNow = true),
        Restaurant("2", "Naschmarkt Falafel Corner", PriceLevel.INEXPENSIVE, 4.7, 640, "Mediorientale"),
    )

    /** Mercatini di Natale di Vienna durante il soggiorno (10–14 dicembre). */
    private val tripEvents = TripEvents(
        from = LocalDate.of(2026, Month.DECEMBER, 10),
        to = LocalDate.of(2026, Month.DECEMBER, 14),
        events = listOf(
            TripEvent(
                id = "wikidata:spittelberg",
                name = "Weihnachtsmarkt am Spittelberg",
                kind = EventKind.CHRISTMAS_MARKET,
                timing = SeasonalCalendar.CHRISTMAS_MARKET_SEASON,
                approximateTiming = true,
                description = "Mercatino di artigianato tra le case Biedermeier del quartiere Spittelberg",
                location = GeoPoint(48.2030, 16.3540),
                venueName = "Spittelberg",
            ),
            TripEvent(
                id = "wikidata:schoenbrunn",
                name = "Weihnachtsmarkt Schloss Schönbrunn",
                kind = EventKind.CHRISTMAS_MARKET,
                timing = SeasonalCalendar.CHRISTMAS_MARKET_SEASON,
                approximateTiming = true,
                location = GeoPoint(48.1849, 16.3122),
                venueName = "Schloss Schönbrunn",
            ),
        ),
    )

    /** Festività nazionale con il nome locale, per anteprime e test della sezione eventi. */
    val immaculateConception = TripEvent(
        id = "holiday:AT:2026-12-08",
        name = "Immacolata Concezione",
        kind = EventKind.PUBLIC_HOLIDAY,
        timing = EventTiming.OnDates(LocalDate.of(2026, Month.DECEMBER, 8), LocalDate.of(2026, Month.DECEMBER, 8)),
        localName = "Mariä Empfängnis",
    )

    fun loadedState() = TripDashboardUiState(
        trip = trip,
        period = DECEMBER,
        periods = TravelPeriod.selectable(TODAY),
        today = TODAY,
        flights = UiState.Success(flights, DataOrigin.DEMO),
        stays = UiState.Success(stays, DataOrigin.CACHE),
        highlights = UiState.Success(highlights),
        events = UiState.Success(tripEvents),
        transit = UiState.Success(transit),
        restaurants = UiState.Success(restaurants),
        isDemoMode = true,
        assistantAvailable = true,
        alertEnabled = true,
        pricesUpdatedAt = Instant.parse("2026-09-30T19:15:00Z"),
    )

    private val lodgings = listOf(
        Lodging(
            id = "osm:way/1",
            name = "Hotel Sacher Wien",
            type = LodgingType.HOTEL,
            location = GeoPoint(48.2039, 16.3694),
            starRating = 5,
            address = "Philharmoniker Straße 4",
            website = "https://www.sacher.com/",
        ),
        Lodging("osm:node/2", "Pension Nossek", LodgingType.GUEST_HOUSE, GeoPoint(48.2093, 16.3690), address = "Graben 17"),
        Lodging("osm:node/3", "Wombat's City Hostel The Naschmarkt", LodgingType.HOSTEL, GeoPoint(48.1975, 16.3601), address = "Rechte Wienzeile 35"),
    )

    private fun openDataRestaurant(id: String, name: String, cuisine: String, address: String, location: GeoPoint) = Restaurant(
        id = id,
        name = name,
        priceLevel = null,
        rating = null,
        cuisine = cuisine,
        address = address,
        location = location,
        mapsUrl = googleMapsSearchUrl("$name, $address, Vienna"),
    )

    private val openDataRestaurants = listOf(
        openDataRestaurant("osm:node/11", "Figlmüller", "Austriaca", "Wollzeile 5", GeoPoint(48.2091, 16.3747)),
        openDataRestaurant("osm:node/12", "Griechenbeisl", "Austriaca", "Fleischmarkt 11", GeoPoint(48.2115, 16.3771)),
        openDataRestaurant("osm:node/13", "Pizza Bizi", "Pizza", "Rotenturmstraße 4", GeoPoint(48.2094, 16.3736)),
    )

    /** Senza chiavi API: voli e mezzi stimati con i collegamenti ai siti, alloggi e ristoranti reali (OpenStreetMap). */
    fun openDataState() = loadedState().copy(
        stays = UiState.Empty,
        lodgings = UiState.Success(lodgings),
        stayOffersAvailable = false,
        transit = UiState.Success(transit, DataOrigin.DEMO),
        restaurants = UiState.Success(openDataRestaurants),
        restaurantRatingsAvailable = false,
    )

    fun noDepartureState() = loadedState().copy(trip = trip.copy(departure = null), flights = UiState.Empty, alertEnabled = false)

    fun mixedStates() = loadedState().copy(
        highlights = UiState.Loading,
        flights = UiState.Error(DataError.NoConnection),
        stays = UiState.Success(stays, DataOrigin.STALE_CACHE),
        transit = UiState.Empty,
        restaurants = UiState.Error(DataError.Unauthorized),
    )

    // ---- Schermata di ricerca -----------------------------------------------------------------

    private fun city(id: String, name: String, country: String, countryCode: String, lat: Double, lon: Double, zone: String, region: String? = null, population: Int? = null) =
        CityPlace(
            id = id,
            name = name,
            countryCode = countryCode,
            location = GeoPoint(lat, lon),
            timeZone = ZoneId.of(zone),
            country = country,
            region = region,
            population = population,
        )

    private val searchResults = listOf(
        city("geonames:2988507", "Parigi", "Francia", "FR", 48.8534, 2.3488, "Europe/Paris", "Île-de-France", 2_138_551),
        city("geonames:4717560", "Paris", "Stati Uniti", "US", 33.6609, -95.5555, "America/Chicago", "Texas", 24_782),
        city("geonames:2988506", "Parma", "Italia", "IT", 44.8015, 10.3279, "Europe/Rome", "Emilia-Romagna", 175_895),
    )

    private val suggestions = listOf(
        DestinationSuggestion(
            destination = CatalogDestination(
                city = city("catalog:tokyo", "Tokyo", "Giappone", "JP", 35.6762, 139.6503, "Asia/Tokyo"),
                pleasantMonths = setOf(Month.OCTOBER, Month.NOVEMBER),
                experiences = listOf(TravelExperience(TravelTheme.FOLIAGE, setOf(Month.NOVEMBER)), TravelExperience(TravelTheme.FOOD)),
                tagline = "Templi, quartieri futuristici, ciliegi in fiore in primavera e aceri rossi in autunno.",
            ),
            score = 0.65,
            seasonalHighlights = listOf(TravelTheme.FOLIAGE),
            yearRoundHighlights = listOf(TravelTheme.FOOD, TravelTheme.CULTURE),
            pleasantClimate = true,
            currentWeather = WeatherSnapshot(temperatureCelsius = 21.0, condition = WeatherCondition.CLEAR),
        ),
        DestinationSuggestion(
            destination = CatalogDestination(
                city = city("catalog:lisbona", "Lisbona", "Portogallo", "PT", 38.7223, -9.1393, "Europe/Lisbon"),
                pleasantMonths = setOf(Month.OCTOBER),
                experiences = listOf(TravelExperience(TravelTheme.FOOD)),
                tagline = "Tram gialli, belvederi sul Tago e pastéis de nata appena sfornati.",
            ),
            score = 0.4,
            seasonalHighlights = emptyList(),
            yearRoundHighlights = listOf(TravelTheme.FOOD, TravelTheme.CULTURE),
            pleasantClimate = true,
        ),
    )

    fun searchIdleState() = SearchUiState(today = TODAY, departure = SampleDestinations.MILAN_DEPARTURE)

    fun searchResultsState() = SearchUiState(today = TODAY, results = UiState.Success(searchResults))

    fun searchIdeasState() = SearchUiState(
        today = TODAY,
        departure = SampleDestinations.MILAN_DEPARTURE,
        recommendations = UiState.Success(suggestions),
    )

    // ---- Scelta della partenza ------------------------------------------------------------------

    fun departureAirportsState(): DeparturePickerUiState {
        val milan = city("geonames:3173435", "Milano", "Italia", "IT", 45.4643, 9.1895, "Europe/Rome", "Lombardia", 1_371_498)
        val malpensa = SampleDestinations.MILAN_DEPARTURE.airport
        val linate = Airport("LIN", "Milano Linate Airport", "Segrate (MI)", "IT", GeoPoint(45.4451, 9.2767), AirportSize.LARGE)
        val bergamo = Airport("BGY", "Il Caravaggio International Airport", "Orio al Serio (BG)", "IT", GeoPoint(45.6694, 9.7089), AirportSize.LARGE)
        return DeparturePickerUiState(
            current = SampleDestinations.MILAN_DEPARTURE,
            selectedCity = milan,
            airports = UiState.Success(
                listOf(
                    AirportOption(malpensa, distanceKm = 40, recommended = true),
                    AirportOption(linate, distanceKm = 7, recommended = false),
                    AirportOption(bergamo, distanceKm = 46, recommended = false),
                ),
            ),
        )
    }

    // ---- Scheda di un luogo ---------------------------------------------------------------------

    private val stephansdom = PointOfInterest(
        id = "wikipedia:it:83456",
        name = "Duomo di Vienna",
        category = PoiCategory.RELIGIOUS_SITE,
        location = GeoPoint(48.2085, 16.3731),
        description = "Cattedrale cattolica della città austriaca di Vienna",
        tags = setOf(PoiTag.INSTAGRAMMABLE),
        popularity = 1.0,
        wikipediaPage = WikipediaPage("it", "Duomo di Vienna"),
    )

    private val stephansdomDetails = PoiDetails(
        summary = "Il duomo di Santo Stefano è la cattedrale di Vienna e il simbolo della città: capolavoro del gotico " +
            "austriaco, sorge nel cuore del centro storico.\n\nLa torre sud, alta 136 metri, domina il panorama: i viennesi " +
            "la chiamano affettuosamente «Steffl».",
        history = listOf(
            HistoryChapter("Origini", "La prima chiesa, in stile romanico, fu consacrata nel 1147, quando sorgeva ancora fuori dalle mura."),
            HistoryChapter("Il gotico", "Nel 1359 il duca Rodolfo IV avviò la ricostruzione gotica, che diede alla chiesa la forma attuale."),
            HistoryChapter("Dalla guerra a oggi", "Danneggiato da un incendio nel 1945, il duomo fu ricostruito e riaperto nel 1952."),
        ),
        imageCredit = ImageCredit(author = "Bwag", license = "CC BY-SA 4.0", sourceUrl = "https://commons.wikimedia.org/wiki/File:Stephansdom.jpg"),
        sourceUrl = "https://it.wikipedia.org/wiki/Duomo_di_Vienna",
        language = "it",
    )

    fun placeDetailState() = PlaceDetailUiState(poi = stephansdom, details = UiState.Success(stephansdomDetails))

    // ---- Itinerario dell'assistente -------------------------------------------------------------

    private val hofburg = PointOfInterest(
        id = "wikipedia:it:hofburg",
        name = "Hofburg",
        category = PoiCategory.MONUMENT,
        location = GeoPoint(48.2066, 16.3654),
        description = "Palazzo imperiale degli Asburgo",
    )

    /** Itinerario come quelli proposti da Gemini: luoghi ed eventi dell'app più un caffè storico suggerito dall'IA. */
    val tripPlan = TripPlan(
        days = listOf(
            DayPlan(
                date = LocalDate.of(2026, Month.DECEMBER, 10),
                title = "Arrivo e prime luci nel centro",
                stops = listOf(
                    PlanStop(DayPart.AFTERNOON, "Duomo di Vienna", "Ammira la cattedrale gotica e sali sulla torre sud.", 60, StopTarget.Place(stephansdom)),
                    PlanStop(DayPart.AFTERNOON, "Café Central", "Una fetta di Sachertorte nel caffè letterario più famoso.", 60),
                    PlanStop(DayPart.EVENING, "Weihnachtsmarkt am Spittelberg", "Vin brulè e artigianato tra i vicoli Biedermeier.", 90, StopTarget.Event(tripEvents.events.first())),
                ),
                tip = "Compra il biglietto dei mezzi da 72 ore: conviene già dal primo giorno.",
            ),
            DayPlan(
                date = LocalDate.of(2026, Month.DECEMBER, 11),
                title = "Arte imperiale",
                stops = listOf(
                    PlanStop(DayPart.MORNING, "Hofburg", "Appartamenti imperiali e Museo di Sissi.", 120, StopTarget.Place(hofburg)),
                    PlanStop(DayPart.EVENING, "Weihnachtsmarkt Schloss Schönbrunn", "Il mercatino davanti alla reggia.", 90, StopTarget.Event(tripEvents.events.last())),
                ),
            ),
        ),
        packing = listOf(
            PackingGroup("Abbigliamento", listOf("Cappotto caldo", "Sciarpa, guanti e berretto", "Scarpe impermeabili")),
            PackingGroup("Documenti e soldi", listOf("Carta d'identità", "Contanti per i mercatini")),
        ),
        tips = listOf(
            "Molte bancarelle dei mercatini accettano solo contanti.",
            "Al ristorante si arrotonda il conto del 5–10%.",
        ),
    )

    fun itineraryState() = ItineraryUiState(
        destination = SampleDestinations.VIENNA,
        from = LocalDate.of(2026, Month.DECEMBER, 10),
        to = LocalDate.of(2026, Month.DECEMBER, 14),
        preferences = TripPreferences(interests = setOf(TripInterest.ART, TripInterest.FOOD)),
        plan = UiState.Success(tripPlan),
        packedItems = setOf("Abbigliamento › Cappotto caldo"),
    )

    // ---- Chiedi a PartiMo -------------------------------------------------------------------------

    fun chatState() = ChatUiState(
        destination = SampleDestinations.VIENNA,
        from = LocalDate.of(2026, Month.DECEMBER, 10),
        to = LocalDate.of(2026, Month.DECEMBER, 14),
        messages = listOf(
            ChatMessage(ChatRole.USER, "Cosa devo assolutamente mangiare a Vienna?"),
            ChatMessage(
                ChatRole.ASSISTANT,
                "Ecco tre piatti imperdibili:\n\n• Wiener Schnitzel: la cotoletta di vitello impanata, da Figlmüller.\n" +
                    "• Tafelspitz: bollito di manzo con salsa di rafano e mele, da Plachutta.\n" +
                    "• Sachertorte: la torta al cioccolato più famosa, all'Hotel Sacher.\n\nPrenota in anticipo: a dicembre i locali sono pieni.",
            ),
        ),
    )
}
