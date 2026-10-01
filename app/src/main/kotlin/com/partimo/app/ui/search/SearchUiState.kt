package com.partimo.app.ui.search

import com.partimo.app.ui.common.UiState
import com.partimo.domain.common.DataError
import com.partimo.domain.model.Destination
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.place.DeparturePoint
import com.partimo.domain.model.place.DestinationSuggestion
import com.partimo.domain.model.saved.SavedTrip
import java.time.LocalDate

/** Stato della schermata iniziale "Dove vuoi andare?". */
data class SearchUiState(
    val today: LocalDate,
    /** "Prossimi giorni" e i dodici mesi successivi. */
    val periods: List<TravelPeriod> = TravelPeriod.selectable(today),
    val period: TravelPeriod = TravelPeriod.NextDays,
    /** Punto di partenza scelto dall'utente; `null` finché non ne indica uno. */
    val departure: DeparturePoint? = null,
    /** Città trovate; `null` finché il testo digitato è troppo corto per cercare. */
    val results: UiState<List<CityPlace>>? = null,
    /** Mete consigliate; `null` finché l'utente non tocca "Consigliami". */
    val recommendations: UiState<List<DestinationSuggestion>>? = null,
    /** Città in preparazione (ricerca dell'aeroporto): mostra un indicatore sulla voce toccata. */
    val preparingCityId: String? = null,
    /** Apertura della dashboard da eseguire; la UI la consuma e poi lo notifica. */
    val pendingNavigation: PendingNavigation? = null,
    val preparationError: PreparationError? = null,
    /** Viaggi salvati, da riaprire con un tocco (prima quelli in arrivo). */
    val savedTrips: List<SavedTrip> = emptyList(),
)

data class PendingNavigation(val destination: Destination, val period: TravelPeriod)

data class PreparationError(val cityName: String, val error: DataError)
