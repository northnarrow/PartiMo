package com.partimo.data.remote.gemini

import com.partimo.data.cache.ResponseCache
import com.partimo.data.network.NetworkJson
import com.partimo.data.repository.DefaultTravelAssistantRepository
import com.partimo.data.testing.bodyText
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.plan.ChatMessage
import com.partimo.domain.model.plan.ChatRole
import com.partimo.domain.model.plan.DayPart
import com.partimo.domain.model.plan.StopTarget
import com.partimo.domain.model.plan.TripInterest
import com.partimo.domain.model.plan.TripPace
import com.partimo.domain.model.plan.TripPreferences
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.failureError
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeminiTravelAssistantDataSourceTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val artAndFood = TripPreferences(interests = setOf(TripInterest.ART, TripInterest.FOOD))

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/gemini/$name")) { "Fixture mancante: $name" }.readText()

    private fun source(body: String = fixture("plan_vienna.json"), cache: ResponseCache = inMemoryCache()) =
        GeminiTravelAssistantDataSource(
            api = GeminiApi(
                client = mockHttpClient { request ->
                    requests += request
                    respond(body, HttpStatusCode.OK, jsonHeaders)
                },
                apiKey = "chiave-di-prova",
                models = listOf(GeminiModel("gemini-flash-lite-latest")),
                baseUrl = "https://gemini.test/v1beta/",
            ),
            cache = cache,
            clock = TestData.FIXED_CLOCK,
        )

    private fun HttpRequestData.json(): JsonObject = NetworkJson.parseToJsonElement(bodyText()).jsonObject

    private fun JsonObject.firstText(field: String, index: Int = 0): String =
        (this[field]!!.let { if (field == "contents") it.jsonArray[index].jsonObject else it.jsonObject })["parts"]!!
            .jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content

    @Test
    fun `l'itinerario reale collega le tappe ai luoghi e agli eventi dell'app`() = runBlocking {
        val plan = source().planTrip(ViennaTrip.knowledge(), artAndFood, forceRefresh = false).data

        assertEquals((0L..4L).map { ViennaTrip.from.plusDays(it) }, plan.days.map { it.date })
        val arrival = plan.days.first()
        assertEquals("Arrivo e prime luci nel centro", arrival.title)
        val (duomo, cafe, market) = arrival.stops
        assertEquals(DayPart.AFTERNOON, duomo.dayPart)
        assertEquals("wikipedia:it:duomo", (duomo.target as StopTarget.Place).poi.id)
        assertEquals("Café Central", cafe.name)
        assertNull(cafe.target, "Locale suggerito dall'assistente, fuori dagli elenchi dell'app")
        assertEquals("wikidata:Q2", (market.target as StopTarget.Event).event.id)
        assertEquals(DayPart.EVENING, market.dayPart)
        assertTrue(arrival.tip!!.isNotBlank())
        assertEquals(listOf("wikipedia:it:albertina"), plan.days.last().stops.map { (it.target as StopTarget.Place).poi.id })
        assertTrue(plan.packing.isNotEmpty() && plan.packing.all { it.items.isNotEmpty() })
        assertTrue(plan.tips.isNotEmpty())

        val body = requests.single().json()
        assertContains(body.firstText("systemInstruction"), "Sei PartiMo")
        val prompt = body.firstText("contents")
        assertContains(prompt, "Viaggio a Vienna (Austria) dal 10 al 14 dicembre 2026: 5 giorni, 1 viaggiatore.")
        assertContains(prompt, "Interessi: arte e musei, cibo e cucina tipica.")
        assertContains(prompt, "L1 | Duomo di Santo Stefano | luogo di culto | cattedrale di Vienna")
        assertContains(prompt, "E2 | Wiener Christkindlmarkt | tutti i giorni del soggiorno (mercatino di Natale)")
        val config = body["generationConfig"]!!.jsonObject
        assertEquals("application/json", config["responseMimeType"]!!.jsonPrimitive.content)
        assertEquals(TravelPrompts.PLAN_SCHEMA, config["responseSchema"])
    }

    @Test
    fun `riaprendo l'itinerario non si richiama Gemini, rigenerando o cambiando preferenze sì`() = runBlocking {
        val source = source(cache = inMemoryCache())
        val knowledge = ViennaTrip.knowledge()

        assertEquals(DataOrigin.REMOTE, source.planTrip(knowledge, artAndFood, forceRefresh = false).origin)
        assertEquals(DataOrigin.CACHE, source.planTrip(knowledge, artAndFood, forceRefresh = false).origin)
        assertEquals(1, requests.size)

        source.planTrip(knowledge, artAndFood, forceRefresh = true)
        source.planTrip(knowledge, TripPreferences(pace = TripPace.INTENSE), forceRefresh = false)
        assertEquals(3, requests.size)
    }

    @Test
    fun `un itinerario senza giorni è una risposta non valida`() = runBlocking {
        val repository = DefaultTravelAssistantRepository(source(geminiResponse("""{"days":[],"packing":[],"tips":[]}""")), Dispatchers.Unconfined)

        assertEquals(DataError.InvalidResponse, repository.planTrip(ViennaTrip.knowledge(), artAndFood).failureError())
    }

    @Test
    fun `senza chiave l'assistente non è disponibile`() = runBlocking {
        val repository = DefaultTravelAssistantRepository(null, Dispatchers.Unconfined)

        assertEquals(false, repository.isAvailable)
        assertEquals(DataError.Unauthorized, repository.planTrip(ViennaTrip.knowledge(), artAndFood).failureError())
    }

    @Test
    fun `la risposta reale a una domanda tiene conto del viaggio e della conversazione`() = runBlocking {
        val conversation = listOf(
            ChatMessage(ChatRole.USER, "Ciao!"),
            ChatMessage(ChatRole.ASSISTANT, "Ciao, come posso aiutarti?"),
            ChatMessage(ChatRole.USER, "Cosa devo assolutamente mangiare a Vienna?"),
        )

        val answer = source(fixture("chat_vienna_food.json")).answer(ViennaTrip.knowledge(), conversation).data

        assertTrue(answer.startsWith("Ciao! A Vienna la tradizione culinaria"))
        assertContains(answer, "• Wiener Schnitzel: la celebre cotoletta")
        val body = requests.single().json()
        assertEquals(listOf("user", "model", "user"), body["contents"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        assertEquals("Cosa devo assolutamente mangiare a Vienna?", body.firstText("contents", index = 2))
        val system = body.firstText("systemInstruction")
        assertContains(system, "Viaggio dell'utente: Vienna (Austria) dal 10 al 14 dicembre 2026: 5 giorni, 1 viaggiatore.")
        assertContains(system, "Oggi è 30 settembre 2026.")
        assertContains(system, "• Weihnachtsmarkt am Spittelberg: tutti i giorni del soggiorno (mercatino di Natale)")
        assertContains(system, "Luoghi consigliati dall'app: Duomo di Santo Stefano, Hofburg")
        assertNull(body["generationConfig"]!!.jsonObject["responseMimeType"], "Le risposte sono testo libero")
    }

    @Test
    fun `il markdown dei modelli diventa testo semplice`() {
        val text = "## Dove mangiare\n* **Figlmüller**: la cotoletta\n- Plachutta, per il __Tafelspitz__"

        assertEquals(
            "Dove mangiare\n• Figlmüller: la cotoletta\n• Plachutta, per il Tafelspitz",
            GeminiTravelAssistantDataSource.plainText(text),
        )
    }
}
