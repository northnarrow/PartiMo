package com.partimo.domain.model.plan

import com.partimo.domain.model.Destination
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.event.TripEvent
import com.partimo.domain.model.poi.PointOfInterest
import java.time.LocalDate

/** Ritmo del viaggio: quante tappe al giorno propone l'itinerario. */
enum class TripPace(val stopsPerDay: IntRange) {
    RELAXED(2..3),
    BALANCED(3..4),
    INTENSE(5..6),
}

/** Interessi che orientano la scelta dei luoghi. */
enum class TripInterest { ART, HISTORY, FOOD, NATURE, SHOPPING, NIGHTLIFE, FAMILY }

/** Preferenze dell'utente per l'itinerario. */
data class TripPreferences(
    val pace: TripPace = TripPace.BALANCED,
    val interests: Set<TripInterest> = emptySet(),
)

/** Ciò che l'app sa del viaggio: date, luoghi da vedere ed eventi del soggiorno. */
data class TripKnowledge(
    val destination: Destination,
    val from: LocalDate,
    val to: LocalDate,
    val travellers: Int = 1,
    /** Luoghi consigliati per la stagione, dal più interessante. */
    val places: List<PointOfInterest> = emptyList(),
    /** Eventi tra arrivo e partenza (mercatini, festival, festività). */
    val events: List<TripEvent> = emptyList(),
) {
    init {
        require(!to.isBefore(from)) { "Il viaggio finisce prima di cominciare: $from–$to" }
    }

    /** Giorni del viaggio, compresi quelli di arrivo e di partenza. */
    val dayCount: Int get() = (to.toEpochDay() - from.toEpochDay()).toInt() + 1
}

/** Momento della giornata di una tappa. */
enum class DayPart { MORNING, AFTERNOON, EVENING }

/** Luogo o evento dell'app a cui si riferisce una tappa: si apre la sua scheda. */
sealed interface StopTarget {
    val location: GeoPoint?

    data class Place(val poi: PointOfInterest) : StopTarget {
        override val location: GeoPoint get() = poi.location
    }

    data class Event(val event: TripEvent) : StopTarget {
        override val location: GeoPoint? get() = event.location
    }
}

/**
 * Tappa dell'itinerario. Se l'app conosce il luogo [target] lo indica (scheda e coordinate); altrimenti
 * è un posto suggerito dall'assistente (es. un caffè storico), da cercare per nome sulla mappa.
 */
data class PlanStop(
    val dayPart: DayPart,
    val name: String,
    /** Cosa fare, in una o due frasi. */
    val activity: String,
    val durationMinutes: Int? = null,
    val target: StopTarget? = null,
) {
    val location: GeoPoint? get() = target?.location
}

/** Programma di una giornata. */
data class DayPlan(
    val date: LocalDate,
    /** Tema della giornata, es. "Arte imperiale e mercatini". */
    val title: String,
    val stops: List<PlanStop>,
    val tip: String? = null,
) {
    /** Somma delle distanze in linea d'aria tra tappe consecutive di cui si conosce la posizione. */
    val straightLineMeters: Double
        get() = stops.mapNotNull { it.location }.zipWithNext { a, b -> a.distanceTo(b) }.sum()
}

/** Gruppo della lista per la valigia, es. "Abbigliamento". */
data class PackingGroup(val category: String, val items: List<String>)

/** Itinerario giorno per giorno, lista per la valigia e consigli pratici, proposti dall'assistente. */
data class TripPlan(
    val days: List<DayPlan>,
    val packing: List<PackingGroup> = emptyList(),
    val tips: List<String> = emptyList(),
)

/** Messaggio della conversazione con l'assistente. */
data class ChatMessage(val role: ChatRole, val text: String)

enum class ChatRole { USER, ASSISTANT }
