package com.partimo.domain.service

import com.partimo.domain.model.Money
import com.partimo.domain.model.deal.Deal
import com.partimo.domain.model.deal.DealKind
import com.partimo.domain.model.deal.PriceChange
import com.partimo.domain.model.deal.PricePoint
import com.partimo.domain.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DealDetectorTest {

    private val detector = DealDetector()

    private fun history(vararg amounts: String, currency: String = "EUR") = amounts.map { PricePoint(Money.of(it, currency), TestData.NOW) }

    private fun eur(amount: String) = Money.of(amount, "EUR")

    @Test
    fun `senza storico non c'è un riferimento e nessun avviso`() {
        val assessment = detector.assess(eur("50"), emptyList(), lastNotified = null)

        assertNull(assessment.usualPrice)
        assertFalse(assessment.shouldNotify)
    }

    @Test
    fun `un prezzo almeno il 15 per cento sotto il solito è un affare`() {
        val usual = history("100", "110", "120")

        assertEquals(eur("110"), detector.assess(eur("93"), usual, null).usualPrice)
        assertTrue(detector.assess(eur("93.50"), usual, null).shouldNotify)
        assertFalse(detector.assess(eur("95"), usual, null).shouldNotify)
    }

    @Test
    fun `il prezzo abituale è la mediana degli ultimi controlli`() {
        assertEquals(eur("110"), detector.usualPrice(history("100", "120"), "EUR"))
        // Un picco isolato non sposta la mediana.
        assertEquals(eur("100"), detector.usualPrice(history("100", "100", "900"), "EUR"))
        // Contano solo gli ultimi 10 controlli.
        assertEquals(eur("50"), detector.usualPrice(history("500", *Array(10) { "50" }), "EUR"))
    }

    @Test
    fun `lo stesso affare non viene notificato due volte`() {
        val usual = history("200", "200", "200")

        assertFalse(detector.assess(eur("150"), usual, lastNotified = eur("150")).shouldNotify)
        assertTrue(detector.assess(eur("150"), usual, lastNotified = eur("150")).isBelowThreshold)
        // Serve un ulteriore calo del 5%.
        assertTrue(detector.assess(eur("142"), usual, lastNotified = eur("150")).shouldNotify)
    }

    @Test
    fun `i prezzi in altre valute non sono confrontabili`() {
        assertNull(detector.assess(eur("50"), history("100", currency = "USD"), null).usualPrice)
    }

    @Test
    fun `lo sconto e la variazione di prezzo si calcolano sugli importi`() {
        val deal = Deal(DealKind.FLIGHT, "Wizz Air", price = eur("75"), usualPrice = eur("100"))
        assertEquals(25, deal.discountPercent)

        val drop = PriceChange(previous = eur("120"), current = eur("99"))
        assertTrue(drop.isDrop)
        assertEquals(eur("21").amount.negate(), drop.difference)
        assertFalse(PriceChange(eur("99"), eur("99")).isRise)
    }
}
