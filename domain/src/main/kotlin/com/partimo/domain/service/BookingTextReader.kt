package com.partimo.domain.service

import com.partimo.domain.model.booking.BookingDraft
import com.partimo.domain.model.booking.BookingKind
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime

/**
 * Legge il testo di una conferma di prenotazione (email, PDF o screenshot riconosciuti sul telefono) e ne ricava
 * le prenotazioni: un volo per ogni numero di volo (andata e ritorno), oppure un alloggio, un treno, un pullman.
 * Conosce date e orari scritti in italiano e in inglese. È un aiuto, non una certezza: l'utente controlla i campi
 * prima di salvare.
 */
class BookingTextReader(
    /** Codici IATA degli aeroporti conosciuti (per distinguere "VIE" da una parola qualunque). */
    private val airports: Set<String>,
    /** Compagnie aeree: codice IATA → nome. */
    private val airlines: Map<String, String>,
) {

    /** Prenotazioni trovate in [text], nell'ordine in cui compaiono; vuoto se non c'è nemmeno una data. */
    fun read(text: String, today: LocalDate): List<BookingDraft> {
        val clean = normalize(text)
        val dates = findDates(clean, today)
        // Senza nemmeno una data non è una prenotazione (es. una mail di benvenuto).
        if (dates.isEmpty()) return emptyList()
        val times = findTimes(clean, dates.map { it.range })
        val reference = findReference(clean)
        val flights = findFlights(clean)
        val lower = clean.lowercase()
        return when {
            flights.isNotEmpty() -> flights.mapIndexed { index, flight ->
                flightDraft(clean, flight, flights.getOrNull(index - 1), flights.getOrNull(index + 1)?.range?.first ?: clean.length, dates, times, reference)
            }
            LODGING_WORDS.count { it in lower } >= 2 -> listOfNotNull(lodgingDraft(clean, lower, dates, times, reference))
            TRAIN_WORDS.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(lower) } ->
                listOfNotNull(groundDraft(BookingKind.TRAIN, clean, dates, times, reference))
            BUS_WORDS.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(lower) } ->
                listOfNotNull(groundDraft(BookingKind.BUS, clean, dates, times, reference))
            else -> listOfNotNull(genericDraft(clean, dates, times, reference))
        }
    }

    // ---- Voli -------------------------------------------------------------------------------------------------

    private data class FlightNumber(val code: String, val number: String, val range: IntRange)

    private fun findFlights(text: String): List<FlightNumber> {
        val withKeyword = FLIGHT_WITH_KEYWORD.findAll(text).map { FlightNumber(it.groupValues[1].uppercase(), it.groupValues[2], it.groups[1]!!.range.first..it.range.last) }
        // Un codice di compagnia seguito da un numero è un volo solo in un testo che parla di voli: con un aeroporto
        // o una parola da aeroporto. Così "FR 9521" su un biglietto del treno o "AT 1010" in un indirizzo non lo sono.
        val aboutFlights = hasAirports(text) || FLIGHT_WORDS.containsMatchIn(text)
        val known = FLIGHT_NUMBER.findAll(text)
            .filter { aboutFlights && it.groupValues[1] in airlines }
            .map { FlightNumber(it.groupValues[1], it.groupValues[2], it.range) }
        // Lo stesso volo può comparire più volte (es. nell'oggetto e nel corpo della mail): conta la prima.
        return (withKeyword + known)
            .sortedBy { it.range.first }
            .distinctBy { it.code + it.number.trimStart('0') }
            .toList()
    }

    private fun hasAirports(text: String): Boolean =
        AIRPORT_IN_PARENTHESES.findAll(text).any { it.groupValues[1] in airports } ||
            AIRPORT_PAIR.findAll(text).any { it.groupValues[1] in airports && it.groupValues[2] in airports }

    @Suppress("LongParameterList")
    private fun flightDraft(
        text: String,
        flight: FlightNumber,
        previous: FlightNumber?,
        segmentEnd: Int,
        dates: List<Found<LocalDate>>,
        times: List<Found<LocalTime>>,
        reference: String?,
    ): BookingDraft {
        val segment = flight.range.first until segmentEnd
        val segmentText = text.substring(segment.first, segmentEnd)
        val date = flightDate(flight, previous, segment, dates, times)
        val segmentTimes = times.filter { it.range.first in segment }.map { it.value }
        val departure = segmentTimes.getOrNull(0)
        val arrival = segmentTimes.getOrNull(1)
        val (origin, destination) = findRoute(segmentText)
        val airline = airlines[flight.code]
        val startDate = date?.value
        // Arrivo prima della partenza: si arriva il giorno dopo (voli notturni).
        val endDate = if (startDate != null && departure != null && arrival != null && arrival.isBefore(departure)) startDate.plusDays(1) else startDate
        return BookingDraft(
            kind = BookingKind.FLIGHT,
            title = listOfNotNull(airline ?: "Volo", flight.code + " " + flight.number.trimStart('0').ifEmpty { "0" }).joinToString(" "),
            startDate = startDate,
            startTime = departure,
            endDate = endDate?.takeIf { arrival != null },
            endTime = arrival,
            origin = origin.orEmpty(),
            destination = destination.orEmpty(),
            reference = reference.orEmpty(),
            provider = airline.orEmpty(),
        )
    }

    /**
     * Giorno del volo: la data nel suo tratto o quella che lo introduce, appena prima del numero di volo e senza
     * orari in mezzo (che sarebbero del volo precedente); vale la più vicina. Altrimenti l'ultima data prima del
     * volo, come per il secondo volo di una coincidenza nello stesso giorno.
     */
    private fun flightDate(
        flight: FlightNumber,
        previous: FlightNumber?,
        segment: IntRange,
        dates: List<Found<LocalDate>>,
        times: List<Found<LocalTime>>,
    ): Found<LocalDate>? {
        val after = dates.firstOrNull { it.range.first in segment }
        val before = dates.lastOrNull { date ->
            date.range.last < flight.range.first &&
                (previous == null || date.range.first > previous.range.last) &&
                flight.range.first - date.range.last < DATE_BEFORE_FLIGHT_CHARS &&
                times.none { it.range.first in date.range.last until flight.range.first }
        }
        val chosen = when {
            after == null -> before
            before == null -> after
            flight.range.first - before.range.last <= after.range.first - flight.range.last -> before
            else -> after
        }
        return chosen ?: dates.lastOrNull { it.range.last < flight.range.first }
    }

    /** Aeroporti di partenza e arrivo: i codici tra parentesi, oppure "MXP → VIE". */
    private fun findRoute(segment: String): Pair<String?, String?> {
        val inParentheses = AIRPORT_IN_PARENTHESES.findAll(segment).map { it.groupValues[1] }.filter { it in airports }.distinct().toList()
        if (inParentheses.size >= 2) return inParentheses[0] to inParentheses[1]
        AIRPORT_PAIR.findAll(segment).firstOrNull { it.groupValues[1] in airports && it.groupValues[2] in airports }?.let {
            return it.groupValues[1] to it.groupValues[2]
        }
        return inParentheses.firstOrNull() to null
    }

    // ---- Alloggi ----------------------------------------------------------------------------------------------

    private fun lodgingDraft(text: String, lower: String, dates: List<Found<LocalDate>>, times: List<Found<LocalTime>>, reference: String?): BookingDraft? {
        val checkInAt = CHECK_IN_WORDS.mapNotNull { word -> lower.indexOf(word).takeIf { it >= 0 } }.minOrNull()
        val checkOutAt = CHECK_OUT_WORDS.mapNotNull { word -> lower.indexOf(word).takeIf { it >= 0 } }.minOrNull()
        val checkIn = checkInAt?.let { at -> dates.firstOrNull { it.range.first >= at } } ?: dates.firstOrNull()
        val checkOut = checkOutAt?.let { at -> dates.firstOrNull { it.range.first >= at } }
            ?: dates.firstOrNull { it.value.isAfter(checkIn?.value ?: LocalDate.MIN) }
        if (checkIn == null) return null
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val name = lines.firstOrNull { line -> LODGING_NAME.containsMatchIn(line) && line.length <= MAX_TITLE && !PROVIDERS.any { it in line.lowercase() } }
        return BookingDraft(
            kind = BookingKind.LODGING,
            title = name ?: "Alloggio",
            startDate = checkIn.value,
            startTime = checkInAt?.let { at -> lineTime(text, at, times) },
            endDate = checkOut?.value,
            endTime = checkOutAt?.let { at -> lineTime(text, at, times) },
            address = lines.firstOrNull { STREET.containsMatchIn(it) && it.any(Char::isDigit) && it.length <= MAX_ADDRESS }.orEmpty(),
            reference = reference.orEmpty(),
            provider = PROVIDER_NAMES.entries.firstOrNull { it.key in lower }?.value.orEmpty(),
        )
    }

    /** Ora scritta nella stessa riga della parola alla posizione [at] (es. "Check-in: ... dalle 15:00"). */
    private fun lineTime(text: String, at: Int, times: List<Found<LocalTime>>): LocalTime? {
        val lineEnd = text.indexOf('\n', at).let { if (it < 0) text.length else it }
        return times.firstOrNull { it.range.first in at until lineEnd }?.value
    }

    // ---- Treni e pullman --------------------------------------------------------------------------------------

    private fun groundDraft(kind: BookingKind, text: String, dates: List<Found<LocalDate>>, times: List<Found<LocalTime>>, reference: String?): BookingDraft? {
        val date = dates.firstOrNull() ?: return null
        val (origin, destination) = findPlaces(text)
        val after = times.filter { it.range.first >= date.range.first }.ifEmpty { times }
        val departure = after.getOrNull(0)?.value
        val arrival = after.getOrNull(1)?.value
        val lower = text.lowercase()
        val train = TRAIN_NAME.find(text)?.let { "${it.groupValues[1]} ${it.groupValues[2]}" }
        val provider = PROVIDER_NAMES.entries.firstOrNull { it.key in lower }?.value
        val route = if (origin != null && destination != null) "$origin → $destination" else null
        val title = train ?: listOfNotNull(provider ?: if (kind == BookingKind.TRAIN) "Treno" else "Pullman", route).joinToString(" ")
        val endDate = dates.getOrNull(1)?.value?.takeIf { it.isAfter(date.value) }
            ?: if (departure != null && arrival != null && arrival.isBefore(departure)) date.value.plusDays(1) else date.value
        return BookingDraft(
            kind = kind,
            title = title,
            startDate = date.value,
            startTime = departure,
            endDate = endDate.takeIf { arrival != null },
            endTime = arrival,
            origin = origin.orEmpty(),
            destination = destination.orEmpty(),
            reference = reference.orEmpty(),
            provider = provider.orEmpty(),
        )
    }

    /** Tratta di un treno o di un pullman: "Milano Centrale → Roma Termini", "da Milano a Roma", "From X to Y". */
    private fun findPlaces(text: String): Pair<String?, String?> {
        text.lines().forEach { line ->
            ARROW_ROUTE.find(line.trim())?.let { match ->
                val from = cleanPlace(match.groupValues[1])
                val to = cleanPlace(match.groupValues[2])
                if (from != null && to != null) return from to to
            }
        }
        FROM_TO.find(text)?.let { match ->
            val from = cleanPlace(match.groupValues[1])
            val to = cleanPlace(match.groupValues[2])
            if (from != null && to != null) return from to to
        }
        return null to null
    }

    private fun cleanPlace(raw: String): String? = raw.trim().trim('-', ':', ',').trim()
        .takeIf { it.length in 2..MAX_PLACE && it.none(Char::isDigit) && it.any(Char::isLetter) }

    // ---- Altro ------------------------------------------------------------------------------------------------

    private fun genericDraft(text: String, dates: List<Found<LocalDate>>, times: List<Found<LocalTime>>, reference: String?): BookingDraft? {
        val date = dates.firstOrNull() ?: return null
        val title = text.lines().map { it.trim() }.firstOrNull { line -> line.length in 3..MAX_TITLE && line.any(Char::isLetter) }.orEmpty()
        return BookingDraft(
            kind = BookingKind.OTHER,
            title = title,
            startDate = date.value,
            startTime = times.firstOrNull { it.range.first >= date.range.first }?.value,
            reference = reference.orEmpty(),
        )
    }

    // ---- Codice, date e orari ---------------------------------------------------------------------------------

    private fun findReference(text: String): String? = REFERENCE.findAll(text)
        .map { it.groupValues[1].trimEnd('.', '-') }
        .firstOrNull { code -> code.length >= MIN_REFERENCE && (code.any(Char::isDigit) || code.all { it.isUpperCase() }) }

    private data class Found<T>(val value: T, val range: IntRange)

    private fun findDates(text: String, today: LocalDate): List<Found<LocalDate>> {
        val found = mutableListOf<Found<LocalDate>>()
        fun add(range: IntRange, day: Int, month: Int, year: Int?) {
            if (found.any { it.range.first <= range.last && range.first <= it.range.last }) return
            val date = try {
                if (year != null) LocalDate.of(year, month, day) else nextOccurrence(today, month, day)
            } catch (e: DateTimeException) {
                return
            }
            found += Found(date, range)
        }
        ISO_DATE.findAll(text).forEach { add(it.range, it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        NUMERIC_DATE.findAll(text).forEach { match ->
            val year = match.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            add(match.range, match.groupValues[1].toInt(), match.groupValues[2].toInt(), year)
        }
        DAY_MONTH_NAME.findAll(text).forEach { match ->
            val month = MONTHS[match.groupValues[2].lowercase().trimEnd('.')] ?: return@forEach
            add(match.range, match.groupValues[1].toInt(), month, match.groupValues[3].toIntOrNull())
        }
        MONTH_NAME_DAY.findAll(text).forEach { match ->
            val month = MONTHS[match.groupValues[1].lowercase().trimEnd('.')] ?: return@forEach
            add(match.range, match.groupValues[2].toInt(), month, match.groupValues[3].toIntOrNull())
        }
        return found.sortedBy { it.range.first }
    }

    /** Senza l'anno: la prima volta che quel giorno arriva (una conferma parla del futuro). */
    private fun nextOccurrence(today: LocalDate, month: Int, day: Int): LocalDate {
        val thisYear = LocalDate.of(today.year, month, day)
        return if (thisYear.isBefore(today.minusDays(RECENT_DAYS))) thisYear.plusYears(1) else thisYear
    }

    /** Orari "21:10" o "21.10", esclusi i pezzi delle date ("11.12.2026"). */
    private fun findTimes(text: String, dateRanges: List<IntRange>): List<Found<LocalTime>> = TIME.findAll(text)
        .filter { match -> dateRanges.none { it.first <= match.range.last && match.range.first <= it.last } }
        .map { Found(LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()), it.range) }
        .toList()

    private fun normalize(text: String): String = text
        .replace('\u00A0', ' ')
        .replace('\u202F', ' ')
        .replace(Regex("[–—]"), "-")
        .replace(Regex("->|➔|➝|⟶|»"), "→")
        .lines()
        .joinToString("\n") { it.replace(Regex("[ \\t]+"), " ").trim() }

    private companion object {
        const val DATE_BEFORE_FLIGHT_CHARS = 200
        const val RECENT_DAYS = 7L
        const val MAX_TITLE = 60
        const val MAX_ADDRESS = 90
        const val MAX_PLACE = 40
        const val MIN_REFERENCE = 5

        val MONTHS: Map<String, Int> = buildMap {
            listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre")
                .forEachIndexed { index, name -> put(name, index + 1) }
            listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic").forEachIndexed { index, name -> put(name, index + 1) }
            listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
                .forEachIndexed { index, name -> put(name, index + 1) }
            listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").forEachIndexed { index, name -> put(name, index + 1) }
            put("sept", 9)
        }

        /** Nomi dei mesi dal più lungo, così "dicembre" non si ferma a "dic". */
        val MONTH_NAMES = MONTHS.keys.sortedByDescending { it.length }.joinToString("|")

        val ISO_DATE = Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)""")
        val NUMERIC_DATE = Regex("""(?<![\d.])(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4}|\d{2})(?![\d.])""")
        val DAY_MONTH_NAME = Regex("""(?<!\d)(\d{1,2})(?:°|º)?\s+($MONTH_NAMES)\.?(?![a-zà-ù])(?:\s+(\d{4}))?""", RegexOption.IGNORE_CASE)
        val MONTH_NAME_DAY = Regex("""(?<![a-zà-ù])($MONTH_NAMES)\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})""", RegexOption.IGNORE_CASE)
        val TIME = Regex("""(?<![\d:.])([01]?\d|2[0-3])[:.]([0-5]\d)(?![\d])""")

        /** "FR 7178", "U2 8131", "W61234": designatore IATA (due caratteri) e numero. */
        val FLIGHT_NUMBER = Regex("""(?<![A-Za-z0-9])([A-Z][A-Z0-9]|[0-9][A-Z])\s?(\d{1,4})(?![\d])""")

        /** "Volo EZY8131", "Flight no. LH 1844": con la parola volo anche i codici ICAO di tre lettere. */
        val FLIGHT_WITH_KEYWORD = Regex("""(?i:\b(?:volo|flight|vuelo|vol|flug))\s*(?i:n\.|nr\.|no\.|number|numero)?\s*([A-Z0-9]{2,3})\s?(\d{1,4})(?![\d])""")

        val AIRPORT_IN_PARENTHESES = Regex("""\(([A-Z]{3})\)""")
        val AIRPORT_PAIR = Regex("""(?<![A-Z])([A-Z]{3})\s*(?:→|-|>|/|to|a)\s*([A-Z]{3})(?![A-Z])""")

        /** Codice dopo le parole che lo annunciano: "Codice di prenotazione: K7M2QX", "PNR QWERTY", "Numero di conferma: 4123.567.890". */
        val REFERENCE = Regex(
            """(?i:codice (?:di )?prenotazione|codice pnr|pnr|booking reference|booking code|reservation code|confirmation (?:number|code)|numero (?:di )?(?:conferma|prenotazione)|booking number|codice biglietto|ticket code|(?:conferma|prenotazione|biglietto|ordine|booking|order) (?:n\.?|nr\.?|no\.?|#))\s*(?:n\.|nr\.|no\.|#)?\s*:?\s*([A-Z0-9][A-Z0-9.\-]{3,14}[A-Z0-9])""",
        )

        /** Parole che dicono che il testo parla di voli. */
        val FLIGHT_WORDS = Regex("""(?i)\b(volo|voli|flight|flights|imbarco|boarding|gate|aeroporto|airport|terminal)\b""")

        val LODGING_WORDS = listOf("check-in", "check in", "check-out", "check out", "hotel", "b&b", "booking.com", "airbnb", "struttura", "notti", "nights", "camera", "room", "ostello", "hostel", "appartamento", "apartment")
        val CHECK_IN_WORDS = listOf("check-in", "check in", "arrivo", "arrival")
        val CHECK_OUT_WORDS = listOf("check-out", "check out", "partenza", "departure")
        val LODGING_NAME = Regex("""(?i)\b(hotel|b&b|bed and breakfast|residence|hostel|ostello|apartment|appartamento|guesthouse|guest house|pensione|albergo|resort|inn|suites?|villa|camping|campeggio|locanda|agriturismo)\b""")
        val STREET = Regex("""(?i)\b(via|viale|piazza|piazzale|corso|largo|vicolo|strada|lungomare|straße|strasse|gasse|platz|street|st\.|road|avenue|rue|calle|avenida|rua|ulica)\b""")

        val TRAIN_WORDS = listOf("trenitalia", "italo", "frecciarossa", "frecciargento", "frecciabianca", "intercity", "regionale", "treno", "train", "carrozza", "coach", "trenord", "öbb", "sncf", "renfe", "eurostar", "railjet", "tgv", "ice", "eurocity")
        val BUS_WORDS = listOf("flixbus", "itabus", "blablacar", "autobus", "pullman", "bus", "marinobus", "baltour")
        val TRAIN_NAME = Regex("""\b(Frecciarossa|Frecciargento|Frecciabianca|Italo|Intercity Notte|Intercity|InterCity|Regionale Veloce|Regionale|EuroCity|Eurostar|Railjet|ICE|TGV|FR|IC|EC|RJ|RV)\s?(\d{2,5})\b""")
        val ARROW_ROUTE = Regex("""^([^→\d][^→]{1,40}?)\s*→\s*([^→]{2,40})$""")
        val FROM_TO = Regex("""(?i)\b(?:da|from)\s+([A-ZÀ-Ü][\p{L} '.]{1,38}?)\s+(?:a|to)\s+([A-ZÀ-Ü][\p{L} '.]{1,38})(?=[\n,.]|$)""")

        val PROVIDERS = listOf("booking.com", "airbnb", "expedia", "hotels.com")
        val PROVIDER_NAMES = linkedMapOf(
            "booking.com" to "Booking.com",
            "airbnb" to "Airbnb",
            "expedia" to "Expedia",
            "hotels.com" to "Hotels.com",
            "trenitalia" to "Trenitalia",
            "italo" to "Italo",
            "trenord" to "Trenord",
            "flixbus" to "FlixBus",
            "itabus" to "Itabus",
            "blablacar" to "BlaBlaCar",
        )
    }
}
