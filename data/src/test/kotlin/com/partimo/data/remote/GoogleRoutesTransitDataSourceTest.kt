package com.partimo.data.remote

import com.partimo.data.remote.routes.GoogleRoutesApi
import com.partimo.data.remote.routes.GoogleRoutesTransitDataSource
import com.partimo.data.remote.routes.parseDuration
import com.partimo.data.remote.routes.toAllowedTravelModes
import com.partimo.data.remote.routes.vehicleTypeToMode
import com.partimo.data.testing.bodyText
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.model.transit.TransitMode
import com.partimo.domain.model.transit.TransitPreference
import com.partimo.domain.model.transit.TransitRouteQuery
import com.partimo.domain.testing.TestData
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleRoutesTransitDataSourceTest {

    private val routesJson = """
        {"routes": [{"duration": "810s", "distanceMeters": 3140, "legs": [{"steps": [
          {"travelMode": "WALK", "staticDuration": "120s", "distanceMeters": 150},
          {"travelMode": "WALK", "staticDuration": "60s", "distanceMeters": 80},
          {"travelMode": "TRANSIT", "staticDuration": "420s", "distanceMeters": 2800,
           "transitDetails": {
             "stopDetails": {
               "departureStop": {"name": "Hauptbahnhof"}, "departureTime": "2026-09-30T08:05:00Z",
               "arrivalStop": {"name": "Stephansplatz"}, "arrivalTime": "2026-09-30T08:12:00Z"},
             "headsign": "Leopoldau",
             "transitLine": {"agencies": [{"name": "Wiener Linien"}], "name": "U1", "nameShort": "U1",
                             "color": "#e3000f", "vehicle": {"name": {"text": "Metropolitana"}, "type": "SUBWAY"}},
             "stopCount": 4}},
          {"travelMode": "WALK", "staticDuration": "90s", "distanceMeters": 110}
        ]}]}]}
    """.trimIndent()

    @Test
    fun `converte i passi in tratti con orari e accorpa la camminata`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockHttpClient { request ->
            requests += request
            respond(routesJson, HttpStatusCode.OK, jsonHeaders)
        }
        val source = GoogleRoutesTransitDataSource(GoogleRoutesApi(client, "test-key", "https://routes.test/"), inMemoryCache(), "it")
        val query = TransitRouteQuery(
            origin = TestData.VIENNA_HUB,
            destination = TestData.VIENNA_CENTER,
            departureTime = Instant.parse("2026-09-30T08:00:30Z"),
            allowedModes = setOf(TransitMode.METRO, TransitMode.BUS),
            preference = TransitPreference.FEWER_TRANSFERS,
        )

        val route = source.routes(query, forceRefresh = false).data.single()

        assertEquals(3, route.legs.size)
        val (firstWalk, metro, lastWalk) = route.legs
        assertEquals(TransitMode.WALK, firstWalk.mode)
        assertEquals(Duration.ofMinutes(3), firstWalk.duration)
        assertEquals(Instant.parse("2026-09-30T08:02:00Z"), firstWalk.departureTime)
        assertEquals("Hauptbahnhof", firstWalk.arrivalStop?.name)

        assertEquals(TransitMode.METRO, metro.mode)
        assertEquals("U1", metro.line?.shortName)
        assertEquals("Wiener Linien", metro.line?.agencyName)
        assertEquals("Leopoldau", metro.headsign)
        assertEquals(4, metro.stopCount)

        assertEquals("Stephansplatz", lastWalk.departureStop?.name)
        assertEquals(Instant.parse("2026-09-30T08:13:30Z"), lastWalk.arrivalTime)

        val request = requests.single()
        assertEquals("/directions/v2:computeRoutes", request.url.encodedPath)
        assertTrue(request.headers[GoogleRoutesApi.FIELD_MASK_HEADER].orEmpty().contains("routes.legs.steps.transitDetails"))
        val body = Json.parseToJsonElement(request.bodyText()).jsonObject
        assertEquals("TRANSIT", body.getValue("travelMode").jsonPrimitive.content)
        assertEquals("2026-09-30T08:00:00Z", body.getValue("departureTime").jsonPrimitive.content)
        val preferences = body.getValue("transitPreferences").jsonObject
        assertEquals(listOf("BUS", "SUBWAY"), preferences.getValue("allowedTravelModes").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("FEWER_TRANSFERS", preferences.getValue("routingPreference").jsonPrimitive.content)
    }

    @Test
    fun `i tipi di veicolo vengono tradotti nelle modalità di dominio`() {
        assertEquals(TransitMode.METRO, vehicleTypeToMode("METRO_RAIL"))
        assertEquals(TransitMode.TRAIN, vehicleTypeToMode("COMMUTER_TRAIN"))
        assertEquals(TransitMode.BUS, vehicleTypeToMode("TROLLEYBUS"))
        assertEquals(TransitMode.CABLE_CAR, vehicleTypeToMode("FUNICULAR"))
        assertEquals(TransitMode.OTHER, vehicleTypeToMode(null))
    }

    @Test
    fun `nessun filtro sui mezzi quando sono ammessi tutti`() {
        assertNull(TransitMode.PUBLIC_MODES.toAllowedTravelModes())
        assertEquals(listOf("LIGHT_RAIL"), setOf(TransitMode.TRAM).toAllowedTravelModes())
    }

    @Test
    fun `le durate di Routes API vengono interpretate`() {
        assertEquals(Duration.ofSeconds(123), parseDuration("123s"))
        assertEquals(Duration.ofMillis(1_500), parseDuration("1.5s"))
        assertEquals(Duration.ZERO, parseDuration(null))
    }
}
