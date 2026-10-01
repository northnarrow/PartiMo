package com.partimo.domain.service

import com.partimo.domain.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CarbonFootprintTest {

    private val malpensa = GeoPoint(45.6306, 8.7231)
    private val vienna = GeoPoint(48.2082, 16.3738)

    @Test
    fun `da Milano a Vienna il treno inquina un quarto dell'aereo e l'auto da soli più di tutti`() {
        val footprint = CarbonFootprint.roundTrip(malpensa, vienna).associateBy { it.mode }

        val straightKm = malpensa.distanceTo(vienna) / 1_000
        assertEquals(straightKm * 2 * CarbonFootprint.FLIGHT_KG_PER_KM, footprint.getValue(TravelMode.FLIGHT).kgCo2e, 0.001)
        assertEquals(straightKm * 2.4, footprint.getValue(TravelMode.TRAIN).distanceKm, 0.001)
        // Circa 640 km in linea d'aria: ~180 kg in aereo, ~55 in treno, ~43 in pullman, ~260 in auto.
        assertEquals(180.0, footprint.getValue(TravelMode.FLIGHT).kgCo2e, 15.0)
        assertEquals(55.0, footprint.getValue(TravelMode.TRAIN).kgCo2e, 5.0)
        assertEquals(43.0, footprint.getValue(TravelMode.COACH).kgCo2e, 5.0)
        assertEquals(260.0, footprint.getValue(TravelMode.CAR).kgCo2e, 20.0)
        assertEquals(
            listOf(TravelMode.COACH, TravelMode.TRAIN, TravelMode.FLIGHT, TravelMode.CAR),
            CarbonFootprint.roundTrip(malpensa, vienna).map { it.mode },
            "Dal meno al più inquinante",
        )
    }

    @Test
    fun `in auto in quattro le emissioni si dividono`() {
        val alone = CarbonFootprint.roundTrip(malpensa, vienna).first { it.mode == TravelMode.CAR }
        val four = CarbonFootprint.roundTrip(malpensa, vienna, travellers = 4).first { it.mode == TravelMode.CAR }

        assertEquals(alone.kgCo2e / 4, four.kgCo2e, 0.001)
        assertFailsWith<IllegalArgumentException> { CarbonFootprint.roundTrip(malpensa, vienna, travellers = 0) }
    }

    @Test
    fun `i voli brevi pesano di più al chilometro e per le mete lontane resta solo l'aereo`() {
        val rome = GeoPoint(41.9028, 12.4964)
        val naples = GeoPoint(40.8518, 14.2681)
        val short = CarbonFootprint.roundTrip(rome, naples).first { it.mode == TravelMode.FLIGHT }
        assertEquals(short.distanceKm * CarbonFootprint.SHORT_FLIGHT_KG_PER_KM, short.kgCo2e, 0.001)

        val newYork = GeoPoint(40.7128, -74.0060)
        assertEquals(listOf(TravelMode.FLIGHT), CarbonFootprint.roundTrip(malpensa, newYork).map { it.mode })

        val milan = GeoPoint(45.4642, 9.1900)
        assertTrue(CarbonFootprint.roundTrip(milan, GeoPoint(45.4800, 9.2000)).isEmpty(), "Meta vicina: niente confronto")
    }
}
