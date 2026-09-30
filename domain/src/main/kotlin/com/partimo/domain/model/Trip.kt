package com.partimo.domain.model

import com.partimo.domain.model.place.DeparturePoint
import java.time.LocalDate
import java.time.ZoneId

/** Destinazione con i riferimenti usati dai vari moduli (voli, alloggi, POI, trasporti). */
data class Destination(
    val name: String,
    val countryCode: String,
    val airportIata: String,
    /** Centro città: punto di riferimento per POI, ristoranti e alloggi. */
    val center: GeoPoint,
    /** Principale nodo di arrivo (stazione o aeroporto) per i percorsi del trasporto pubblico. */
    val arrivalHub: GeoPoint,
    val arrivalHubName: String,
    /** Fuso orario locale, per mostrare gli orari dei mezzi nell'ora della destinazione. */
    val timeZone: ZoneId,
)

/** Viaggio pianificato: contesto comune a tutte le sezioni della dashboard. */
data class TripContext(
    val destination: Destination,
    val departureDate: LocalDate,
    val returnDate: LocalDate,
    /** Punto di partenza scelto dall'utente; `null` finché non lo indica (i voli non si possono cercare). */
    val departure: DeparturePoint? = null,
    val travellers: Int = 1,
) {
    init {
        require(!returnDate.isBefore(departureDate)) { "La data di ritorno precede la partenza" }
        require(travellers >= 1) { "Serve almeno un viaggiatore" }
    }

    val originIata: String? get() = departure?.airport?.iata
}
