package com.partimo.data.demo

import com.partimo.data.network.Fetched
import com.partimo.data.source.FlightOffersDataSource
import com.partimo.data.source.TransitDataSource
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.flight.FlightOffer
import com.partimo.domain.model.flight.FlightSearchQuery
import com.partimo.domain.model.transit.TransitRoute
import com.partimo.domain.model.transit.TransitRouteQuery
import kotlinx.coroutines.delay

// Sorgenti demo: stessa interfaccia delle sorgenti reali, dati dal DemoCatalog e origine DEMO.
// Una piccola latenza simulata rende visibili gli stati di caricamento della UI.

private const val DEFAULT_DEMO_LATENCY_MILLIS = 600L

internal class DemoFlightDataSource(
    private val catalog: DemoCatalog,
    private val latencyMillis: Long = DEFAULT_DEMO_LATENCY_MILLIS,
) : FlightOffersDataSource {
    override suspend fun searchOffers(query: FlightSearchQuery, forceRefresh: Boolean): Fetched<List<FlightOffer>> {
        delay(latencyMillis)
        return Fetched(catalog.flights(query), DataOrigin.DEMO)
    }
}

internal class DemoTransitDataSource(
    private val catalog: DemoCatalog,
    private val latencyMillis: Long = DEFAULT_DEMO_LATENCY_MILLIS,
) : TransitDataSource {
    override suspend fun routes(query: TransitRouteQuery, forceRefresh: Boolean): Fetched<List<TransitRoute>> {
        delay(latencyMillis)
        return Fetched(catalog.transitRoutes(query), DataOrigin.DEMO)
    }
}
