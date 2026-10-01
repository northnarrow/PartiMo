package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.guide.CountryInfo
import com.partimo.domain.model.guide.ExchangeRates
import com.partimo.domain.model.guide.GuideSection
import com.partimo.domain.model.guide.PowerInfo
import com.partimo.domain.model.guide.TravelGuide
import com.partimo.domain.model.weather.DailyForecast
import com.partimo.domain.model.weather.DailyObservation
import com.partimo.domain.model.weather.TripWeather
import com.partimo.domain.model.weather.WeatherCondition
import com.partimo.domain.testing.FakeCountryInfoRepository
import com.partimo.domain.testing.FakeExchangeRateRepository
import com.partimo.domain.testing.FakeTravelGuideRepository
import com.partimo.domain.testing.FakeTripWeatherRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuideAndWeatherUseCasesTest {

    // ---- Meteo del viaggio -----------------------------------------------------------------------

    private val weather = FakeTripWeatherRepository()
    private val getTripWeather = GetTripWeatherUseCase(weather, TestData.FIXED_CLOCK)
    private val today = TestData.TODAY

    private fun forecast(date: LocalDate) = DailyForecast(date, WeatherCondition.RAIN, 15.0, 9.0, 2.0, 60)

    @Test
    fun `per un viaggio vicino usa le previsioni dei giorni del viaggio`() = runTest {
        val from = today.plusDays(2)
        weather.forecastResult = DataResult.Success((0L..15L).map { forecast(today.plusDays(it)) }, DataOrigin.CACHE)

        val result = getTripWeather(TestData.VIENNA_CENTER, from, from.plusDays(4))

        val days = assertIs<TripWeather.Forecast>(result.successData()).days
        assertEquals((0L..4L).map { from.plusDays(it) }, days.map { it.date })
        assertEquals(listOf(from to from.plusDays(4)), weather.forecastRequests)
        assertTrue(weather.historyRequests.isEmpty())
    }

    @Test
    fun `un viaggio che va oltre l'orizzonte usa le previsioni dei giorni disponibili`() = runTest {
        val from = today.plusDays(13)
        weather.forecastResult = DataResult.Success((13L..15L).map { forecast(today.plusDays(it)) })

        getTripWeather(TestData.VIENNA_CENTER, from, from.plusDays(4)).successData()

        assertEquals(listOf(from to today.plusDays(15)), weather.forecastRequests)
    }

    @Test
    fun `per un viaggio lontano calcola il clima tipico del periodo, anche a cavallo di Capodanno`() = runTest {
        // Dieci anni di dati: 30 dicembre–5 gennaio a 2 °C/−3 °C, con pioggia un giorno su due; il resto più caldo.
        val history = (2016..2025).flatMap { year ->
            generateSequence(LocalDate.of(year, 1, 1)) { it.plusDays(1) }.takeWhile { it.year == year }.map { date ->
                val inWindow = MonthDay.from(date) >= MonthDay.of(Month.DECEMBER, 25) || MonthDay.from(date) <= MonthDay.of(Month.JANUARY, 6)
                DailyObservation(date, if (inWindow) 2.0 else 20.0, if (inWindow) -3.0 else 10.0, if (inWindow && date.dayOfMonth % 2 == 0) 4.0 else 0.0)
            }.toList()
        }
        weather.historyResult = DataResult.Success(history)

        val climate = assertIs<TripWeather.Climate>(getTripWeather(TestData.VIENNA_CENTER, LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2)).successData())

        val normals = climate.normals
        assertEquals(MonthDay.of(Month.DECEMBER, 27), normals.from, "Tre giorni di margine")
        assertEquals(MonthDay.of(Month.JANUARY, 5), normals.to)
        assertEquals(2.0, normals.averageMaxCelsius, 0.001)
        assertEquals(-3.0, normals.averageMinCelsius, 0.001)
        assertTrue(normals.wetDaysShare in 0.4..0.6)
        assertEquals(10, normals.years)
        assertEquals(listOf(10), weather.historyRequests)
    }

    @Test
    fun `senza dati storici sufficienti il clima non si inventa`() = runTest {
        weather.historyResult = DataResult.Success(listOf(DailyObservation(LocalDate.of(2025, 12, 10), 3.0, 0.0, 0.0)))

        assertEquals(DataError.InvalidResponse, getTripWeather(TestData.VIENNA_CENTER, LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 14)).failureError())
    }

    // ---- Valuta ---------------------------------------------------------------------------------

    @Test
    fun `cambio dall'euro alla valuta del paese e viceversa`() = runTest {
        val rates = ExchangeRates("EUR", mapOf("CZK" to BigDecimal("24.44"), "HUF" to BigDecimal("366.2")))
        val repository = FakeExchangeRateRepository(DataResult.Success(rates, DataOrigin.CACHE))

        val rate = GetExchangeRateUseCase(repository)(to = "CZK").successData()

        assertEquals(0, BigDecimal("24.44").compareTo(rate.rate))
        assertEquals(0, BigDecimal("244.40").compareTo(rate.convert(BigDecimal.TEN)))
        assertEquals(0, BigDecimal.ONE.compareTo(rate.inverse().convert(BigDecimal("24.44")).setScale(4, RoundingMode.HALF_UP)))
        // Tra due valute diverse dall'euro si passa dall'euro: 366,20 fiorini = 1 € = 24,44 corone.
        assertEquals(0, BigDecimal("24.44").compareTo(rates.convert(BigDecimal("366.2"), "HUF", "CZK")))
        assertEquals(DataError.InvalidResponse, GetExchangeRateUseCase(repository)(to = "XYZ").failureError())
    }

    @Test
    fun `prese e tensione dicono se serve un adattatore`() {
        assertTrue(PowerInfo(listOf("C", "F"), "230", "50").fitsItalianPlugs)
        assertFalse(PowerInfo(listOf("G"), "230", "50").fitsItalianPlugs)
        assertTrue(PowerInfo(listOf("A", "B"), "100", "50/60").lowVoltage)
        assertFalse(PowerInfo(listOf("C", "N"), "127/220", "60").lowVoltage)
    }

    @Test
    fun `informazioni sul paese con il codice in maiuscolo`() = runTest {
        val info = CountryInfo("AT", "AUT", "Austria", "EUR", "euro", "€")
        val repository = FakeCountryInfoRepository(DataResult.Success(info))

        assertTrue(GetCountryInfoUseCase(repository)("at").successData().usesEuro)
        assertEquals(listOf("AT"), repository.requests)
    }

    // ---- Guida ------------------------------------------------------------------------------------

    @Test
    fun `della guida restano i capitoli utili a chi parte, nell'ordine in cui servono`() = runTest {
        val guide = TravelGuide(
            title = "Vienna",
            language = "it",
            url = "https://it.wikivoyage.org/wiki/Vienna",
            introduction = listOf("Vienna è la capitale dell'Austria."),
            sections = listOf(
                GuideSection("Cosa vedere", listOf("Musei")),
                GuideSection("Come spostarsi", listOf("La metropolitana ha 5 linee.", " ")),
                GuideSection("Da sapere", emptyList(), listOf(GuideSection("Quando andare", listOf("Dicembre è freddo.")))),
                GuideSection("Come arrivare", listOf("In aereo", "In treno", "In autobus")),
                GuideSection("Dove alloggiare", listOf("Hotel")),
                GuideSection("Sicurezza", emptyList()),
            ),
        )
        val repository = FakeTravelGuideRepository(DataResult.Success(guide))

        val useful = GetTravelGuideUseCase(repository, maxParagraphsPerSection = 2)(TestData.destination()).successData()!!

        assertEquals(listOf("Da sapere", "Come arrivare", "Come spostarsi"), useful.sections.map { it.title })
        assertEquals(listOf("In aereo", "In treno"), useful.sections[1].paragraphs)
        assertEquals(listOf("La metropolitana ha 5 linee."), useful.sections[2].paragraphs)
        assertEquals("Quando andare", useful.sections[0].subsections.single().title)
    }

    @Test
    fun `una città senza guida non è un errore`() = runTest {
        assertNull(GetTravelGuideUseCase(FakeTravelGuideRepository())(TestData.destination()).successData())
    }
}
