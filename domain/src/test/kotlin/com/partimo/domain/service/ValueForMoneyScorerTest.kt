package com.partimo.domain.service

import com.partimo.domain.testing.TestData.flightOffer
import com.partimo.domain.testing.TestData.stayOffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValueForMoneyScorerTest {

    private val scorer = ValueForMoneyScorer()

    @Test
    fun `il volo più economico, rapido e diretto ottiene il punteggio massimo`() {
        val scores = scorer.scoreFlights(
            listOf(
                flightOffer("best", "100", durationMinutes = 90, stops = 0),
                flightOffer("worst", "300", durationMinutes = 300, stops = 2),
            ),
        ).associate { it.offer.id to it.valueScore }

        assertEquals(1.0, scores.getValue("best"), 1e-9)
        assertEquals(0.0, scores.getValue("worst"), 1e-9)
    }

    @Test
    fun `a parità di prezzo vince il volo più breve`() {
        val scores = scorer.scoreFlights(
            listOf(
                flightOffer("short", "120", durationMinutes = 90),
                flightOffer("long", "120", durationMinutes = 200),
            ),
        ).associate { it.offer.id to it.valueScore }

        assertTrue(scores.getValue("short") > scores.getValue("long"))
    }

    @Test
    fun `una lista vuota produce una lista vuota`() {
        assertTrue(scorer.scoreFlights(emptyList()).isEmpty())
        assertTrue(scorer.scoreStays(emptyList()).isEmpty())
    }

    @Test
    fun `la media bayesiana penalizza i punteggi con poche recensioni`() {
        val scores = scorer.scoreStays(
            listOf(
                stayOffer("few-reviews", "400", reviewScore = 9.6, reviewCount = 3),
                stayOffer("many-reviews", "400", reviewScore = 9.0, reviewCount = 900),
                stayOffer("average-1", "400", reviewScore = 7.5, reviewCount = 300),
                stayOffer("average-2", "400", reviewScore = 7.5, reviewCount = 300),
            ),
        ).associate { it.offer.id to it.valueScore }

        assertTrue(scores.getValue("many-reviews") > scores.getValue("few-reviews"))
    }

    @Test
    fun `l'alloggio economico e ben recensito ha il miglior rapporto qualità prezzo`() {
        val ranked = scorer.scoreStays(
            listOf(
                stayOffer("luxury", "1200", reviewScore = 9.2, stars = 5),
                stayOffer("smart", "420", reviewScore = 8.9, stars = 3),
                stayOffer("cheap-poor", "380", reviewScore = 6.1, stars = 2),
            ),
        ).sortedByDescending { it.valueScore }

        assertEquals("smart", ranked.first().offer.id)
    }
}
