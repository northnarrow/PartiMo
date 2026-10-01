package com.partimo.domain.model.backup

import com.partimo.domain.model.Travellers
import com.partimo.domain.model.booking.Booking
import com.partimo.domain.model.budget.TripBudget
import com.partimo.domain.model.deal.PriceWatch
import com.partimo.domain.model.place.DeparturePoint
import com.partimo.domain.model.saved.SavedTrip
import java.time.LocalDate

/**
 * Tutto ciò che l'utente ha salvato sul telefono: si esporta in un file (copia di sicurezza, telefono
 * nuovo) e si reimporta. Cache, pacchetti lingua e mappe non ci sono: si riscaricano da soli.
 */
data class UserData(
    val departure: DeparturePoint? = null,
    /** Viaggiatori scelti; `null` se l'utente non li ha mai indicati (una persona sola). */
    val travellers: Travellers? = null,
    val savedTrips: List<SavedTrip> = emptyList(),
    val priceWatches: List<PriceWatch> = emptyList(),
    /** Budget con un tetto o almeno una spesa. */
    val budgets: List<TripBudget> = emptyList(),
    /** Voci spuntate delle liste di controllo (es. la valigia di un viaggio), per lista. */
    val checklists: Map<String, Set<String>> = emptyMap(),
    /** Prenotazioni (voli, alloggi, treni...); i documenti allegati restano nei file dell'app. */
    val bookings: List<Booking> = emptyList(),
) {
    /** Quanto contengono i dati, per i messaggi all'utente. */
    val summary: UserDataSummary
        get() = UserDataSummary(
            trips = savedTrips.size,
            favorites = savedTrips.sumOf { it.favorites.size },
            alerts = priceWatches.size,
            expenses = budgets.sumOf { it.expenses.size },
            checkedItems = checklists.values.sumOf { it.size },
            hasDeparture = departure != null,
            bookings = bookings.size,
        )

    /**
     * Unisce i dati di un file a quelli del telefono senza perderne: viaggi, avvisi e budget del file
     * sostituiscono quelli uguali (stessa meta e periodo, stesso avviso), ma preferiti e spese dei viaggi
     * presenti in entrambi si sommano, come le voci spuntate. Il punto di partenza del file, se c'è, vince.
     */
    fun mergedWith(imported: UserData): UserData = UserData(
        departure = imported.departure ?: departure,
        travellers = imported.travellers ?: travellers,
        savedTrips = mergeById(savedTrips, imported.savedTrips, SavedTrip::id) { current, incoming ->
            incoming.copy(favorites = (incoming.favorites + current.favorites).distinctBy { it.key })
        },
        priceWatches = mergeById(priceWatches, imported.priceWatches, PriceWatch::id) { _, incoming -> incoming },
        budgets = mergeById(budgets, imported.budgets, TripBudget::tripId) { current, incoming ->
            incoming.copy(limit = incoming.limit ?: current.limit, expenses = (incoming.expenses + current.expenses).distinctBy { it.id })
        },
        checklists = (checklists.keys + imported.checklists.keys).associateWith { id ->
            checklists[id].orEmpty() + imported.checklists[id].orEmpty()
        },
        // Una prenotazione del file sostituisce quella con lo stesso id, ma il documento del telefono resta.
        bookings = mergeById(bookings, imported.bookings, Booking::id) { current, incoming ->
            incoming.copy(attachment = current.attachment, attachmentType = current.attachmentType)
        },
    )

    private companion object {
        /** Elementi di [current] (aggiornati con quelli del file con lo stesso id), poi quelli nuovi del file. */
        fun <T> mergeById(current: List<T>, incoming: List<T>, id: (T) -> String, merge: (T, T) -> T): List<T> {
            val incomingById = incoming.distinctBy(id).associateBy(id)
            val currentIds = current.mapTo(HashSet(), id)
            return current.map { item -> incomingById[id(item)]?.let { merge(item, it) } ?: item } +
                incomingById.values.filter { id(it) !in currentIds }
        }
    }
}

/** Riepilogo dei dati dell'utente: quanti viaggi, preferiti, avvisi, spese e voci spuntate. */
data class UserDataSummary(
    val trips: Int = 0,
    val favorites: Int = 0,
    val alerts: Int = 0,
    val expenses: Int = 0,
    val checkedItems: Int = 0,
    val hasDeparture: Boolean = false,
    val bookings: Int = 0,
) {
    val isEmpty: Boolean get() = trips == 0 && alerts == 0 && expenses == 0 && checkedItems == 0 && !hasDeparture && bookings == 0
}

/** File di backup pronto da salvare: nome proposto, contenuto e riepilogo di ciò che contiene. */
data class UserDataExport(val fileName: String, val content: String, val summary: UserDataSummary) {
    companion object {
        /** Nome proposto per il file di backup del giorno [date] (es. "PartiMo-backup-2026-10-01.json"). */
        fun fileName(date: LocalDate): String = "PartiMo-backup-$date.json"
    }
}
