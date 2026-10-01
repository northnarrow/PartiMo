package com.partimo.domain.usecase

import com.partimo.domain.common.getOrNull
import com.partimo.domain.model.Destination
import com.partimo.domain.model.Money
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.deal.Deal
import com.partimo.domain.model.deal.DealAlert
import com.partimo.domain.model.deal.DealKind
import com.partimo.domain.model.deal.PricePoint
import com.partimo.domain.model.deal.PriceWatch
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.place.DeparturePoint
import com.partimo.domain.model.stay.AccommodationOffer
import com.partimo.domain.model.stay.AccommodationSearchQuery
import com.partimo.domain.repository.PriceWatchRepository
import com.partimo.domain.service.DealDetector
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Prezzi seguiti dagli avvisi: stessa regola nella dashboard e nei controlli in background. */
object TrackedPrices {
    /** Un alloggio è "davvero conveniente" solo se è anche ben recensito. */
    const val GOOD_REVIEW_SCORE = 8.0

    fun cheapestFlight(offers: List<FlightOffer>): FlightOffer? = offers.minByOrNull { it.totalPrice.amount }

    fun cheapestGoodStay(offers: List<AccommodationOffer>): AccommodationOffer? =
        offers.filter { (it.reviewScore ?: 0.0) >= GOOD_REVIEW_SCORE }.minByOrNull { it.pricePerNight.amount }
}

/** Indica se un viaggio è seguito (campanella attiva nella dashboard). */
class ObservePriceAlertUseCase(private val repository: PriceWatchRepository) {

    operator fun invoke(departure: DeparturePoint?, destination: Destination, period: TravelPeriod): Flow<Boolean> {
        if (departure == null) return flowOf(false)
        val id = PriceWatch.idFor(departure.airport.iata, destination, period)
        return repository.watches.map { watches -> watches.any { it.id == id } }.distinctUntilChanged()
    }
}

/**
 * Attiva o disattiva l'avviso per un viaggio. All'attivazione i prezzi visti in quel momento diventano
 * il primo riferimento, così già il primo controllo in background può riconoscere un calo.
 */
class SetPriceAlertUseCase(
    private val repository: PriceWatchRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    @Suppress("LongParameterList")
    suspend operator fun invoke(
        enabled: Boolean,
        departure: DeparturePoint,
        destination: Destination,
        period: TravelPeriod,
        currentFlightPrice: Money? = null,
        currentStayPrice: Money? = null,
    ) {
        if (!enabled) {
            repository.remove(PriceWatch.idFor(departure.airport.iata, destination, period))
            return
        }
        val now = Instant.now(clock)
        repository.add(
            PriceWatch(
                departure = departure,
                destination = destination,
                period = period,
                createdAt = now,
                flightPrices = listOfNotNull(currentFlightPrice?.let { PricePoint(it, now) }),
                stayPrices = listOfNotNull(currentStayPrice?.let { PricePoint(it, now) }),
            ),
        )
    }
}

/**
 * Controllo periodico dei viaggi seguiti, eseguito in background: per ognuno cerca voli e alloggi
 * ignorando la cache, aggiorna lo storico dei prezzi e restituisce gli affari da notificare.
 * Gli avvisi di mesi ormai passati vengono rimossi. Una ricerca fallita (es. offline) non altera lo storico.
 */
class CheckPriceWatchesUseCase(
    private val repository: PriceWatchRepository,
    private val searchFlights: SearchFlightsUseCase,
    private val searchAccommodations: SearchAccommodationsUseCase,
    private val dealDetector: DealDetector = DealDetector(),
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend operator fun invoke(): List<DealAlert> {
        val today = LocalDate.now(clock)
        return repository.watches.first().mapNotNull { watch ->
            if (watch.period.isOver(today)) {
                repository.remove(watch.id)
                return@mapNotNull null
            }
            val (updated, deals) = check(watch, today)
            repository.update(updated)
            deals.takeIf { it.isNotEmpty() }?.let { DealAlert(updated, it) }
        }
    }

    private suspend fun check(watch: PriceWatch, today: LocalDate): Pair<PriceWatch, List<Deal>> = coroutineScope {
        val departureDate = watch.period.departureDate(today)
        val returnDate = watch.period.returnDate(today)
        val flights = async {
            val query = FlightSearchQuery(
                originIata = watch.departure.airport.iata,
                destinationIata = watch.destination.airportIata,
                departureDate = departureDate,
                returnDate = returnDate,
                // Come nella dashboard: con i prezzi di molte date si segue il volo più conveniente del periodo.
                flexibleDepartures = watch.period.departureWindow(today),
            )
            searchFlights(query, forceRefresh = true).getOrNull().orEmpty().map { it.offer }
        }
        val stays = async {
            val query = AccommodationSearchQuery(location = watch.destination.center, checkIn = departureDate, checkOut = returnDate)
            searchAccommodations(query, forceRefresh = true).getOrNull().orEmpty().map { it.offer }
        }

        val now = Instant.now(clock)
        var updated = watch
        val deals = mutableListOf<Deal>()
        TrackedPrices.cheapestFlight(flights.await())?.let { flight ->
            val track = track(flight.totalPrice, watch.flightPrices, watch.lastNotifiedFlight, now)
            updated = updated.copy(flightPrices = track.history, lastNotifiedFlight = track.lastNotified)
            track.dealUsualPrice?.let { deals += Deal(DealKind.FLIGHT, flight.carrierName, flight.totalPrice, it) }
        }
        TrackedPrices.cheapestGoodStay(stays.await())?.let { stay ->
            val track = track(stay.pricePerNight, watch.stayPrices, watch.lastNotifiedStay, now)
            updated = updated.copy(stayPrices = track.history, lastNotifiedStay = track.lastNotified)
            track.dealUsualPrice?.let { deals += Deal(DealKind.STAY, stay.name, stay.pricePerNight, it, stay.reviewScore) }
        }
        updated to deals
    }

    private class Track(val history: List<PricePoint>, val lastNotified: Money?, val dealUsualPrice: Money?)

    private fun track(price: Money, history: List<PricePoint>, lastNotified: Money?, now: Instant): Track {
        val assessment = dealDetector.assess(price, history, lastNotified)
        val newLastNotified = when {
            assessment.shouldNotify -> price
            // Il prezzo è tornato normale: il prossimo calo potrà essere notificato di nuovo.
            !assessment.isBelowThreshold -> null
            else -> lastNotified
        }
        return Track(
            history = (history + PricePoint(price, now)).takeLast(MAX_HISTORY),
            lastNotified = newLastNotified,
            dealUsualPrice = assessment.usualPrice.takeIf { assessment.shouldNotify },
        )
    }

    companion object {
        /** Controlli conservati per ogni prezzo seguito (circa una settimana con un controllo ogni 6 ore). */
        const val MAX_HISTORY = 30
    }
}
