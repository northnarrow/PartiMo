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
import com.partimo.data.source.FlightOffersDataSource
import com.partimo.domain.model.Money
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.flight.FlightSlice
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Prezzi dei voli dalla Data API di Aviasales (Travelpayouts), gratuita con il token del partner: le
 * tariffe più economiche trovate negli ultimi giorni da chi ha cercato la stessa tratta, per tutti i giorni
 * del periodo del viaggio. Sono prezzi reali ma da verificare: ogni volo porta alla sua pagina su Aviasales.
 *
 * Si cerca per città (Roma comprende Fiumicino e Ciampino, Londra anche Stansted e Luton): con i soli
 * aeroporti principali si perderebbe quasi tutta l'offerta low cost. Il token viaggia nell'intestazione
 * `X-Access-Token`, mai nell'URL.
 */
internal class TravelpayoutsFlightDataSource(
    private val client: HttpClient,
    private val cache: ResponseCache,
    private val token: String,
    private val codes: BundledFlightCodes,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : FlightOffersDataSource {

    override suspend fun searchOffers(query: FlightSearchQuery, forceRefresh: Boolean): Fetched<List<FlightOffer>> {
        val origin = cityCode(query.originIata)
        val destination = cityCode(query.destinationIata)
        val oneWay = query.returnDate == null
        val currency = query.currencyCode.lowercase(Locale.ROOT)
        val flexible = query.flexibleDates
        // Le date esatte si chiedono a parte quando contano (fra le tariffe di un mese potrebbero mancare),
        // i mesi quando vanno bene anche altri giorni.
        val ranges = buildList {
            if (flexible == null || flexible.exactDatesFirst) add(query.departureDate.toString() to query.returnDate?.toString())
            flexible?.let { addAll(departureMonths(it.departures).map { month -> month.toString() to null }) }
        }
        val responses = ranges.map { (departureAt, returnAt) -> fetch(origin, destination, departureAt, returnAt, oneWay, currency, forceRefresh) }
        val offers = responses
            .flatMap { it.data.fares }
            .sortedBy { it.price.content.toBigDecimalOrNull() }
            .mapNotNullSafely { fare -> fare.toOffer(query) }
            .filter { offer -> query.matchesDates(offer.outbound.departureTime.toLocalDate(), offer.inbound?.departureTime?.toLocalDate()) }
            .filter { it.maxStops <= query.maxConnections }
            // Con le date scelte quei giorni restano anche se i giorni vicini hanno tante tariffe più basse.
            .sortedByDescending { flexible?.exactDatesFirst == true && query.isOnTripDates(it) }
            .distinctBy { it.id }
            .take(MAX_OFFERS)
        return Fetched(offers, combinedOrigin(responses.map { it.origin }))
    }

    private suspend fun cityCode(iata: String): String = codes.airport(iata)?.cityCode ?: iata.uppercase(Locale.ROOT)

    /** Mesi delle partenze accettate: uno, o due quando la finestra scavalca la fine del mese. */
    private fun departureMonths(window: ClosedRange<LocalDate>): List<YearMonth> {
        val last = YearMonth.from(window.endInclusive)
        return generateSequence(YearMonth.from(window.start)) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(last) }
            .take(MAX_MONTHS)
            .toList()
    }

    /** Tariffe con partenza in un giorno ("2026-12-10") o in un mese ("2026-12"), con il ritorno se indicato. */
    private suspend fun fetch(
        origin: String,
        destination: String,
        departureAt: String,
        returnAt: String?,
        oneWay: Boolean,
        currency: String,
        forceRefresh: Boolean,
    ): Fetched<TravelpayoutsPricesResponse> = cache.getOrFetch(
        key = CacheKey.of("tp-prices", origin, destination, departureAt, returnAt, oneWay, currency),
        ttl = CachePolicy.FLIGHT_PRICES,
        forceRefresh = forceRefresh,
        fetch = {
            client.get("${baseUrl}aviasales/v3/prices_for_dates") {
                header(TOKEN_HEADER, token)
                parameter("origin", origin)
                parameter("destination", destination)
                parameter("departure_at", departureAt)
                returnAt?.let { parameter("return_at", it) }
                parameter("one_way", oneWay)
                parameter("unique", false)
                parameter("direct", false)
                parameter("sorting", "price")
                parameter("currency", currency)
                parameter("limit", PAGE_SIZE)
                parameter("page", 1)
            }.bodyAsText()
        },
        parse = ::parse,
    )

    private fun parse(body: String): TravelpayoutsPricesResponse {
        val response = NetworkJson.decodeFromString(TravelpayoutsPricesResponse.serializer(), body)
        if (!response.success) throw UnusableResponseException("Travelpayouts: ${response.error}")
        return response
    }

    private suspend fun TravelpayoutsFareDto.toOffer(query: FlightSearchQuery): FlightOffer {
        val carrier = requireNotNull(airline?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }) { "Compagnia mancante" }
        val departure = OffsetDateTime.parse(departureAt)
        val returning = returnAt?.let(OffsetDateTime::parse)
        val originZone = codes.airport(originAirport)?.timeZone
        val destinationZone = codes.airport(destinationAirport)?.timeZone
        val number = flightNumber?.content?.trim()?.takeIf { it.isNotEmpty() }
        val outbound = slice(
            from = originAirport,
            to = destinationAirport,
            departure = departure,
            minutes = requireNotNull(durationTo ?: duration?.takeIf { returning == null }) { "Durata mancante" },
            stops = transfers,
            // Senza il fuso dell'aeroporto d'arrivo si usa l'ora del ritorno, che parte da lì.
            arrivalZone = destinationZone ?: returning?.offset ?: departure.offset,
            flightNumbers = listOfNotNull(number?.let { carrier + it }),
        )
        val inbound = returning?.let { back ->
            slice(
                from = destinationAirport,
                to = originAirport,
                departure = back,
                minutes = requireNotNull(durationBack) { "Durata del ritorno mancante" },
                stops = returnTransfers,
                arrivalZone = originZone ?: departure.offset,
                flightNumbers = emptyList(),
            )
        }
        val fare = requireNotNull(price.content.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }) { "Prezzo non valido" }
        return FlightOffer(
            id = listOf("tp", originAirport, destinationAirport, departureAt, returnAt.orEmpty(), carrier + number.orEmpty()).joinToString("|"),
            carrierName = codes.airlineName(carrier) ?: carrier,
            carrierIata = carrier,
            carrierLogoUrl = "$LOGO_BASE_URL$carrier.png",
            // La Data API indica il prezzo di un posto: lo pagano tutti tranne i neonati in braccio
            // (stima: il loro supplemento e lo sconto dei bambini si vedono su Aviasales).
            totalPrice = Money.of(fare.multiply(query.travellers.seatedPassengers.toBigDecimal()), query.currencyCode),
            slices = listOfNotNull(outbound, inbound),
            bookingUrl = bookingUrl(departure, returning, query.travellers),
            priceFoundOn = priceFoundOn(),
            passengers = query.travellers.seatedPassengers,
        )
    }

    private fun slice(
        from: String,
        to: String,
        departure: OffsetDateTime,
        minutes: Int,
        stops: Int,
        arrivalZone: ZoneId,
        flightNumbers: List<String>,
    ): FlightSlice {
        require(minutes > 0) { "Durata non valida" }
        val duration = Duration.ofMinutes(minutes.toLong())
        return FlightSlice(
            originIata = from,
            destinationIata = to,
            departureTime = departure.toLocalDateTime(),
            arrivalTime = LocalDateTime.ofInstant(departure.toInstant().plus(duration), arrivalZone),
            duration = duration,
            stops = stops,
            flightNumbers = flightNumbers,
        )
    }

    /**
     * Pagina del volo su Aviasales (con il prezzo trovato) per tutti i viaggiatori; senza, la ricerca della
     * tratta in quei giorni.
     */
    private fun TravelpayoutsFareDto.bookingUrl(departure: OffsetDateTime, returning: OffsetDateTime?, travellers: Travellers): String =
        link?.takeIf { it.startsWith("/") }?.let { AVIASALES_BASE_URL + AviasalesPassengers.applyTo(it, travellers) }
            ?: buildString {
                append(AVIASALES_BASE_URL).append("/search/")
                append(originAirport).append(departure.format(DAY_MONTH)).append(destinationAirport)
                returning?.let { append(it.format(DAY_MONTH)) }
                append(AviasalesPassengers.code(travellers))
            }

    /** Il collegamento riporta il giorno della ricerca in cui è stato trovato il prezzo (`search_date=ggmmaaaa`). */
    private fun TravelpayoutsFareDto.priceFoundOn(): LocalDate? =
        link?.let { SEARCH_DATE.find(it) }?.let { match -> runCatching { LocalDate.parse(match.groupValues[1], SEARCH_DATE_FORMAT) }.getOrNull() }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.travelpayouts.com/"
        const val TOKEN_HEADER = "X-Access-Token"
        const val AVIASALES_BASE_URL = "https://www.aviasales.com"

        /** Loghi delle compagnie aeree di Aviasales. */
        private const val LOGO_BASE_URL = "https://pics.avs.io/96/96/"
        private const val PAGE_SIZE = 100
        private const val MAX_OFFERS = 40
        private const val MAX_MONTHS = 2
        private val DAY_MONTH = DateTimeFormatter.ofPattern("ddMM")
        private val SEARCH_DATE = Regex("[?&]search_date=(\\d{8})")
        private val SEARCH_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy")
    }
}

@Serializable
internal data class TravelpayoutsPricesResponse(
    val success: Boolean = false,
    @SerialName("data") val fares: List<TravelpayoutsFareDto> = emptyList(),
    val currency: String? = null,
    val error: String? = null,
)

/** Tariffa trovata dalle ricerche dei viaggiatori; orari locali degli aeroporti, con il loro scostamento da UTC. */
@Serializable
internal data class TravelpayoutsFareDto(
    @SerialName("origin_airport") val originAirport: String,
    @SerialName("destination_airport") val destinationAirport: String,
    @SerialName("departure_at") val departureAt: String,
    @SerialName("return_at") val returnAt: String? = null,
    /** Prezzo per un adulto, letto come testo per non perdere precisione. */
    val price: JsonPrimitive,
    val airline: String? = null,
    /** A volte testo, a volte numero. */
    @SerialName("flight_number") val flightNumber: JsonPrimitive? = null,
    val transfers: Int = 0,
    @SerialName("return_transfers") val returnTransfers: Int = 0,
    @SerialName("duration_to") val durationTo: Int? = null,
    @SerialName("duration_back") val durationBack: Int? = null,
    val duration: Int? = null,
    val link: String? = null,
)

/**
 * Passeggeri nelle ricerche di Aviasales: le ultime cifre del percorso dopo le date sono adulti, bambini e
 * neonati (es. `/search/ROM1112VIE1312211` = 2 adulti, 1 bambino, 1 neonato).
 */
internal object AviasalesPassengers {

    /** Percorso di ricerca: città, giorno e mese di andata e (facoltativi) di ritorno, poi i passeggeri. */
    private val SEARCH_PATH = Regex("""^(/search/[A-Z]{3}\d{4}[A-Z]{3}(?:\d{4})?)(\d{1,3})(?=[?#]|$)""")

    /** Su Aviasales la tariffa ridotta per bambini vale da 2 a 11 anni; dai 12 si paga come un adulto. */
    private val CHILD_FARE_AGES = Travellers.INFANT_AGE_LIMIT..11

    /** "1" per una persona, "21" per due adulti e un bambino, "211" con un neonato. */
    fun code(travellers: Travellers): String {
        val children = travellers.childAges.count { it in CHILD_FARE_AGES }
        val infants = travellers.infants
        val adults = travellers.total - children - infants
        return buildString {
            append(adults)
            if (children > 0 || infants > 0) append(children)
            if (infants > 0) append(infants)
        }
    }

    /** [path] di una ricerca con i passeggeri di [travellers]; un percorso diverso resta com'è. */
    fun applyTo(path: String, travellers: Travellers): String {
        val match = SEARCH_PATH.find(path) ?: return path
        return match.groupValues[1] + code(travellers) + path.substring(match.range.last + 1)
    }
}
