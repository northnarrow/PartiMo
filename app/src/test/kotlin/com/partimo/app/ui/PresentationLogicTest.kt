package com.partimo.app.ui

import com.partimo.app.ui.common.Formatters
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.common.toListUiState
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.Money
import com.partimo.domain.model.dining.PriceLevel
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiStateMappingTest {

    @Test
    fun `una lista vuota diventa Empty`() {
        assertEquals(UiState.Empty, DataResult.Success(emptyList<Int>()).toListUiState())
    }

    @Test
    fun `un successo conserva dati e origine`() {
        assertEquals(UiState.Success(listOf(1), DataOrigin.CACHE), DataResult.Success(listOf(1), DataOrigin.CACHE).toListUiState())
    }

    @Test
    fun `un fallimento diventa Error`() {
        assertEquals(UiState.Error(DataError.Timeout), DataResult.Failure(DataError.Timeout).toListUiState<Int>())
    }
}

class PeriodFormattingTest {

    @Test
    fun `i mesi dell'anno corrente non mostrano l'anno, quelli successivi sì`() {
        assertEquals("dicembre", Formatters.monthYear(YearMonth.of(2026, Month.DECEMBER), currentYear = 2026))
        assertEquals("gennaio 2027", Formatters.monthYear(YearMonth.of(2027, Month.JANUARY), currentYear = 2026))
    }

    @Test
    fun `le variazioni di prezzo si mostrano senza segno`() {
        val drop = Formatters.moneyAmount(BigDecimal("-12.00"), "EUR")

        assertTrue("12" in drop && "-" !in drop && "−" !in drop, drop)
    }
}

class FormattersTest {

    @Test
    fun `formatta le durate`() {
        assertEquals("1 h 30 min", Formatters.duration(Duration.ofMinutes(90)))
        assertEquals("45 min", Formatters.duration(Duration.ofMinutes(45)))
        assertEquals("2 h", Formatters.duration(Duration.ofMinutes(120)))
    }

    @Test
    fun `formatta intervalli di date con i mesi in minuscolo`() {
        assertEquals("12–16 dic", Formatters.dateRange(LocalDate.of(2026, Month.DECEMBER, 12), LocalDate.of(2026, Month.DECEMBER, 16)))
        assertEquals("28 dic – 2 gen", Formatters.dateRange(LocalDate.of(2026, Month.DECEMBER, 28), LocalDate.of(2027, Month.JANUARY, 2)))
        assertEquals("dicembre", Formatters.monthName(Month.DECEMBER))
    }

    @Test
    fun `mostra i centesimi solo se presenti`() {
        val whole = Formatters.money(Money.of(129, "EUR"))
        val cents = Formatters.money(Money.of("129.40", "EUR"))

        assertTrue("129" in whole && "€" in whole && "," !in whole, whole)
        assertTrue("129,40" in cents, cents)
    }

    @Test
    fun `rappresenta la fascia di prezzo con il simbolo dell'euro`() {
        assertEquals("€€", Formatters.priceLevel(PriceLevel.MODERATE))
        assertEquals("€", Formatters.priceLevel(PriceLevel.FREE))
    }
}
