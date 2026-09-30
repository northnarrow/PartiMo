package com.partimo.app.ui.dashboard

import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.place.Airport
import com.partimo.domain.model.place.AirportSize
import com.partimo.domain.model.place.DeparturePoint
import java.time.ZoneId

/** Destinazione e partenza di esempio usate da anteprime e test. */
object SampleDestinations {

    val VIENNA = Destination(
        name = "Vienna",
        countryCode = "AT",
        airportIata = "VIE",
        center = GeoPoint(48.2085, 16.3731),
        arrivalHub = GeoPoint(48.1103, 16.5697),
        arrivalHubName = "Vienna International Airport",
        timeZone = ZoneId.of("Europe/Vienna"),
    )

    val MILAN_DEPARTURE = DeparturePoint(
        cityName = "Milano",
        airport = Airport(
            iata = "MXP",
            name = "Milan Malpensa International Airport",
            city = "Ferno (VA)",
            countryCode = "IT",
            location = GeoPoint(45.6306, 8.7281),
            size = AirportSize.HUB,
        ),
    )
}
