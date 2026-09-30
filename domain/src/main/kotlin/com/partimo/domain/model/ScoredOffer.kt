package com.partimo.domain.model

/**
 * Offerta arricchita con il punteggio qualità/prezzo calcolato dal dominio.
 *
 * @property valueScore punteggio in [0, 1] relativo ai risultati della stessa ricerca:
 * 1 indica il miglior rapporto qualità/prezzo.
 */
data class ScoredOffer<out T>(val offer: T, val valueScore: Double)
