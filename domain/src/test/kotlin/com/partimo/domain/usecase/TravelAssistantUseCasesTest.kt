package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.plan.ChatMessage
import com.partimo.domain.model.plan.ChatRole
import com.partimo.domain.model.plan.DayPart
import com.partimo.domain.model.plan.DayPlan
import com.partimo.domain.model.plan.PackingGroup
import com.partimo.domain.model.plan.PlanStop
import com.partimo.domain.model.plan.StopTarget
import com.partimo.domain.model.plan.TripInterest
import com.partimo.domain.model.plan.TripKnowledge
import com.partimo.domain.model.plan.TripPace
import com.partimo.domain.model.plan.TripPlan
import com.partimo.domain.model.plan.TripPreferences
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.service.SeasonalCalendar
import com.partimo.domain.testing.FakeEventRepository
import com.partimo.domain.testing.FakeHolidayRepository
import com.partimo.domain.testing.FakePoiRepository
import com.partimo.domain.testing.FakeTravelAssistantRepository
import com.partimo.domain.testing.FakeUserPreferencesRepository
import com.partimo.domain.testing.FakeWeatherRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.TestData.event
import com.partimo.domain.testing.TestData.poi
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TravelAssistantUseCasesTest {

    private val vienna = TestData.destination()
    private val from = LocalDate.of(2026, Month.DECEMBER, 10)
    private val to = LocalDate.of(2026, Month.DECEMBER, 14)

    private val museum = poi("museum", "Kunsthistorisches Museum", category = PoiCategory.MUSEUM, rating = 4.8)
    private val cathedral = poi("duomo", "Duomo di Santo Stefano", category = PoiCategory.RELIGIOUS_SITE, rating = 4.7)
    private val market = event("spittelberg", SeasonalCalendar.CHRISTMAS_MARKET_SEASON, kind = EventKind.CHRISTMAS_MARKET)

    private val poiRepository = FakePoiRepository(result = DataResult.Success(listOf(museum, cathedral)))
    private val eventRepository = FakeEventRepository(result = DataResult.Success(listOf(market)))
    private val assistant = FakeTravelAssistantRepository()
    private val loadKnowledge = LoadTripKnowledgeUseCase(
        GetSeasonalHighlightsUseCase(poiRepository, FakeWeatherRepository(), clock = TestData.FIXED_CLOCK),
        GetTripEventsUseCase(eventRepository, FakeHolidayRepository()),
    )
    private val planTrip = PlanTripUseCase(assistant, loadKnowledge)
    private val askAssistant = AskTravelAssistantUseCase(assistant, maxMessages = 4)

    private fun stop(name: String, part: DayPart = DayPart.MORNING, target: StopTarget? = null) =
        PlanStop(dayPart = part, name = name, activity = "Visita", target = target)

    private fun knowledge() = TripKnowledge(vienna, from, to)

    @Test
    fun `l'assistente riceve luoghi, eventi del soggiorno, preferenze e richiesta di rigenerare`() = runTest {
        assistant.planResult = DataResult.Success(TripPlan(days = listOf(DayPlan(from, "Arrivo", listOf(stop("Duomo"))))))
        val preferences = TripPreferences(pace = TripPace.RELAXED, interests = setOf(TripInterest.ART))

        planTrip(vienna, from, to, preferences, forceRefresh = true).successData()

        val (knowledge, sentPreferences, forceRefresh) = assistant.planRequests.single()
        assertEquals(setOf("museum", "duomo"), knowledge.places.map { it.id }.toSet())
        assertEquals(listOf("spittelberg"), knowledge.events.map { it.id })
        assertEquals(5, knowledge.dayCount)
        assertEquals(preferences, sentPreferences)
        assertTrue(forceRefresh)
        assertEquals(false, eventRepository.forceRefreshFlags.single(), "Gli eventi arrivano dalla cache")
    }

    @Test
    fun `l'assistente sa chi parte, come scelto dall'utente`() = runTest {
        val family = Travellers(adults = 2, childAges = listOf(6))
        val load = LoadTripKnowledgeUseCase(
            GetSeasonalHighlightsUseCase(poiRepository, FakeWeatherRepository(), clock = TestData.FIXED_CLOCK),
            GetTripEventsUseCase(eventRepository, FakeHolidayRepository()),
            observeTravellers = ObserveTravellersUseCase(FakeUserPreferencesRepository(initialTravellers = family)),
        )

        assertEquals(family, load(vienna, from, to).travellers)
        assertEquals(Travellers.SOLO, loadKnowledge(vienna, from, to).travellers, "Senza preferenza: una persona")
    }

    @Test
    fun `senza luoghi né eventi l'itinerario si chiede comunque`() = runTest {
        poiRepository.result = DataResult.Failure(DataError.NoConnection)
        eventRepository.result = DataResult.Failure(DataError.Timeout)
        assistant.planResult = DataResult.Success(TripPlan(days = listOf(DayPlan(from, "Arrivo", listOf(stop("Hofburg"))))))

        val plan = planTrip(vienna, from, to).successData()

        val knowledge = assistant.planRequests.single().first
        assertTrue(knowledge.places.isEmpty() && knowledge.events.isEmpty())
        assertEquals("Hofburg", plan.days.single().stops.single().name)
    }

    @Test
    fun `tiene solo i giorni del viaggio, in ordine, e ogni luogo una volta sola`() = runTest {
        val museumStop = { part: DayPart -> stop("KHM", part, StopTarget.Place(museum)) }
        assistant.planResult = DataResult.Success(
            TripPlan(
                days = listOf(
                    DayPlan(from.plusDays(1), "Musei", listOf(stop("Caffè", DayPart.EVENING), museumStop(DayPart.MORNING), stop(" "))),
                    DayPlan(from, "Arrivo", listOf(stop("Mercatino", DayPart.EVENING, StopTarget.Event(market)))),
                    DayPlan(from.plusDays(1), "Doppione", listOf(stop("Altro"))),
                    DayPlan(from.plusDays(2), "Di nuovo il museo", listOf(museumStop(DayPart.AFTERNOON))),
                    DayPlan(to.plusDays(1), "Dopo la partenza", listOf(stop("Fuori"))),
                ),
                packing = listOf(PackingGroup("Abbigliamento", listOf("Cappotto", "Cappotto", " ")), PackingGroup("Vuoto", emptyList())),
                tips = listOf("Usa i mezzi", "", "Usa i mezzi"),
            ),
            DataOrigin.CACHE,
        )

        val result = planTrip(vienna, from, to)
        val plan = result.successData()

        assertEquals(listOf(from, from.plusDays(1)), plan.days.map { it.date }, "Giorni fuori viaggio, doppi o rimasti vuoti scartati")
        assertEquals(listOf("KHM", "Caffè"), plan.days[1].stops.map { it.name }, "Prima la mattina, poi la sera")
        assertEquals(listOf(PackingGroup("Abbigliamento", listOf("Cappotto"))), plan.packing)
        assertEquals(listOf("Usa i mezzi"), plan.tips)
        assertEquals(DataOrigin.CACHE, (result as DataResult.Success).origin)
    }

    @Test
    fun `un itinerario senza giorni utilizzabili è una risposta non valida`() = runTest {
        assistant.planResult = DataResult.Success(TripPlan(days = listOf(DayPlan(to.plusDays(3), "Fuori", listOf(stop("X"))))))

        assertEquals(DataError.InvalidResponse, planTrip(vienna, from, to).failureError())
    }

    @Test
    fun `gli errori dell'assistente arrivano alla UI`() = runTest {
        assistant.planResult = DataResult.Failure(DataError.RateLimited)

        assertEquals(DataError.RateLimited, planTrip(vienna, from, to).failureError())
    }

    @Test
    fun `la distanza della giornata somma i tratti tra tappe con posizione`() {
        val near = poi("near", location = TestData.VIENNA_HUB)
        val day = DayPlan(from, "Giro", listOf(stop("A", target = StopTarget.Place(museum)), stop("Senza posizione"), stop("B", target = StopTarget.Place(near))))

        assertEquals(TestData.VIENNA_CENTER.distanceTo(TestData.VIENNA_HUB), day.straightLineMeters, 0.1)
    }

    @Test
    fun `alla domanda invia gli ultimi messaggi, cominciando sempre da una domanda`() = runTest {
        val conversation = listOf(
            ChatMessage(ChatRole.USER, "Primo"),
            ChatMessage(ChatRole.ASSISTANT, "Risposta 1"),
            ChatMessage(ChatRole.USER, "Secondo"),
            ChatMessage(ChatRole.ASSISTANT, "Risposta 2"),
            ChatMessage(ChatRole.USER, "Terzo"),
        )
        assistant.answerResult = DataResult.Success("  Prova il Tafelspitz.  ")

        val answer = askAssistant(knowledge(), conversation).successData()

        assertEquals("Prova il Tafelspitz.", answer)
        assertEquals(listOf("Secondo", "Risposta 2", "Terzo"), assistant.conversations.single().map { it.text })
    }

    @Test
    fun `una risposta vuota è un errore e la domanda deve essere dell'utente`() = runTest {
        assistant.answerResult = DataResult.Success(" ")

        assertEquals(DataError.InvalidResponse, askAssistant(knowledge(), listOf(ChatMessage(ChatRole.USER, "Ciao"))).failureError())
        assertFailsWith<IllegalArgumentException> { askAssistant(knowledge(), listOf(ChatMessage(ChatRole.ASSISTANT, "Ciao"))) }
    }
}
