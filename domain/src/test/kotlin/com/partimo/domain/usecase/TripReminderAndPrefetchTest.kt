package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.guide.CountryInfo
import com.partimo.domain.model.guide.ExchangeRates
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.model.weather.DailyObservation
import com.partimo.domain.service.ReminderKind
import com.partimo.domain.service.TripReminders
import com.partimo.domain.testing.FakeCountryInfoRepository
import com.partimo.domain.testing.FakeEventRepository
import com.partimo.domain.testing.FakeExchangeRateRepository
import com.partimo.domain.testing.FakeHolidayRepository
import com.partimo.domain.testing.FakeLodgingRepository
import com.partimo.domain.testing.FakePoiRepository
import com.partimo.domain.testing.FakeReminderLogRepository
import com.partimo.domain.testing.FakeRestaurantRepository
import com.partimo.domain.testing.FakeSavedTripRepository
import com.partimo.domain.testing.FakeTravelGuideRepository
import com.partimo.domain.testing.FakeTripWeatherRepository
import com.partimo.domain.testing.FakeWeatherRepository
import com.partimo.domain.testing.TestData
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripReminderAndPrefetchTest {

    private val december = TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER))
    private val vienna = SavedTrip(TestData.destination(), december, Instant.parse("2026-09-30T08:00:00Z"))
    private val lastMinute = SavedTrip(TestData.destination().copy(name = "Praga", countryCode = "CZ"), TravelPeriod.NextDays, Instant.parse("2026-09-30T08:00:00Z"))

    @Test
    fun `una settimana prima e il giorno prima della partenza, una volta sola`() {
        // Partenza prevista il 10 dicembre.
        assertTrue(TripReminders.due(listOf(vienna), LocalDate.of(2026, 12, 2), emptySet()).isEmpty(), "Otto giorni prima: ancora niente")

        val week = TripReminders.due(listOf(vienna), LocalDate.of(2026, 12, 3), emptySet()).single()
        assertEquals(ReminderKind.WEEK_BEFORE, week.kind)
        assertEquals(7, week.daysLeft)
        assertEquals(LocalDate.of(2026, 12, 10), week.departure)

        // Telefono spento il 3: il promemoria arriva il 5, poi non si ripete.
        assertEquals(5, TripReminders.due(listOf(vienna), LocalDate.of(2026, 12, 5), emptySet()).single().daysLeft)
        assertTrue(TripReminders.due(listOf(vienna), LocalDate.of(2026, 12, 5), setOf(week.key)).isEmpty())

        val day = TripReminders.due(listOf(vienna), LocalDate.of(2026, 12, 9), setOf(week.key)).single()
        assertEquals(ReminderKind.DAY_BEFORE, day.kind)
        assertEquals(1, day.daysLeft)
        assertTrue(TripReminders.due(listOf(vienna), LocalDate.of(2026, 12, 11), emptySet()).isEmpty(), "Dopo la partenza niente")
    }

    @Test
    fun `con le date scelte il promemoria segue il giorno di partenza vero`() {
        val lisbon = SavedTrip(
            TestData.destination().copy(name = "Lisbona", countryCode = "PT"),
            TravelPeriod.Dates(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 24)),
            Instant.parse("2026-09-30T08:00:00Z"),
        )

        val week = TripReminders.due(listOf(lisbon), LocalDate.of(2026, 11, 13), emptySet()).single()
        assertEquals(ReminderKind.WEEK_BEFORE, week.kind)
        assertEquals(LocalDate.of(2026, 11, 20), week.departure)
        assertEquals(ReminderKind.DAY_BEFORE, TripReminders.due(listOf(lisbon), LocalDate.of(2026, 11, 19), emptySet()).single().kind)
        assertTrue(TripReminders.hasUpcoming(listOf(lisbon), LocalDate.of(2026, 11, 20)))
        assertFalse(TripReminders.hasUpcoming(listOf(lisbon), LocalDate.of(2026, 11, 21)))
    }

    @Test
    fun `i viaggi last minute non hanno una data fissa e non ricevono promemoria`() {
        assertTrue(TripReminders.due(listOf(lastMinute), LocalDate.of(2026, 12, 9), emptySet()).isEmpty())
        assertFalse(TripReminders.hasUpcoming(listOf(lastMinute), LocalDate.of(2026, 12, 9)))
        assertTrue(TripReminders.hasUpcoming(listOf(vienna, lastMinute), LocalDate.of(2026, 12, 9)))
        assertFalse(TripReminders.hasUpcoming(listOf(vienna), LocalDate.of(2026, 12, 11)))
    }

    @Test
    fun `il caso d'uso legge i viaggi salvati e ricorda i promemoria mostrati`() = runTest {
        val log = FakeReminderLogRepository()
        val clock = Clock.fixed(Instant.parse("2026-12-09T08:00:00Z"), ZoneOffset.UTC)
        val reminders = TripRemindersUseCase(FakeSavedTripRepository(listOf(vienna, lastMinute)), log, clock)

        val due = reminders.due()
        assertEquals(listOf(ReminderKind.DAY_BEFORE), due.map { it.kind })
        reminders.markShown(due)
        assertTrue(reminders.due().isEmpty())
        assertEquals(setOf(due.single().key), log.sent)
        assertTrue(reminders.hasUpcoming())
    }

    @Test
    fun `prepara il viaggio offline con tutte le fonti, anche se una non risponde`() = runTest {
        val pois = FakePoiRepository(DataResult.Success(listOf(TestData.poi("view", "Kahlenberg").copy(photoUrl = "https://img.test/kahlenberg.jpg"))))
        val restaurants = FakeRestaurantRepository(DataResult.Failure(DataError.NoConnection))
        val rates = FakeExchangeRateRepository(DataResult.Success(ExchangeRates("EUR", mapOf("CZK" to BigDecimal("24.44")))))
        val czechia = CountryInfo("CZ", "CZE", "Cechia", "CZK", "corona ceca", "Kč")
        val prefetch = PrefetchTripUseCase(
            getSeasonalHighlights = GetSeasonalHighlightsUseCase(pois, FakeWeatherRepository(), clock = TestData.FIXED_CLOCK),
            getTripEvents = GetTripEventsUseCase(FakeEventRepository(), FakeHolidayRepository()),
            findBudgetRestaurants = FindBudgetRestaurantsUseCase(restaurants),
            findLodgings = FindLodgingsUseCase(FakeLodgingRepository()),
            getTravelGuide = GetTravelGuideUseCase(FakeTravelGuideRepository()),
            getTripWeather = GetTripWeatherUseCase(
                FakeTripWeatherRepository(
                    historyResult = DataResult.Success(
                        (2016..2025).flatMap { year -> (5..20).map { day -> DailyObservation(LocalDate.of(year, 12, day), 4.0, -1.0, 0.0) } },
                    ),
                ),
                TestData.FIXED_CLOCK,
            ),
            getCountryInfo = GetCountryInfoUseCase(FakeCountryInfoRepository(DataResult.Success(czechia))),
            getExchangeRate = GetExchangeRateUseCase(rates),
        )

        val result = prefetch(TestData.destination(), LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 14))

        assertEquals(1, result.failed, "I ristoranti non rispondono")
        assertEquals(6, result.succeeded, "Luoghi, eventi, alloggi, guida, meteo e cambio")
        assertEquals(listOf("https://img.test/kahlenberg.jpg"), result.photoUrls)
        assertEquals(listOf("EUR" to false), rates.requests, "Il cambio della corona")
    }
}
