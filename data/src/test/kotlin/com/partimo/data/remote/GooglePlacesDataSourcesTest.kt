package com.partimo.data.remote

import com.partimo.data.config.AndroidAppIdentity
import com.partimo.data.network.AndroidAppHeaders
import com.partimo.data.remote.places.GooglePlacesApi
import com.partimo.data.remote.places.GooglePlacesPoiDataSource
import com.partimo.data.remote.places.GooglePlacesRestaurantDataSource
import com.partimo.data.remote.places.categoryFromPlaceTypes
import com.partimo.data.remote.places.placesMinRating
import com.partimo.data.testing.bodyText
import com.partimo.data.testing.inMemoryCache
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.model.dining.PriceLevel
import com.partimo.domain.model.dining.RestaurantSearchQuery
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiQuery
import com.partimo.domain.model.poi.SeasonalTheme
import com.partimo.domain.service.SeasonalCalendar
import com.partimo.domain.testing.TestData
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GooglePlacesDataSourcesTest {

    private val restaurantsJson = """
        {"places": [
          {"id": "p1", "displayName": {"text": "Trattoria Da Anna", "languageCode": "it"},
           "rating": 4.3, "userRatingCount": 310, "priceLevel": "PRICE_LEVEL_MODERATE",
           "formattedAddress": "Vienna", "location": {"latitude": 48.207, "longitude": 16.366},
           "primaryTypeDisplayName": {"text": "Ristorante italiano"},
           "photos": [{"name": "places/p1/photos/abc", "widthPx": 800, "heightPx": 600}],
           "currentOpeningHours": {"openNow": true}, "googleMapsUri": "https://maps.test/?cid=1"},
          {"id": "p2", "displayName": {"text": "Locale senza fascia di prezzo"}, "rating": 4.8}
        ]}
    """.trimIndent()

    private fun placesJson(vararg places: Pair<String, String>): String = places.joinToString(
        prefix = """{"places": [""",
        postfix = "]}",
    ) { (id, type) ->
        """{"id": "$id", "displayName": {"text": "Luogo $id"}, "types": ["$type"],
            "location": {"latitude": 48.2, "longitude": 16.37}, "rating": 4.6, "userRatingCount": 900}"""
    }

    @Test
    fun `invia i vincoli di budget e interpreta i ristoranti`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockHttpClient { request ->
            requests += request
            respond(restaurantsJson, HttpStatusCode.OK, jsonHeaders)
        }
        val api = GooglePlacesApi(client, apiKey = "test-key", baseUrl = "https://places.test/v1/")
        val source = GooglePlacesRestaurantDataSource(api, inMemoryCache(), languageCode = "it")
        val query = RestaurantSearchQuery(
            location = TestData.VIENNA_CENTER,
            priceLevels = setOf(PriceLevel.MODERATE, PriceLevel.INEXPENSIVE),
            minRating = 4.3,
        )

        val restaurants = source.searchRestaurants(query, forceRefresh = false).data

        val request = requests.single()
        assertEquals("/v1/places:searchText", request.url.encodedPath)
        assertEquals("test-key", request.headers[GooglePlacesApi.API_KEY_HEADER])
        assertTrue(request.headers[GooglePlacesApi.FIELD_MASK_HEADER].orEmpty().contains("places.priceLevel"))

        val body = Json.parseToJsonElement(request.bodyText()).jsonObject
        assertEquals("restaurant", body.getValue("includedType").jsonPrimitive.content)
        assertEquals(4.0, body.getValue("minRating").jsonPrimitive.double, "4,3 va arrotondato per difetto a 4,0")
        assertEquals(
            listOf("PRICE_LEVEL_INEXPENSIVE", "PRICE_LEVEL_MODERATE"),
            body.getValue("priceLevels").jsonArray.map { it.jsonPrimitive.content },
        )
        assertFalse("openNow" in body, "I campi nulli non vanno inviati")

        val trattoria = restaurants.first()
        assertEquals(PriceLevel.MODERATE, trattoria.priceLevel)
        assertEquals(true, trattoria.isOpenNow)
        assertEquals("https://places.test/v1/places/p1/photos/abc/media?maxWidthPx=640&key=test-key", trattoria.photoUrl)
        assertNull(restaurants[1].priceLevel)
    }

    @Test
    fun `unisce attrazioni e temi stagionali ereditando mesi e categoria del tema`() = runTest {
        val christmasTheme = SeasonalTheme("mercatini di Natale", PoiCategory.SEASONAL_EVENT, SeasonalCalendar.CHRISTMAS_MONTHS)
        val client = mockHttpClient { request ->
            val body = if ("Natale" in request.bodyText()) {
                placesJson("xmas" to "tourist_attraction", "shared" to "plaza")
            } else {
                placesJson("museum" to "museum", "shared" to "plaza")
            }
            respond(body, HttpStatusCode.OK, jsonHeaders)
        }
        val source = GooglePlacesPoiDataSource(GooglePlacesApi(client, "test-key", "https://places.test/v1/"), inMemoryCache(), "it")

        val pois = source.pointsOfInterest(
            PoiQuery(TestData.VIENNA_CENTER, Month.DECEMBER, seasonalThemes = listOf(christmasTheme)),
            forceRefresh = false,
        ).data.associateBy { it.id }

        assertEquals(setOf("xmas", "shared", "museum"), pois.keys)
        assertEquals(PoiCategory.SEASONAL_EVENT, pois.getValue("xmas").category)
        assertEquals(SeasonalCalendar.CHRISTMAS_MONTHS, pois.getValue("shared").activeMonths)
        assertEquals(PoiCategory.MUSEUM, pois.getValue("museum").category)
    }

    @Test
    fun `se una ricerca tematica fallisce restano le attrazioni generiche`() = runTest {
        val client = mockHttpClient { request ->
            if ("Natale" in request.bodyText()) {
                respond("{}", HttpStatusCode.InternalServerError, jsonHeaders)
            } else {
                respond(placesJson("museum" to "museum"), HttpStatusCode.OK, jsonHeaders)
            }
        }
        val source = GooglePlacesPoiDataSource(GooglePlacesApi(client, "test-key", "https://places.test/v1/"), inMemoryCache(), "it")
        val theme = SeasonalTheme("mercatini di Natale", PoiCategory.SEASONAL_EVENT, SeasonalCalendar.CHRISTMAS_MONTHS)

        val pois = source.pointsOfInterest(PoiQuery(TestData.VIENNA_CENTER, Month.DECEMBER, seasonalThemes = listOf(theme)), false).data

        assertEquals(listOf("museum"), pois.map { it.id })
    }

    @Test
    fun `con una chiave limitata alle app Android invia package e certificato dell'app`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockHttpClient { request ->
            requests += request
            respond(restaurantsJson, HttpStatusCode.OK, jsonHeaders)
        }
        val identity = AndroidAppIdentity(packageName = "com.partimo.app", certificateSha1 = "A9993E364706816ABA3E25717850C26C9CD0D89D")
        val withIdentity = GooglePlacesApi(client, "test-key", "https://places.test/v1/", androidApp = identity)
        val withoutIdentity = GooglePlacesApi(client, "test-key", "https://places.test/v1/")

        GooglePlacesRestaurantDataSource(withIdentity, inMemoryCache(), "it").searchRestaurants(RestaurantSearchQuery(TestData.VIENNA_CENTER), false)
        GooglePlacesRestaurantDataSource(withoutIdentity, inMemoryCache(), "it").searchRestaurants(RestaurantSearchQuery(TestData.VIENNA_CENTER), false)

        assertEquals("com.partimo.app", requests[0].headers[AndroidAppHeaders.PACKAGE])
        assertEquals("A9993E364706816ABA3E25717850C26C9CD0D89D", requests[0].headers[AndroidAppHeaders.CERTIFICATE])
        assertNull(requests[1].headers[AndroidAppHeaders.PACKAGE], "Senza identità nessuna intestazione Android")
    }

    @Test
    fun `minRating viene arrotondato per difetto al mezzo punto`() {
        assertEquals(4.0, placesMinRating(4.3))
        assertEquals(4.5, placesMinRating(4.5))
        assertEquals(4.5, placesMinRating(4.9))
    }

    @Test
    fun `i tipi di Places vengono mappati sulle categorie di dominio`() {
        assertEquals(PoiCategory.MUSEUM, categoryFromPlaceTypes(listOf("sconosciuto", "museum")))
        assertEquals(PoiCategory.VIEWPOINT, categoryFromPlaceTypes(listOf("observation_deck")))
        assertEquals(PoiCategory.ATTRACTION, categoryFromPlaceTypes(emptyList()))
    }
}
