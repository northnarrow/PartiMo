package com.partimo.app.ui.dashboard

import com.partimo.app.ui.common.UiState
import com.partimo.domain.model.ScoredOffer
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.TripContext
import com.partimo.domain.model.deal.PriceChange
import com.partimo.domain.model.dining.Restaurant
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.poi.SeasonalHighlights
import com.partimo.domain.model.stay.AccommodationOffer
import com.partimo.domain.model.transit.TransitRoute
import java.time.Instant
import java.time.LocalDate

/** Sezioni della dashboard: ognuna ha il suo pulsante nella barra in basso e si carica in modo indipendente. */
enum class DashboardSection { FLIGHTS, STAYS, HIGHLIGHTS, TRANSIT, RESTAURANTS }

/** Messaggi temporanei (snackbar) mostrati dopo un'azione dell'utente. */
enum class DashboardMessage { ALERT_ENABLED, ALERT_ENABLED_WITHOUT_NOTIFICATIONS, ALERT_DISABLED, ALERT_NEEDS_DEPARTURE }

/** Esito di "Aggiorna": variazione dei prezzi migliori rispetto al caricamento precedente. */
data class RefreshSummary(val flight: PriceChange?, val stay: PriceChange?)

/** Stato aggregato della dashboard di viaggio: ogni modulo ha il proprio [UiState]. */
data class TripDashboardUiState(
    val trip: TripContext,
    val period: TravelPeriod,
    /** "Prossimi giorni" e i dodici mesi successivi. */
    val periods: List<TravelPeriod>,
    val today: LocalDate,
    val selectedSection: DashboardSection = DashboardSection.FLIGHTS,
    val photoSpotsOnly: Boolean = false,
    val flights: UiState<List<ScoredOffer<FlightOffer>>> = UiState.Loading,
    val stays: UiState<List<ScoredOffer<AccommodationOffer>>> = UiState.Loading,
    val highlights: UiState<SeasonalHighlights> = UiState.Loading,
    val transit: UiState<List<TransitRoute>> = UiState.Loading,
    val restaurants: UiState<List<Restaurant>> = UiState.Loading,
    /** `true` durante un aggiornamento forzato ("Aggiorna" o pull-to-refresh). */
    val isRefreshing: Boolean = false,
    val isDemoMode: Boolean = false,
    /** Viaggio seguito: PartiMo avvisa quando voli o alloggi diventano davvero convenienti. */
    val alertEnabled: Boolean = false,
    /** Ora dell'ultimo caricamento completo delle offerte. */
    val pricesUpdatedAt: Instant? = null,
    /** Da mostrare una sola volta; la UI lo consuma e lo notifica al ViewModel. */
    val refreshSummary: RefreshSummary? = null,
    val message: DashboardMessage? = null,
)
