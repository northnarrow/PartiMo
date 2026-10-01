package com.partimo.app.ui.common

import com.partimo.domain.model.Travellers
import com.partimo.domain.model.stay.AccommodationSearchQuery
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Collegamenti ai siti di viaggio con la ricerca già compilata (tratta, date, viaggiatori): mostrano
 * prezzi e disponibilità reali senza chiavi API. Dall'app esce solo ciò che serve alla ricerca.
 */
object TravelLinks {

    private val skyscannerDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd", Locale.ROOT)

    /** Google Voli: andata e ritorno tra i due aeroporti, con i prezzi in euro. */
    fun googleFlights(originIata: String, destinationIata: String, departure: LocalDate, returnDate: LocalDate): String =
        "https://www.google.com/travel/flights?q=" +
            encode("Flights from $originIata to $destinationIata on $departure through $returnDate") +
            "&hl=it&curr=EUR"

    /**
     * Skyscanner: compagnie di linea, low cost e agenzie a confronto per la stessa tratta e le stesse date,
     * con gli adulti e l'età di ogni bambino.
     */
    fun skyscanner(originIata: String, destinationIata: String, departure: LocalDate, returnDate: LocalDate, travellers: Travellers): String =
        "https://www.skyscanner.it/trasporti/voli/" +
            "${originIata.lowercase(Locale.ROOT)}/${destinationIata.lowercase(Locale.ROOT)}/" +
            "${departure.format(skyscannerDate)}/${returnDate.format(skyscannerDate)}/?adultsv2=${travellers.adults}" +
            (if (travellers.children > 0) "&childrenv2=" + travellers.childAges.joinToString("%7C") else "")

    /**
     * Booking.com con disponibilità e prezzi per le date: [place] è una città ("Vienna") oppure una
     * struttura precisa ("Hotel Sacher, Vienna"). Adulti, età dei bambini e una camera ogni due adulti.
     */
    fun booking(place: String, checkIn: LocalDate, checkOut: LocalDate, travellers: Travellers): String =
        "https://www.booking.com/searchresults.it.html?ss=" + encode(place) +
            "&checkin=$checkIn&checkout=$checkOut&group_adults=${travellers.adults}" +
            "&no_rooms=${AccommodationSearchQuery.roomsFor(travellers)}&group_children=${travellers.children}" +
            travellers.childAges.joinToString("") { "&age=$it" }

    /**
     * Airbnb: case e appartamenti a [place] per le date del viaggio. Per Airbnb i ragazzi dai 13 anni
     * contano come adulti, i bambini sotto i 2 anni come neonati.
     */
    fun airbnb(place: String, checkIn: LocalDate, checkOut: LocalDate, travellers: Travellers): String {
        val infants = travellers.infants
        val children = travellers.childAges.count { it in Travellers.INFANT_AGE_LIMIT..AIRBNB_MAX_CHILD_AGE }
        val adults = travellers.total - children - infants
        return "https://www.airbnb.it/s/" + encode(place) + "/homes?checkin=$checkIn&checkout=$checkOut&adults=$adults" +
            (if (children > 0) "&children=$children" else "") + (if (infants > 0) "&infants=$infants" else "")
    }

    private const val AIRBNB_MAX_CHILD_AGE = 12

    /**
     * Ricerca Google degli eventi in città nei giorni del viaggio: concerti, mostre e spettacoli con
     * date e biglietti, compresi quelli che le fonti aperte non conoscono.
     */
    fun googleEvents(city: String, from: LocalDate, to: LocalDate): String {
        val days = if (from.year == to.year && from.month == to.month) {
            "dal ${from.dayOfMonth} al ${to.dayOfMonth} ${Formatters.monthName(to.month)} ${to.year}"
        } else {
            "dal ${from.dayOfMonth} ${Formatters.monthName(from.month)} ${from.year} al ${to.dayOfMonth} ${Formatters.monthName(to.month)} ${to.year}"
        }
        return "https://www.google.com/search?q=" + encode("eventi a $city $days")
    }

    /**
     * Rome2rio: tutti i modi per andare da [from] a [to] (treno, pullman, aereo, auto, traghetto) con
     * durata, prezzi indicativi e siti dove prenotare. Le città vanno nel percorso, con i trattini al
     * posto degli spazi (es. "Reggio-Emilia").
     */
    fun rome2rio(from: String, to: String): String = "https://www.rome2rio.com/s/" + pathName(from) + "/" + pathName(to)

    /**
     * Tiqets: biglietti di musei e attrazioni. Con il nome di un luogo apre i suoi biglietti (es. il
     * Kunsthistorisches Museum), con una città le sue attrazioni.
     */
    fun tiqets(query: String): String = "https://www.tiqets.com/it/search?q=" + encode(query)

    private fun pathName(name: String): String = encode(name.trim().replace(Regex("\\s+"), "-"))

    /** Codifica con gli spazi come %20, valida sia nel percorso sia nei parametri. */
    private fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")
}
