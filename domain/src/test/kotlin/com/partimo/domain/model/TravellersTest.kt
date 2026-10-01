package com.partimo.domain.model

import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TravellersTest {

    @Test
    fun `una famiglia con un neonato occupa un posto in meno in aereo`() {
        val family = Travellers(adults = 2, childAges = listOf(7, 1))

        assertEquals(2, family.children)
        assertEquals(4, family.total)
        assertEquals(1, family.infants)
        assertEquals(3, family.seatedPassengers)
        assertTrue(family.infantsHaveLaps)
        assertFalse(family.isSolo)
        assertTrue(Travellers.SOLO.isSolo)
    }

    @Test
    fun `ogni neonato deve stare in braccio a un adulto diverso`() {
        assertFalse(Travellers(adults = 1, childAges = listOf(0, 1)).infantsHaveLaps)
        assertTrue(Travellers(adults = 2, childAges = listOf(0, 1)).infantsHaveLaps)
        assertTrue(Travellers(adults = 1, childAges = listOf(2)).infantsHaveLaps, "A 2 anni si ha un posto proprio")
    }

    @Test
    fun `al massimo nove viaggiatori, almeno un adulto, bambini fino a 17 anni`() {
        assertFailsWith<IllegalArgumentException> { Travellers(adults = 0) }
        assertFailsWith<IllegalArgumentException> { Travellers(adults = 5, childAges = List(5) { 6 }) }
        assertFailsWith<IllegalArgumentException> { Travellers(adults = 1, childAges = listOf(18)) }
        assertFailsWith<IllegalArgumentException> { Travellers(adults = 1, childAges = listOf(-1)) }
        assertEquals(9, Travellers(adults = 2, childAges = List(7) { 10 }).total)
    }

    @Test
    fun `una camera ogni due adulti, i bambini dormono con loro`() {
        assertEquals(1, AccommodationSearchQuery.roomsFor(Travellers(adults = 1)))
        assertEquals(1, AccommodationSearchQuery.roomsFor(Travellers(adults = 2, childAges = listOf(4, 9))))
        assertEquals(2, AccommodationSearchQuery.roomsFor(Travellers(adults = 3)))
        assertEquals(2, AccommodationSearchQuery.roomsFor(Travellers(adults = 4)))
    }

    @Test
    fun `il prezzo a persona divide il totale tra i posti pagati`() {
        val offer = TestData.flightOffer("a", "250").copy(passengers = 3)

        assertEquals(Money.of("83.33", "EUR"), offer.pricePerPassenger)
        assertEquals(TestData.flightOffer("b", "88").totalPrice, TestData.flightOffer("b", "88").pricePerPassenger)
    }
}
