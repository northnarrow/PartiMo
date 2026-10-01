package com.partimo.app.ui.search

import com.partimo.app.ui.common.UiState
import com.partimo.domain.common.DataError
import com.partimo.domain.model.Destination
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.booking.Booking
import com.partimo.domain.model.flight.CheapDestination
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
    /** Chi parte: vale per tutti i viaggi. */
    val travellers: Travellers = Travellers.SOLO,
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
    /** `true` se si possono cercare le mete più economiche (token di Travelpayouts). */
    val anywhereAvailable: Boolean = false,
    /** Mete più economiche dalla città di partenza («Ovunque»); `null` finché non si tocca il pulsante. */
    val anywhere: UiState<List<CheapDestination>>? = null,
    /** Prezzo massimo a persona scelto per «Ovunque»; `null` = tutte le mete. */
    val anywhereMaxPrice: Int? = null,
    /** Prenotazioni non ancora passate, dalla più vicina: la prima si mostra nella riga «Le mie prenotazioni». */
    val upcomingBookings: List<Booking> = emptyList(),
) {
    /** Mete di «Ovunque» entro il prezzo massimo scelto. */
    val anywhereShown: List<CheapDestination>
        get() = (anywhere as? UiState.Success)?.data.orEmpty()
            .filter { destination -> anywhereMaxPrice == null || destination.fare.price.amount <= anywhereMaxPrice.toBigDecimal() }

    companion object {
        /** Prezzi massimi proposti per «Ovunque», in euro a persona. */
        val ANYWHERE_PRICE_STEPS = listOf(50, 100, 200)
    }
}

data class PendingNavigation(val destination: Destination, val period: TravelPeriod)

data class PreparationError(val cityName: String, val error: DataError)
