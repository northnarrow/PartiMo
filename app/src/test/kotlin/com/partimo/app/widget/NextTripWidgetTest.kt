package com.partimo.app.widget

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.saved.SavedTrip
import com.partimo.domain.testing.TestData
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NextTripWidgetTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val savedAt = Instant.parse("2026-09-30T08:00:00Z")
    private val vienna = SavedTrip(TestData.destination(), TravelPeriod.InMonth(YearMonth.of(2026, Month.DECEMBER)), savedAt)
    private val lisbon = SavedTrip(TestData.destination().copy(name = "Lisbona", countryCode = "PT"), TravelPeriod.InMonth(YearMonth.of(2026, Month.NOVEMBER)), savedAt)
    private val past = SavedTrip(TestData.destination().copy(name = "Roma", countryCode = "IT"), TravelPeriod.InMonth(YearMonth.of(2026, Month.AUGUST)), savedAt)

    @Test
    fun `mostra il viaggio salvato che parte per primo, senza quelli passati`() {
        val today = LocalDate.of(2026, Month.OCTOBER, 1)

        val next = NextTripInfo.from(listOf(vienna, past, lisbon), today)!!

        assertEquals("Lisbona", next.trip.destination.name)
        assertEquals(LocalDate.of(2026, Month.NOVEMBER, 10), next.departure)
        assertEquals(40, next.daysLeft)
        assertNull(NextTripInfo.from(listOf(past), today))
    }

    @Test
    fun `il conto alla rovescia dice oggi, domani o tra quanti giorni`() {
        assertEquals("si parte oggi", countdownText(context, 0))
        assertEquals("si parte domani", countdownText(context, 1))
        assertEquals("tra 40 giorni", countdownText(context, 40))
    }
}
