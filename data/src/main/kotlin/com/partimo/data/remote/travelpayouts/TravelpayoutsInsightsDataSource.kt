package com.partimo.data.remote.travelpayouts

import com.partimo.data.cache.CacheKey
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.local.BundledFlightCodes
import com.partimo.data.network.Fetched
import com.partimo.data.network.NetworkJson
import com.partimo.data.network.UnusableResponseException
import com.partimo.data.network.combinedOrigin
import com.partimo.data.network.mapNotNullSafely
import com.partimo.data.source.FlightInsightsDataSource
import com.partimo.domain.model.Money
import com.partimo.domain.model.flight.AnywhereQuery
import com.partimo.domain.model.flight.CheapDestination
import com.partimo.domain.model.flight.FareSnapshot
import com.partimo.domain.model.place.CityPlace
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.util.Locale

/**
 * Prezzi della Data API di Aviasales (Travelpayouts) per scegliere quando e dove andare: la tariffa più bassa di
 * ogni mese e di ogni giorno di partenza di una tratta (`grouped_prices`) e le mete più economiche da una città
 * (`prices_for_dates` senza destinazione, una tariffa per meta). Prezzi a persona, andata e ritorno, raccolti
 * dalle ricerche dei viaggiatori: reali ma da verificare.
 */
internal class TravelpayoutsInsightsDataSource(
    private val client: HttpClient,
    private val cache: ResponseCache,
    private val token: String,
    private val codes: BundledFlightCodes,
    private val baseUrl: String = TravelpayoutsFlightDataSource.DEFAULT_BASE_URL,
    private val currencyCode: String = "EUR",
) : FlightInsightsDataSource {

    override suspend fun cheapestByMonth(
        originIata: String,
        destinationIata: String,
        stayNights: LongRange,
        forceRefresh: Boolean,
    ): Fetched<Map<YearMonth, FareSnapshot>> {
        val response = fetchGrouped(cityCode(originIata), cityCode(destinationIata), GROUP_BY_MONTH, departureAt = null, stayNights, forceRefresh)
        return Fetched(response.data.fares { YearMonth.parse(it) }, response.origin)
    }

    override suspend fun cheapestByDay(
        originIata: String,
        destinationIata: String,
        month: YearMonth,
        stayNights: LongRange,
        forceRefresh: Boolean,
    ): Fetched<Map<LocalDate, FareSnapshot>> {
        val response = fetchGrouped(cityCode(originIata), cityCode(destinationIata), GROUP_BY_DAY, month.toString(), stayNights, forceRefresh)
        return Fetched(response.data.fares { LocalDate.parse(it) }, response.origin)
    }

    override suspend fun cheapestDestinations(query: AnywhereQuery, forceRefresh: Boolean): Fetched<List<CheapDestination>> {
        val origin = cityCode(query.originIata)
        // Con le date scelte una richiesta per quei giorni; altrimenti i mesi delle partenze (due se la finestra
        // dei prossimi giorni scavalca la fine del mese).
        val ranges = query.exactDates?.let { (from, to) -> listOf(from.toString() to to.toString()) }
            ?: departureMonths(query.departures).map { it.toString() to null }
        val responses = ranges.map { (departureAt, returnAt) -> fetchDestinations(origin, departureAt, returnAt, forceRefresh) }
        val destinations = responses
            .flatMap { it.data.fares }
            .sortedBy { it.price.content.toBigDecimalOrNull() }
            .mapNotNullSafely { fare -> fare.toCheapDestination(origin) }
            .distinctBy { it.cityCode }
        return Fetched(destinations, combinedOrigin(responses.map { it.origin }))
    }

    private suspend fun cityCode(iata: String): String = codes.airport(iata)?.cityCode ?: iata.uppercase(Locale.ROOT)

    private fun departureMonths(window: ClosedRange<LocalDate>): List<YearMonth> {
        val last = YearMonth.from(window.endInclusive)
        return generateSequence(YearMonth.from(window.start)) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.take(MAX_MONTHS).toList()
    }

    /** Tariffa più bassa per mese o per giorno di partenza ([departureAt] = "2026-12"), per soggiorni di [stayNights] notti. */
    private suspend fun fetchGrouped(
        origin: String,
        destination: String,
        groupBy: String,
        departureAt: String?,
        stayNights: LongRange,
        forceRefresh: Boolean,
    ): Fetched<TravelpayoutsGroupedResponse> = cache.getOrFetch(
        key = CacheKey.of("tp-grouped", origin, destination, groupBy, departureAt, stayNights.first, stayNights.last, currencyCode),
        ttl = CachePolicy.FLIGHT_PRICES,
        forceRefresh = forceRefresh,
        fetch = {
            client.get("${baseUrl}aviasales/v3/grouped_prices") {
                header(TravelpayoutsFlightDataSource.TOKEN_HEADER, token)
                parameter("origin", origin)
                parameter("destination", destination)
                parameter("group_by", groupBy)
                departureAt?.let { parameter("departure_at", it) }
                // Con la durata del soggiorno l'API risponde con i viaggi di andata e ritorno.
                parameter("min_trip_duration", stayNights.first)
                parameter("max_trip_duration", stayNights.last)
                parameter("currency", currencyCode.lowercase(Locale.ROOT))
            }.bodyAsText()
        },
        parse = { body ->
            NetworkJson.decodeFromString(TravelpayoutsGroupedResponse.serializer(), body).also { response ->
                if (!response.success) throw UnusableResponseException("Travelpayouts: ${response.error}")
            }
        },
    )

    /** Una tariffa per meta (la più economica), dalla più conveniente. */
    private suspend fun fetchDestinations(
        origin: String,
        departureAt: String,
        returnAt: String?,
        forceRefresh: Boolean,
    ): Fetched<TravelpayoutsPricesResponse> = cache.getOrFetch(
        key = CacheKey.of("tp-anywhere", origin, departureAt, returnAt, currencyCode),
        ttl = CachePolicy.FLIGHT_PRICES,
        forceRefresh = forceRefresh,
        fetch = {
            client.get("${baseUrl}aviasales/v3/prices_for_dates") {
                header(TravelpayoutsFlightDataSource.TOKEN_HEADER, token)
                parameter("origin", origin)
                parameter("departure_at", departureAt)
                returnAt?.let { parameter("return_at", it) }
                parameter("one_way", false)
                parameter("unique", true)
                parameter("direct", false)
                parameter("sorting", "price")
                parameter("currency", currencyCode.lowercase(Locale.ROOT))
                parameter("limit", DESTINATIONS_PAGE_SIZE)
                parameter("page", 1)
            }.bodyAsText()
        },
        parse = { body ->
            NetworkJson.decodeFromString(TravelpayoutsPricesResponse.serializer(), body).also { response ->
                if (!response.success) throw UnusableResponseException("Travelpayouts: ${response.error}")
            }
        },
    )

    /** Tariffe per chiave (mese o giorno); quelle non interpretabili si scartano una a una. */
    private fun <K : Any> TravelpayoutsGroupedResponse.fares(key: (String) -> K): Map<K, FareSnapshot> = data.mapNotNull { (raw, element) ->
        runCatching { key(raw) to NetworkJson.decodeFromJsonElement(TravelpayoutsFareDto.serializer(), element).toSnapshot() }.getOrNull()
    }.toMap()

    private fun TravelpayoutsFareDto.toSnapshot(): FareSnapshot {
        val fare = requireNotNull(price.content.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }) { "Prezzo non valido" }
        return FareSnapshot(
            price = Money.of(fare, currencyCode),
            departureDate = OffsetDateTime.parse(departureAt).toLocalDate(),
            returnDate = returnAt?.let { OffsetDateTime.parse(it).toLocalDate() },
            stops = maxOf(transfers, returnTransfers),
            carrierIata = airline?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() },
        )
    }

    /** Meta di una tariffa, con il nome italiano della città; `null` (scartata) se la città non è tra quelle incluse. */
    private suspend fun TravelpayoutsFareDto.toCheapDestination(origin: String): CheapDestination? {
        val code = (destination ?: codes.airport(destinationAirport)?.cityCode ?: destinationAirport).uppercase(Locale.ROOT)
        if (code == origin) return null
        val city = codes.city(code) ?: return null
        return CheapDestination(
            city = CityPlace(
                id = "tp:$code",
                name = city.name,
                countryCode = city.countryCode,
                location = city.location,
                timeZone = city.timeZone,
                country = Locale("", city.countryCode).getDisplayCountry(Locale.ITALIAN).takeIf { it.isNotBlank() && it != city.countryCode },
            ),
            cityCode = code,
            airportIata = destinationAirport.uppercase(Locale.ROOT),
            fare = toSnapshot(),
            bookingUrl = link?.takeIf { it.startsWith("/") }?.let { TravelpayoutsFlightDataSource.AVIASALES_BASE_URL + it },
        )
    }

    private companion object {
        const val GROUP_BY_MONTH = "month"
        const val GROUP_BY_DAY = "departure_at"

        /** Una tariffa per meta: cento mete bastano anche dopo aver scartato i soggiorni troppo brevi o lunghi. */
        const val DESTINATIONS_PAGE_SIZE = 100
        const val MAX_MONTHS = 2
    }
}

/** Risposta di `grouped_prices`: per ogni mese ("2026-12") o giorno ("2026-12-07") la tariffa più bassa. */
@Serializable
internal data class TravelpayoutsGroupedResponse(
    val success: Boolean = false,
    val data: Map<String, JsonElement> = emptyMap(),
    val currency: String? = null,
    val error: String? = null,
)
