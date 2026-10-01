package com.partimo.app.ui.place

import com.partimo.app.navigation.PlaceDetailDestination
import com.partimo.app.testing.MainDispatcherRule
import com.partimo.app.testing.successData
import com.partimo.app.ui.common.UiState
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.model.poi.WikipediaPage
import com.partimo.domain.testing.FakePoiArticleRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.usecase.GetPoiDetailsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val cathedral = TestData.poi(
        "wikipedia:it:83456",
        "Duomo di Vienna",
        category = PoiCategory.RELIGIOUS_SITE,
        photoUrl = "https://img.test/card.jpg",
        wikipediaPage = WikipediaPage("it", "Duomo di Vienna"),
    )
    private val articles = FakePoiArticleRepository(DataResult.Success(TestData.article()))

    private fun createViewModel() = PlaceDetailViewModel(GetPoiDetailsUseCase(articles), cathedral)

    @Test
    fun `mostra subito il luogo e poi carica descrizione e storia`() = runTest {
        val viewModel = createViewModel()

        assertEquals(UiState.Loading, viewModel.uiState.value.details)
        assertEquals("https://img.test/card.jpg", viewModel.uiState.value.imageUrl, "Durante il caricamento c'è la foto della card")

        advanceUntilIdle()
        val state = viewModel.uiState.value
        val details = state.details.successData()

        assertEquals(cathedral, articles.requestedPois.single())
        assertEquals(listOf("Origini", "Età moderna"), details.history.map { it.title })
        assertEquals("https://upload.test/stephansdom.jpg", state.imageUrl, "Poi la foto in alta risoluzione della voce")
    }

    @Test
    fun `dopo un errore riprova ignorando la cache`() = runTest {
        articles.result = DataResult.Failure(DataError.NoConnection)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(UiState.Error(DataError.NoConnection), viewModel.uiState.value.details)

        articles.result = DataResult.Success(TestData.article())
        viewModel.retry()
        advanceUntilIdle()

        assertIs<UiState.Success<*>>(viewModel.uiState.value.details)
        assertEquals(listOf(false, true), articles.forceRefreshFlags)
    }
}

class PlaceDetailDestinationTest {

    @Test
    fun `la rotta conserva tutti i dati del luogo`() {
        val poi = TestData.poi(
            "wikipedia:it:1510212",
            "Colosseo",
            category = PoiCategory.MONUMENT,
            rating = null,
            reviewCount = null,
            description = "Antico anfiteatro romano a Roma",
            tags = setOf(PoiTag.INSTAGRAMMABLE, PoiTag.PANORAMIC),
            location = GeoPoint(41.890278, 12.492222),
            photoUrl = "https://img.test/colosseo.jpg",
            popularity = 0.97,
            wikipediaPage = WikipediaPage("it", "Colosseo"),
        )

        assertEquals(poi, PlaceDetailDestination.from(poi).toPointOfInterest())
    }

    @Test
    fun `valori sconosciuti ripiegano su valori sicuri`() {
        val route = PlaceDetailDestination.from(TestData.poi("x", "Luogo"))
            .copy(category = "SCONOSCIUTA", tags = listOf("NON_ESISTE"), wikipediaTitle = null, popularity = 3.0)

        val poi = route.toPointOfInterest()

        assertEquals(PoiCategory.ATTRACTION, poi.category)
        assertEquals(emptySet(), poi.tags)
        assertNull(poi.wikipediaPage)
        assertEquals(1.0, poi.popularity)
    }
}

class MapsLinksTest {

    @Test
    fun `naviga apre le indicazioni di Google Maps fino alle coordinate del luogo`() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=41.890278%2C12.492222",
            googleMapsDirectionsUrl(GeoPoint(41.890278, 12.492222)),
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=-33.856784%2C151.215297",
            googleMapsDirectionsUrl(GeoPoint(-33.856784, 151.215297)),
            "Coordinate negative e punto decimale indipendenti dalla lingua del telefono",
        )
    }

    @Test
    fun `i trasporti aprono su Google Maps il percorso con i mezzi dal nodo di arrivo al centro`() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&origin=48.110300%2C16.569700&destination=48.208500%2C16.373100&travelmode=transit",
            googleMapsTransitUrl(origin = GeoPoint(48.1103, 16.5697), destination = GeoPoint(48.2085, 16.3731)),
        )
    }

    @Test
    fun `treni e bus tra due città si cercano su Google Maps per nome`() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&origin=Milano&destination=Monaco%20di%20Baviera&travelmode=transit",
            googleMapsTransitUrl("Milano", "Monaco di Baviera"),
        )
    }

    @Test
    fun `la ricerca per nome apre la scheda del locale e i link di Maps preferiscono l'app`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Figlm%C3%BCller%2C%20Wollzeile%205%2C%20Vienna",
            googleMapsSearchUrl("Figlmüller, Wollzeile 5, Vienna"),
        )
        assertTrue(isGoogleMapsUrl(googleMapsSearchUrl("Figlmüller")))
        assertTrue(isGoogleMapsUrl("https://maps.google.com/?cid=123456789"))
        assertFalse(isGoogleMapsUrl("https://www.google.com/travel/flights?q=Flights"))
        assertFalse(isGoogleMapsUrl("https://www.booking.com/searchresults.it.html?ss=Vienna"))
    }
}
