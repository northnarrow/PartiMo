package com.partimo.domain.usecase

import com.partimo.domain.common.DataResult
import com.partimo.domain.common.map
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.flight.AnywhereQuery
import com.partimo.domain.model.flight.CheapDestination
import com.partimo.domain.model.flight.FareSnapshot
import com.partimo.domain.model.flight.PriceCalendar
import com.partimo.domain.repository.FlightInsightsRepository
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * Il mese più conveniente: la tariffa più bassa di ogni mese (andata e ritorno, a persona) per soggiorni da un
 * fine settimana a una settimana, per i chip dei mesi.
 */
class GetMonthPricesUseCase(private val repository: FlightInsightsRepository) {
    val isAvailable: Boolean get() = repository.isAvailable

    suspend operator fun invoke(originIata: String, destinationIata: String, forceRefresh: Boolean = false): DataResult<Map<YearMonth, FareSnapshot>> =
        repository.cheapestByMonth(originIata, destinationIata, TravelPeriod.FLEXIBLE_STAY_NIGHTS, forceRefresh)
}

/**
 * Calendario dei prezzi di un mese per giorno di partenza. Con le date scelte valgono le loro notti (quale giorno
 * conviene per lo stesso soggiorno), altrimenti i soggiorni da un fine settimana a una settimana.
 */
class GetPriceCalendarUseCase(private val repository: FlightInsightsRepository) {
    val isAvailable: Boolean get() = repository.isAvailable

    suspend operator fun invoke(
        originIata: String,
        destinationIata: String,
        month: YearMonth,
        period: TravelPeriod,
        forceRefresh: Boolean = false,
    ): DataResult<PriceCalendar> {
        val stayNights = stayNightsFor(period)
        return repository.cheapestByDay(originIata, destinationIata, month, stayNights, forceRefresh)
            .map { fares -> PriceCalendar(month, stayNights, fares.filterKeys { YearMonth.from(it) == month }) }
    }

    companion object {
        fun stayNightsFor(period: TravelPeriod): LongRange =
            if (period is TravelPeriod.Dates) period.nights..period.nights else TravelPeriod.FLEXIBLE_STAY_NIGHTS
    }
}

/**
 * «Ovunque»: le mete più economiche dalla città di partenza nel periodo scelto, dalla più conveniente. La città
 * di partenza non compare tra le mete.
 */
class FindCheapDestinationsUseCase(
    private val repository: FlightInsightsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    val isAvailable: Boolean get() = repository.isAvailable

    suspend operator fun invoke(originIata: String, period: TravelPeriod, forceRefresh: Boolean = false): DataResult<List<CheapDestination>> {
        val query = AnywhereQuery.of(originIata, period, LocalDate.now(clock))
        return repository.cheapestDestinations(query, forceRefresh).map { destinations ->
            destinations
                .filter { it.airportIata != originIata && query.matches(it.fare.departureDate, it.fare.returnDate) }
                .sortedBy { it.fare.price.amount }
                .distinctBy { it.cityCode }
        }
    }
}
