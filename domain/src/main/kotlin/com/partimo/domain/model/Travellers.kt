package com.partimo.domain.model

/**
 * Chi parte: adulti e bambini con la loro età, che decide tariffe e sistemazioni. L'utente lo sceglie una
 * volta e vale per tutti i viaggi: prezzi dei voli, alloggi, collegamenti ai siti di prenotazione, CO₂
 * dell'auto divisa tra i viaggiatori e consigli dell'assistente.
 */
data class Travellers(
    val adults: Int = 1,
    /** Età dei bambini (0–17), una per bambino. */
    val childAges: List<Int> = emptyList(),
) {
    init {
        require(adults in 1..MAX_TRAVELLERS) { "Numero di adulti non valido: $adults" }
        require(adults + childAges.size <= MAX_TRAVELLERS) { "Al massimo $MAX_TRAVELLERS viaggiatori" }
        require(childAges.all { it in 0..MAX_CHILD_AGE }) { "Età dei bambini non valida: $childAges" }
    }

    val children: Int get() = childAges.size

    val total: Int get() = adults + children

    /** Bambini sotto i 2 anni: in aereo viaggiano in braccio a un adulto, senza un posto proprio. */
    val infants: Int get() = childAges.count { it < INFANT_AGE_LIMIT }

    /** Viaggiatori con un posto in aereo (tutti tranne i neonati): ognuno paga una tariffa. */
    val seatedPassengers: Int get() = total - infants

    /** In aereo ogni neonato deve stare in braccio a un adulto diverso. */
    val infantsHaveLaps: Boolean get() = infants <= adults

    val isSolo: Boolean get() = total == 1

    companion object {
        /** Passeggeri accettati in una sola prenotazione dai siti dei voli. */
        const val MAX_TRAVELLERS = 9
        const val MAX_CHILD_AGE = 17
        const val INFANT_AGE_LIMIT = 2

        /** Età proposta per un bambino appena aggiunto. */
        const val DEFAULT_CHILD_AGE = 8

        val SOLO = Travellers()
    }
}
