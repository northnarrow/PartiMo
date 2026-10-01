package com.partimo.domain.service

import com.partimo.domain.model.GeoPoint

/** Mezzo per raggiungere la meta. */
enum class TravelMode { FLIGHT, TRAIN, COACH, CAR }

/** Emissioni stimate con un mezzo per l'andata e il ritorno di una persona. */
data class ModeFootprint(val mode: TravelMode, val distanceKm: Double, val kgCo2e: Double)

/**
 * Confronto delle emissioni (CO₂ equivalente) per raggiungere la meta, andata e ritorno, a persona.
 *
 * Fattori ufficiali del governo britannico, "Greenhouse gas reporting: conversion factors 2025"
 * (DESNZ), in kg CO₂e per passeggero-km. L'aereo comprende gli effetti non-CO₂ in quota (radiative
 * forcing) e si calcola in linea d'aria, perché i fattori includono già le deviazioni dalla rotta
 * diretta; per treno, pullman e auto il percorso è stimato come linea d'aria +20%.
 * https://www.gov.uk/government/publications/greenhouse-gas-reporting-conversion-factors-2025
 */
object CarbonFootprint {

    /** Voli brevi: "Domestic, to/from UK", passeggero medio, con RF. */
    const val SHORT_FLIGHT_KG_PER_KM = 0.22928

    /** Voli internazionali: "International, to/from non-UK", passeggero medio, con RF. */
    const val FLIGHT_KG_PER_KM = 0.14253

    /** Treno: "National rail". */
    const val TRAIN_KG_PER_KM = 0.03546

    /** Pullman: "Coach". */
    const val COACH_KG_PER_KM = 0.02776

    /** Auto media con alimentazione non nota, per km del veicolo (si divide tra i viaggiatori). */
    const val CAR_KG_PER_VEHICLE_KM = 0.16725

    /** Fino a questa distanza un volo è "breve": decollo e atterraggio pesano di più. */
    const val SHORT_FLIGHT_MAX_KM = 500.0

    /** Percorso su strada e binari rispetto alla linea d'aria. */
    const val SURFACE_DETOUR = 1.2

    /** Oltre questa distanza in linea d'aria treno, pullman e auto non sono un'alternativa realistica. */
    const val SURFACE_MAX_KM = 2_000.0

    /** Sotto questa distanza la meta è vicina: niente confronto. */
    const val MIN_KM = 30.0

    /** Mezzi dal meno al più inquinante tra quelli realistici per la distanza; vuoto se la meta è vicina. */
    fun roundTrip(from: GeoPoint, to: GeoPoint, travellers: Int = 1): List<ModeFootprint> {
        require(travellers >= 1) { "Serve almeno un viaggiatore" }
        val straightKm = from.distanceTo(to) / 1_000
        if (straightKm < MIN_KM) return emptyList()
        val flightKm = straightKm * 2
        val flightFactor = if (straightKm <= SHORT_FLIGHT_MAX_KM) SHORT_FLIGHT_KG_PER_KM else FLIGHT_KG_PER_KM
        val flight = ModeFootprint(TravelMode.FLIGHT, flightKm, flightKm * flightFactor)
        if (straightKm > SURFACE_MAX_KM) return listOf(flight)
        val surfaceKm = straightKm * SURFACE_DETOUR * 2
        return listOf(
            flight,
            ModeFootprint(TravelMode.TRAIN, surfaceKm, surfaceKm * TRAIN_KG_PER_KM),
            ModeFootprint(TravelMode.COACH, surfaceKm, surfaceKm * COACH_KG_PER_KM),
            ModeFootprint(TravelMode.CAR, surfaceKm, surfaceKm * CAR_KG_PER_VEHICLE_KM / travellers),
        ).sortedBy { it.kgCo2e }
    }
}
