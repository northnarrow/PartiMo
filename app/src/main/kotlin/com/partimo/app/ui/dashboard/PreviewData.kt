package com.partimo.app.ui.dashboard

import com.partimo.app.files.StoredDocument
import com.partimo.app.ui.bookings.BookingDay
import com.partimo.app.ui.bookings.BookingEditorUiState
import com.partimo.app.ui.bookings.BookingsUiState
import com.partimo.app.ui.budget.BudgetUiState
import com.partimo.app.ui.budget.ExpenseDraft
import com.partimo.app.ui.chat.ChatUiState
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.departure.DeparturePickerUiState
import com.partimo.app.ui.favorites.FavoritesUiState
import com.partimo.app.ui.guide.GuideUiState
import com.partimo.app.ui.itinerary.ItineraryUiState
import com.partimo.app.ui.map.MapPoints
import com.partimo.app.ui.map.MapSource
import com.partimo.app.ui.map.MapUiState
import com.partimo.app.ui.place.PlaceDetailUiState
import com.partimo.app.ui.place.googleMapsSearchUrl
import com.partimo.app.ui.search.SearchUiState
import com.partimo.app.ui.translator.LanguagePackState
import com.partimo.app.ui.translator.PhotoState
import com.partimo.app.ui.translator.TranslatedText
import com.partimo.app.ui.translator.TranslatorUiState
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.Money
import com.partimo.domain.model.ScoredOffer
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.TripContext
import com.partimo.domain.model.WheelchairAccess
import com.partimo.domain.model.booking.Booking
import com.partimo.domain.model.booking.BookingDraft
import com.partimo.domain.model.booking.BookingKind
import com.partimo.domain.model.budget.BudgetSummary
import com.partimo.domain.model.budget.Expense
import com.partimo.domain.model.budget.ExpenseCategory
import com.partimo.domain.model.budget.TripBudget
import com.partimo.domain.model.dining.PriceLevel
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.event.EventTiming
import com.partimo.domain.model.event.TripEvent
import com.partimo.domain.model.event.TripEvents
import com.partimo.domain.model.flight.CheapDestination
import com.partimo.domain.model.flight.FareSnapshot
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightPriceSource
import com.partimo.domain.model.flight.FlightSlice
import com.partimo.domain.model.flight.PriceCalendar
import com.partimo.domain.model.guide.CountryInfo
import com.partimo.domain.model.guide.DrivingSide
import com.partimo.domain.model.guide.EmergencyNumbers
import com.partimo.domain.model.guide.ExchangeRate
import com.partimo.domain.model.guide.GuideSection
import com.partimo.domain.model.guide.PowerInfo
import com.partimo.domain.model.guide.TravelGuide
import com.partimo.domain.model.ocr.PhotoTranslation
import com.partimo.domain.model.ocr.RecognizedBlock
import com.partimo.domain.model.ocr.TranslatedBlock
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
import com.partimo.domain.model.saved.Favorite
import com.partimo.domain.model.saved.FavoriteKind
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.model.stay.AccommodationOffer
import com.partimo.domain.model.stay.Lodging
import com.partimo.domain.model.stay.LodgingType
import com.partimo.domain.model.transit.TransitLeg
import com.partimo.domain.model.transit.TransitLine
import com.partimo.domain.model.transit.TransitMode
import com.partimo.domain.model.transit.TransitRoute
import com.partimo.domain.model.transit.TransitStop
import com.partimo.domain.model.weather.ClimateNormals
import com.partimo.domain.model.weather.DailyForecast
import com.partimo.domain.model.weather.SunTimes
import com.partimo.domain.model.weather.TripWeather
import com.partimo.domain.model.weather.WeatherCondition
import com.partimo.domain.model.weather.WeatherSnapshot
import com.partimo.domain.service.SeasonalCalendar
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay
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

    /** Prezzi trovati di recente su Aviasales: ogni volo ha le sue date nel mese scelto. */
    private val recentFlights = listOf(
        ScoredOffer(
            FlightOffer(
                id = "tp-fr",
                carrierName = "Ryanair",
                carrierIata = "FR",
                carrierLogoUrl = "https://pics.avs.io/96/96/FR.png",
                totalPrice = Money.of(58, "EUR"),
                slices = listOf(
                    slice("BGY", "VIE", LocalDateTime.of(2026, 12, 11, 21, 10), 90, 0),
                    slice("VIE", "BGY", LocalDateTime.of(2026, 12, 13, 8, 25), 90, 0),
                ),
                bookingUrl = "https://www.aviasales.com/search/MIL1112VIE13121",
                priceFoundOn = LocalDate.of(2026, Month.SEPTEMBER, 30),
            ),
            valueScore = 0.94,
        ),
        ScoredOffer(
            FlightOffer(
                id = "tp-os",
                carrierName = "Austrian Airlines",
                carrierIata = "OS",
                carrierLogoUrl = "https://pics.avs.io/96/96/OS.png",
                totalPrice = Money.of(149, "EUR"),
                slices = listOf(
                    slice("MXP", "VIE", LocalDateTime.of(2026, 12, 5, 7, 0), 85, 0),
                    slice("VIE", "MXP", LocalDateTime.of(2026, 12, 9, 19, 40), 85, 0),
                ),
                bookingUrl = "https://www.aviasales.com/search/MIL0512VIE09121",
                priceFoundOn = LocalDate.of(2026, Month.SEPTEMBER, 29),
            ),
            valueScore = 0.71,
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
        flightPriceSource = FlightPriceSource.ESTIMATES,
        stays = UiState.Success(stays, DataOrigin.CACHE),
        highlights = UiState.Success(highlights),
        events = UiState.Success(tripEvents),
        transit = UiState.Success(transit),
        restaurants = UiState.Success(restaurants),
        isDemoMode = true,
        assistantAvailable = true,
        favoritesEnabled = true,
        savedTrip = savedTrips.first(),
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

    private fun openDataRestaurant(
        id: String,
        name: String,
        cuisine: String,
        address: String,
        location: GeoPoint,
        openingHours: String? = null,
        wheelchair: WheelchairAccess? = null,
    ) = Restaurant(
        id = id,
        name = name,
        priceLevel = null,
        rating = null,
        cuisine = cuisine,
        address = address,
        location = location,
        mapsUrl = googleMapsSearchUrl("$name, $address, Vienna"),
        openingHours = openingHours,
        wheelchair = wheelchair,
    )

    /** Orari e accessibilità come nei dati reali di OpenStreetMap. */
    private val openDataRestaurants = listOf(
        openDataRestaurant("osm:node/11", "Figlmüller", "Austriaca", "Wollzeile 5", GeoPoint(48.2091, 16.3747), "Mo-Su 11:00-22:30", WheelchairAccess.LIMITED),
        openDataRestaurant("osm:node/12", "Griechenbeisl", "Austriaca", "Fleischmarkt 11", GeoPoint(48.2115, 16.3771), "Mo-Fr 11:30-14:30, 18:00-22:00; Sa 18:00-22:00; Su, PH off"),
        openDataRestaurant("osm:node/13", "Pizza Bizi", "Pizza", "Rotenturmstraße 4", GeoPoint(48.2094, 16.3736), "Mo-Su,PH 11:00-24:00", WheelchairAccess.YES),
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

    /** Voli con i prezzi trovati di recente su Aviasales (token di Travelpayouts). */
    fun recentFlightPricesState() = loadedState().copy(
        flights = UiState.Success(recentFlights, DataOrigin.REMOTE),
        flightPriceSource = FlightPriceSource.RECENT_SEARCHES,
    )

    /** Date scelte dall'utente (11–13 dicembre): il volo proprio in quei giorni, poi uno dei giorni vicini. */
    val DATES: TravelPeriod.Dates = TravelPeriod.Dates(LocalDate.of(2026, Month.DECEMBER, 11), LocalDate.of(2026, Month.DECEMBER, 13))

    /** Due adulti e un bambino di 7 anni. */
    val FAMILY = Travellers(adults = 2, childAges = listOf(7))

    private val datesFlights = listOf(
        recentFlights.first(),
        ScoredOffer(
            FlightOffer(
                id = "tp-fr-near",
                carrierName = "Ryanair",
                carrierIata = "FR",
                carrierLogoUrl = "https://pics.avs.io/96/96/FR.png",
                totalPrice = Money.of(44, "EUR"),
                slices = listOf(
                    slice("BGY", "VIE", LocalDateTime.of(2026, 12, 10, 17, 5), 85, 0),
                    slice("VIE", "BGY", LocalDateTime.of(2026, 12, 13, 6, 5), 90, 0),
                ),
                bookingUrl = "https://www.aviasales.com/search/MIL1012VIE13121",
                priceFoundOn = LocalDate.of(2026, Month.SEPTEMBER, 30),
            ),
            valueScore = 0.97,
        ),
    )

    /** Voli di Aviasales per le date scelte: prima quelli di quei giorni, poi quelli dei giorni vicini. */
    fun recentFlightPricesDatesState() = recentFlightPricesState().copy(
        trip = trip.copy(departureDate = DATES.departure, returnDate = DATES.returning),
        period = DATES,
        periods = listOf(TravelPeriod.NextDays, DATES) + TravelPeriod.selectable(TODAY).drop(1),
        flights = UiState.Success(datesFlights, DataOrigin.REMOTE),
    )

    /** Due adulti e un bambino: i prezzi trovati su Aviasales sono per tre posti. */
    fun familyFlightPricesState() = recentFlightPricesState().copy(
        trip = trip.copy(travellers = FAMILY),
        flights = UiState.Success(
            recentFlights.map { scored ->
                val offer = scored.offer
                scored.copy(offer = offer.copy(totalPrice = Money.of(offer.totalPrice.amount.multiply(BigDecimal(3)), "EUR"), passengers = 3))
            },
            DataOrigin.REMOTE,
        ),
    )

    /** Prezzi più bassi a persona per mese (Milano–Vienna, andata e ritorno di 2–7 notti, da Aviasales). */
    val monthPrices: Map<YearMonth, FareSnapshot> = listOf(
        YearMonth.of(2026, 10) to "33",
        YearMonth.of(2026, 11) to "39",
        YearMonth.of(2026, 12) to "72",
        YearMonth.of(2027, 1) to "76",
        YearMonth.of(2027, 2) to "50",
    ).associate { (month, price) -> month to FareSnapshot(Money.of(price, "EUR"), month.atDay(7), month.atDay(10), carrierIata = "FR") }

    /** Calendario di dicembre: tariffe reali per giorno di partenza (Milano–Vienna). */
    val decemberCalendar = PriceCalendar(
        month = YearMonth.of(2026, 12),
        stayNights = TravelPeriod.FLEXIBLE_STAY_NIGHTS,
        fares = listOf(4 to "148" to 7, 5 to "146" to 9, 6 to "132" to 9, 7 to "72" to 10, 11 to "92" to 13, 18 to "84" to 22, 19 to "125" to 23, 20 to "96" to 24, 27 to "165" to 30, 28 to "168" to 30)
            .associate { (dayAndPrice, back) ->
                val (day, price) = dayAndPrice
                val departure = LocalDate.of(2026, 12, day)
                departure to FareSnapshot(Money.of(price, "EUR"), departure, LocalDate.of(2026, 12, back), carrierIata = "FR")
            },
    )

    /** Voli di Aviasales con i prezzi dei mesi e il calendario disponibile. */
    fun monthPricesState() = recentFlightPricesState().copy(monthPrices = monthPrices, priceCalendarAvailable = true)

    /** Calendario dei prezzi aperto su dicembre. */
    fun priceCalendarState() = monthPricesState().copy(
        priceCalendar = PriceCalendarState(
            month = YearMonth.of(2026, 12),
            firstMonth = YearMonth.of(2026, 10),
            lastMonth = YearMonth.of(2027, 9),
            calendar = UiState.Success(decemberCalendar, DataOrigin.REMOTE),
        ),
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

    /** Schermata iniziale con le date esatte scelte nelle celle «Andata» e «Ritorno». */
    fun searchDatesState() = searchIdleState().copy(period = DATES, travellers = FAMILY)

    fun searchIdeasState() = SearchUiState(
        today = TODAY,
        departure = SampleDestinations.MILAN_DEPARTURE,
        recommendations = UiState.Success(suggestions),
    )

    /** Mete di «Ovunque» da Milano a dicembre: tariffe reali trovate su Aviasales. */
    private val cheapDestinations = listOf(
        cheap("PMO", "Palermo", "Italia", "IT", 38.1157, 13.3615, "Europe/Rome", "29", 10, 15),
        cheap("TIA", "Tirana", "Albania", "AL", 41.3275, 19.8187, "Europe/Tirane", "30", 10, 13),
        cheap("BUH", "Bucarest", "Romania", "RO", 44.4377, 26.0974, "Europe/Bucharest", "32", 13, 18, airport = "OTP"),
        cheap("BCN", "Barcellona", "Spagna", "ES", 41.3879, 2.1699, "Europe/Madrid", "33", 10, 12),
        cheap("ALC", "Alicante", "Spagna", "ES", 38.3452, -0.481, "Europe/Madrid", "34", 14, 19),
        cheap("WAW", "Varsavia", "Polonia", "PL", 52.2297, 21.0122, "Europe/Warsaw", "36", 9, 14),
        cheap("SVQ", "Siviglia", "Spagna", "ES", 37.3826, -5.9963, "Europe/Madrid", "37", 12, 14),
        cheap("STO", "Stoccolma", "Svezia", "SE", 59.3328, 18.0645, "Europe/Stockholm", "48", 11, 13, airport = "ARN"),
        cheap("PRG", "Praga", "Cechia", "CZ", 50.0755, 14.4378, "Europe/Prague", "55", 21, 23),
        cheap("HRG", "Hurghada", "Egitto", "EG", 27.2579, 33.8116, "Africa/Cairo", "63", 15, 20),
    )

    @Suppress("LongParameterList")
    private fun cheap(
        code: String,
        name: String,
        country: String,
        countryCode: String,
        latitude: Double,
        longitude: Double,
        zone: String,
        price: String,
        from: Int,
        to: Int,
        airport: String = code,
    ) = CheapDestination(
        city = CityPlace("tp:$code", name, countryCode, GeoPoint(latitude, longitude), ZoneId.of(zone), country = country),
        cityCode = code,
        airportIata = airport,
        fare = FareSnapshot(Money.of(price, "EUR"), LocalDate.of(2026, 12, from), LocalDate.of(2026, 12, to), carrierIata = "W4"),
        bookingUrl = "https://www.aviasales.com/search/MIL${"%02d".format(from)}12$code${"%02d".format(to)}121",
    )

    /** «Ovunque» da Milano a dicembre, con le mete più economiche. */
    fun searchAnywhereState() = searchIdleState().copy(
        period = DECEMBER,
        anywhereAvailable = true,
        anywhere = UiState.Success(cheapDestinations, DataOrigin.REMOTE),
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

    // ---- Guida del viaggio ------------------------------------------------------------------------

    val austria = CountryInfo(
        countryCode = "AT",
        countryCode3 = "AUT",
        name = "Austria",
        currencyCode = "EUR",
        currencyName = "euro",
        currencySymbol = "€",
        languages = listOf("tedesco"),
        callingCode = "+43",
        drivingSide = DrivingSide.RIGHT,
        power = PowerInfo(listOf("C", "F"), "230", "50"),
        emergency = EmergencyNumbers(general = "112", police = "133", ambulance = "144", fire = "122"),
    )

    /** Guida reale di Wikivoyage, accorciata. */
    private val viennaGuide = TravelGuide(
        title = "Vienna",
        language = "it",
        url = "https://it.wikivoyage.org/wiki/Vienna",
        introduction = listOf("Vienna è la capitale dell'Austria."),
        sections = listOf(
            GuideSection(
                "Da sapere",
                listOf("Si va a Vienna per rivivere le glorie e i fasti della dinastia asburgica, ma anche le sue tragedie."),
                listOf(GuideSection("Quando andare", listOf("Dicembre è il mese dei mercatini di Natale."))),
            ),
            GuideSection(
                "Come spostarsi",
                emptyList(),
                listOf(
                    GuideSection("Con mezzi pubblici", listOf("I biglietti si acquistano ai distributori automatici e nelle tabaccherie.", "72 ore: 17,10 €.")),
                    GuideSection("In taxi", listOf("Per il servizio taxi chiamare il numero 40100.")),
                ),
            ),
            GuideSection("Sicurezza", listOf("Vienna è una città molto sicura, ma al Prater e nelle stazioni è meglio stare in guardia.")),
        ),
    )

    fun guideState() = GuideUiState(
        destination = SampleDestinations.VIENNA,
        from = LocalDate.of(2026, Month.DECEMBER, 10),
        to = LocalDate.of(2026, Month.DECEMBER, 14),
        country = UiState.Success(austria),
        exchangeRate = UiState.Empty,
        weather = UiState.Success(
            TripWeather.Climate(ClimateNormals(MonthDay.of(Month.DECEMBER, 7), MonthDay.of(Month.DECEMBER, 17), 5.0, 0.1, 0.31, 10)),
        ),
        sunTimes = SunTimes(
            date = LocalDate.of(2026, Month.DECEMBER, 10),
            sunrise = LocalTime.of(7, 34),
            sunset = LocalTime.of(15, 59),
            morningGoldenHourEnd = LocalTime.of(8, 22),
            eveningGoldenHourStart = LocalTime.of(15, 11),
        ),
        guide = UiState.Success(viennaGuide),
        expandedSections = setOf("Come spostarsi"),
    )

    /** Guida di un viaggio a Praga tra pochi giorni: corone ceche e previsioni giorno per giorno. */
    fun pragueGuideState() = guideState().copy(
        destination = SampleDestinations.VIENNA.copy(
            name = "Praga",
            countryCode = "CZ",
            airportIata = "PRG",
            center = GeoPoint(50.0755, 14.4378),
            arrivalHub = GeoPoint(50.1008, 14.26),
            arrivalHubName = "Aeroporto di Praga",
            timeZone = ZoneId.of("Europe/Prague"),
        ),
        from = LocalDate.of(2026, 10, 2),
        to = LocalDate.of(2026, 10, 6),
        sunTimes = null,
        guide = UiState.Empty,
        country = UiState.Success(
            austria.copy(countryCode = "CZ", countryCode3 = "CZE", name = "Cechia", currencyCode = "CZK", currencyName = "corona ceca", currencySymbol = "Kč", languages = listOf("ceco"), callingCode = "+420"),
        ),
        exchangeRate = UiState.Success(ExchangeRate("EUR", "CZK", BigDecimal("24.431512"), Instant.parse("2026-10-01T00:02:31Z"))),
        weather = UiState.Success(
            TripWeather.Forecast(
                listOf(
                    DailyForecast(LocalDate.of(2026, 10, 2), WeatherCondition.OVERCAST, 21.0, 12.0, 0.0, 5),
                    DailyForecast(LocalDate.of(2026, 10, 3), WeatherCondition.PARTLY_CLOUDY, 22.0, 11.0, 0.0, 0),
                    DailyForecast(LocalDate.of(2026, 10, 4), WeatherCondition.RAIN, 16.0, 10.0, 4.5, 75),
                ),
            ),
        ),
    )

    // ---- Preferiti e viaggi salvati ---------------------------------------------------------------

    val favorites = listOf(
        Favorite(
            id = stephansdom.id,
            kind = FavoriteKind.PLACE,
            name = stephansdom.name,
            location = stephansdom.location,
            wikipediaPage = stephansdom.wikipediaPage,
            category = stephansdom.category,
            description = stephansdom.description,
        ),
        Favorite(id = "wikidata:spittelberg", kind = FavoriteKind.EVENT, name = "Weihnachtsmarkt am Spittelberg", location = GeoPoint(48.2030, 16.3540)),
        Favorite(
            id = "osm:node/11",
            kind = FavoriteKind.RESTAURANT,
            name = "Figlmüller",
            subtitle = "Austriaca · Wollzeile 5",
            location = GeoPoint(48.2091, 16.3747),
            url = googleMapsSearchUrl("Figlmüller, Wollzeile 5, Vienna"),
        ),
    )

    fun favoritesState() = FavoritesUiState(
        destination = SampleDestinations.VIENNA,
        period = DECEMBER,
        from = LocalDate.of(2026, Month.DECEMBER, 10),
        to = LocalDate.of(2026, Month.DECEMBER, 14),
        favorites = favorites,
        loaded = true,
    )

    val savedTrips = listOf(
        SavedTrip(SampleDestinations.VIENNA, DECEMBER, Instant.parse("2026-09-30T08:00:00Z"), favorites),
        SavedTrip(
            SampleDestinations.VIENNA.copy(name = "Lisbona", countryCode = "PT", airportIata = "LIS", timeZone = ZoneId.of("Europe/Lisbon")),
            TravelPeriod.InMonth(YearMonth.of(2027, Month.MARCH)),
            Instant.parse("2026-09-29T08:00:00Z"),
        ),
    )

    // ---- Mappa ------------------------------------------------------------------------------------

    /** Mappa di Vienna a dicembre con luoghi, mercatini, ristoranti, alloggi e preferiti. */
    fun mapState(selectFirst: Boolean = false): MapUiState {
        val state = MapUiState(
            destination = SampleDestinations.VIENNA,
            from = LocalDate.of(2026, Month.DECEMBER, 10),
            to = LocalDate.of(2026, Month.DECEMBER, 14),
            sources = mapOf(
                MapSource.HIGHLIGHTS to UiState.Success(MapPoints.places(highlights)),
                MapSource.EVENTS to UiState.Success(MapPoints.events(tripEvents)),
                MapSource.RESTAURANTS to UiState.Success(MapPoints.restaurants(openDataRestaurants)),
                MapSource.LODGINGS to UiState.Success(MapPoints.lodgings(lodgings, SampleDestinations.VIENNA.name)),
            ),
            favoritesEnabled = true,
            favorites = favorites,
            fitRequest = 1,
        )
        return if (selectFirst) state.copy(selectedKey = state.points.first().key) else state
    }

    // ---- Traduttore -------------------------------------------------------------------------------

    /** Traduttore per Vienna con il pacchetto tedesco pronto, una traduzione e il frasario aperto. */
    fun translatorState() = TranslatorUiState(
        destination = SampleDestinations.VIENNA,
        userLanguage = "it",
        foreignLanguage = "de",
        countryLanguages = listOf("de"),
        supportedLanguages = listOf("de", "en", "es", "fr", "it"),
        packs = LanguagePackState.Ready,
        input = "Dov'è la fermata del tram per lo Schönbrunn?",
        result = UiState.Success(
            TranslatedText(
                source = "Dov'è la fermata del tram per lo Schönbrunn?",
                translation = "Wo ist die Straßenbahnhaltestelle nach Schönbrunn?",
                language = "de",
            ),
        ),
        phraseTranslations = mapOf(
            TranslatorUiState.phraseKey("de", "Buongiorno") to "Guten Morgen",
            TranslatorUiState.phraseKey("de", "Buonasera") to "Guten Abend",
            TranslatorUiState.phraseKey("de", "Grazie mille") to "Vielen Dank",
            TranslatorUiState.phraseKey("de", "Per favore") to "Bitte",
        ),
        photoAvailable = true,
    )

    /** Menù di una trattoria di Vienna fotografato e tradotto sopra la foto. */
    fun translatorPhotoState() = translatorState().copy(
        input = "",
        result = null,
        photo = PhotoState(
            uri = "content://com.partimo.app.files/camera/menu.jpg",
            translation = UiState.Success(
                PhotoTranslation(
                    width = 1000,
                    height = 900,
                    blocks = listOf(
                        TranslatedBlock(RecognizedBlock("Speisekarte", 320, 40, 680, 130), "Menù"),
                        TranslatedBlock(RecognizedBlock("Wiener Schnitzel mit Kartoffelsalat 18,50", 60, 190, 940, 270), "Cotoletta alla viennese con insalata di patate 18,50"),
                        TranslatedBlock(RecognizedBlock("Tafelspitz mit Apfelkren 22,90", 60, 310, 940, 390), "Bollito di manzo con salsa di mele e rafano 22,90"),
                        TranslatedBlock(RecognizedBlock("Kaiserschmarrn mit Zwetschkenröster 12,40", 60, 430, 940, 510), "Frittata dolce sminuzzata con composta di prugne 12,40"),
                        TranslatedBlock(RecognizedBlock("Apfelstrudel mit Schlagobers 6,90", 60, 550, 940, 630), "Strudel di mele con panna montata 6,90"),
                        TranslatedBlock(RecognizedBlock("Bitte warten, Sie werden platziert", 100, 760, 900, 840), "Attendere, verrete accompagnati al tavolo"),
                    ),
                ),
            ),
        ),
    )

    /** Primo uso: i pacchetti italiano e tedesco sono ancora da scaricare. */
    fun translatorMissingPackState() = translatorState().copy(
        packs = LanguagePackState.Missing(setOf("it", "de")),
        input = "",
        result = null,
        phraseTranslations = emptyMap(),
    )

    // ---- Budget -----------------------------------------------------------------------------------

    private val prague = SampleDestinations.VIENNA.copy(
        name = "Praga",
        countryCode = "CZ",
        airportIata = "PRG",
        center = GeoPoint(50.0755, 14.4378),
        arrivalHub = GeoPoint(50.1008, 14.26),
        arrivalHubName = "Praga-Ruzyně",
        timeZone = ZoneId.of("Europe/Prague"),
    )

    private val pragueExpenses = listOf(
        Expense("1", BigDecimal("142.50"), "EUR", ExpenseCategory.TRANSPORT, LocalDate.of(2026, Month.DECEMBER, 10), "Volo Milano–Praga"),
        Expense("2", BigDecimal("356"), "EUR", ExpenseCategory.LODGING, LocalDate.of(2026, Month.DECEMBER, 10), "Hotel in Malá Strana"),
        Expense("3", BigDecimal("890"), "CZK", ExpenseCategory.FOOD, LocalDate.of(2026, Month.DECEMBER, 11), "Cena al Lokál"),
        Expense("4", BigDecimal("450"), "CZK", ExpenseCategory.ACTIVITIES, LocalDate.of(2026, Month.DECEMBER, 11), "Castello di Praga"),
        Expense("5", BigDecimal("120"), "CZK", ExpenseCategory.FOOD, LocalDate.of(2026, Month.DECEMBER, 12), "Trdelník e vin brulé"),
    )

    /** Budget di un viaggio a Praga con spese in euro e in corone, convertite con un cambio di 24,44. */
    fun budgetState(): BudgetUiState {
        val rate = BigDecimal("24.44")
        val converted = pragueExpenses.associate { expense ->
            val euros = if (expense.currency == "EUR") expense.amount else expense.amount.divide(rate, 2, java.math.RoundingMode.HALF_UP)
            expense.id to euros.setScale(2, java.math.RoundingMode.HALF_UP)
        }
        val byCategory = pragueExpenses.groupBy { it.category }.mapValues { (_, expenses) -> expenses.sumOf { converted.getValue(it.id) } }
        val budget = TripBudget("CZ:Praga:2026-12", BigDecimal("800"), pragueExpenses)
        return BudgetUiState(
            destination = prague,
            period = DECEMBER,
            from = LocalDate.of(2026, Month.DECEMBER, 10),
            to = LocalDate.of(2026, Month.DECEMBER, 14),
            localCurrency = "CZK",
            budget = budget,
            summary = BudgetSummary(
                currency = "EUR",
                total = converted.values.fold(BigDecimal.ZERO.setScale(2), BigDecimal::add),
                byCategory = byCategory,
                limit = budget.limit,
                converted = converted,
            ),
        )
    }

    /** Nuova spesa in corone mentre si è a Praga. */
    fun budgetDraftState() = budgetState().copy(
        draft = ExpenseDraft(amountText = "320", currency = "CZK", category = ExpenseCategory.TRANSPORT, date = LocalDate.of(2026, Month.DECEMBER, 12), note = "Biglietti del tram"),
    )

    // ---- Prenotazioni ---------------------------------------------------------------------------

    private val outboundFlight = Booking(
        id = "preview-andata",
        kind = BookingKind.FLIGHT,
        title = "Ryanair FR 7178",
        startDate = LocalDate.of(2026, Month.DECEMBER, 11),
        startTime = LocalTime.of(21, 10),
        endDate = LocalDate.of(2026, Month.DECEMBER, 11),
        endTime = LocalTime.of(22, 55),
        origin = "BGY",
        destination = "VIE",
        reference = "K7M2QX",
        provider = "Ryanair",
        notes = "Posto 12A · solo bagaglio a mano",
        attachment = "booking-preview.pdf",
        attachmentType = "application/pdf",
    )

    val previewBookings: List<Booking> = listOf(
        outboundFlight,
        Booking(
            id = "preview-hotel",
            kind = BookingKind.LODGING,
            title = "Hotel Sacher Wien",
            startDate = LocalDate.of(2026, Month.DECEMBER, 11),
            startTime = LocalTime.of(15, 0),
            endDate = LocalDate.of(2026, Month.DECEMBER, 13),
            endTime = LocalTime.of(12, 0),
            reference = "4815162342",
            provider = "Booking.com",
            address = "Philharmoniker Str. 4, 1010 Wien",
        ),
        Booking(
            id = "preview-scuola",
            kind = BookingKind.ACTIVITY,
            title = "Scuola di equitazione spagnola",
            startDate = LocalDate.of(2026, Month.DECEMBER, 12),
            startTime = LocalTime.of(11, 0),
            reference = "SRS-2291",
            address = "Michaelerplatz 1, 1010 Wien",
        ),
        Booking(
            id = "preview-ritorno",
            kind = BookingKind.FLIGHT,
            title = "Ryanair FR 7179",
            startDate = LocalDate.of(2026, Month.DECEMBER, 13),
            startTime = LocalTime.of(8, 25),
            endDate = LocalDate.of(2026, Month.DECEMBER, 13),
            endTime = LocalTime.of(10, 0),
            origin = "VIE",
            destination = "BGY",
            reference = "K7M2QX",
            provider = "Ryanair",
        ),
    )

    private val pastTrain = Booking(
        id = "preview-treno",
        kind = BookingKind.TRAIN,
        title = "Frecciarossa 9517",
        startDate = LocalDate.of(2026, Month.SEPTEMBER, 12),
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(11, 10),
        origin = "Milano Centrale",
        destination = "Roma Termini",
        reference = "PNR8XK",
        provider = "Trenitalia",
    )

    /** Linea del tempo del viaggio a Vienna, con un treno già passato. */
    fun bookingsState() = BookingsUiState(
        today = TODAY,
        upcoming = previewBookings.groupBy { it.startDate }.map { (date, bookings) -> BookingDay(date, bookings) },
        past = listOf(pastTrain),
        loaded = true,
    )

    fun bookingsEmptyState() = BookingsUiState(today = TODAY, loaded = true)

    /** Nuova prenotazione con la mail di conferma incollata, da leggere. */
    fun bookingEditorState() = BookingEditorUiState(
        today = TODAY,
        pastedText = "Conferma della prenotazione K7M2QX\nven, 11 dic 2026 · FR 7178 Milano Bergamo (BGY) - Vienna (VIE)\nPartenza 21:10 · Arrivo 22:55",
    )

    /** Andata e ritorno letti dal PDF del biglietto: la prima da controllare, poi la seconda. */
    fun bookingEditorReadState() = BookingEditorUiState(
        today = TODAY,
        draft = BookingDraft.of(outboundFlight).copy(notes = ""),
        pending = listOf(BookingDraft.of(previewBookings.last())),
        readCount = 2,
        attachment = StoredDocument("booking-preview.pdf", "application/pdf"),
    )
}
