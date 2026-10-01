package com.partimo.app.ui.common

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

    /** Skyscanner: compagnie di linea, low cost e agenzie a confronto per la stessa tratta e le stesse date. */
    fun skyscanner(originIata: String, destinationIata: String, departure: LocalDate, returnDate: LocalDate, adults: Int): String =
        "https://www.skyscanner.it/trasporti/voli/" +
            "${originIata.lowercase(Locale.ROOT)}/${destinationIata.lowercase(Locale.ROOT)}/" +
            "${departure.format(skyscannerDate)}/${returnDate.format(skyscannerDate)}/?adultsv2=$adults"

    /**
     * Booking.com con disponibilità e prezzi per le date: [place] è una città ("Vienna") oppure una
     * struttura precisa ("Hotel Sacher, Vienna").
     */
    fun booking(place: String, checkIn: LocalDate, checkOut: LocalDate, adults: Int): String =
        "https://www.booking.com/searchresults.it.html?ss=" + encode(place) +
            "&checkin=$checkIn&checkout=$checkOut&group_adults=$adults&no_rooms=1&group_children=0"

    /** Airbnb: case e appartamenti a [place] per le date del viaggio. */
    fun airbnb(place: String, checkIn: LocalDate, checkOut: LocalDate, adults: Int): String =
        "https://www.airbnb.it/s/" + encode(place) + "/homes?checkin=$checkIn&checkout=$checkOut&adults=$adults"

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
