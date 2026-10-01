package com.partimo.domain.usecase

import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.Destination
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.event.EventQuery
import com.partimo.domain.model.event.TripEvent
import com.partimo.domain.model.event.TripEvents
import com.partimo.domain.model.event.firstDayWithin
import com.partimo.domain.repository.EventRepository
import com.partimo.domain.repository.HolidayRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

/**
 * Eventi durante il soggiorno: mercatini di Natale, festival e ricorrenze della città, più le
 * festività nazionali, tenendo solo quelli che cadono tra arrivo e partenza.
 *
 * Le due fonti sono indipendenti: se una non risponde restano gli eventi dell'altra. Gli eventi
 * ricorrenti cambiano di rado, quindi si usa la cache ancora valida anche con «Aggiorna»: così non
 * si superano i limiti di richieste del servizio.
 */
class GetTripEventsUseCase(
    private val eventRepository: EventRepository,
    private val holidayRepository: HolidayRepository,
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
) {

    suspend operator fun invoke(
        destination: Destination,
        from: LocalDate,
        to: LocalDate,
        forceRefresh: Boolean = false,
    ): DataResult<TripEvents> = coroutineScope {
        require(!to.isBefore(from)) { "Il soggiorno finisce prima di cominciare: $from–$to" }
        val recurring = async { eventRepository.recurringEvents(EventQuery(destination.center), forceRefresh = false) }
        val holidays = (from.year..to.year).map { year ->
            async { holidayRepository.publicHolidays(destination.countryCode, year, forceRefresh) }
        }
        val results = listOf(recurring.await()) + holidays.awaitAll()
        val successes = results.filterIsInstance<DataResult.Success<List<TripEvent>>>()
        if (successes.isEmpty()) return@coroutineScope results.first() as DataResult.Failure

        val events = successes
            .flatMap { it.data }
            .distinctBy { it.id }
            .mapNotNull { event -> event.timing.firstDayWithin(from, to)?.let { firstDay -> event to firstDay } }
            .sortedWith(compareBy<Pair<TripEvent, LocalDate>>({ it.first.kind.order }, { it.second }, { it.first.name }))
            .map { it.first }
            .take(maxEvents)
        DataResult.Success(
            data = TripEvents(from = from, to = to, events = events),
            origin = combinedOrigin(successes.map { it.origin }),
        )
    }

    /** Prima ciò che si visita (mercatini, festival), poi le festività, utili per orari e chiusure. */
    private val EventKind.order: Int
        get() = when (this) {
            EventKind.CHRISTMAS_MARKET -> 0
            EventKind.RECURRING_EVENT -> 1
            EventKind.PUBLIC_HOLIDAY -> 2
        }

    private fun combinedOrigin(origins: List<DataOrigin>): DataOrigin = when {
        DataOrigin.STALE_CACHE in origins -> DataOrigin.STALE_CACHE
        origins.all { it == DataOrigin.CACHE } -> DataOrigin.CACHE
        else -> DataOrigin.REMOTE
    }

    private companion object {
        const val DEFAULT_MAX_EVENTS = 15
    }
}
