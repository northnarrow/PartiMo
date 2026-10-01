package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.getOrNull
import com.partimo.domain.model.Destination
import com.partimo.domain.model.plan.ChatMessage
import com.partimo.domain.model.plan.ChatRole
import com.partimo.domain.model.plan.DayPlan
import com.partimo.domain.model.plan.StopTarget
import com.partimo.domain.model.plan.TripKnowledge
import com.partimo.domain.model.plan.TripPlan
import com.partimo.domain.model.plan.TripPreferences
import com.partimo.domain.repository.ChecklistRepository
import com.partimo.domain.repository.TravelAssistantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

/**
 * Raccoglie ciò che l'app sa del viaggio (luoghi da vedere ed eventi del soggiorno) per darlo
 * all'assistente. Le due fonti sono facoltative: senza, l'assistente usa le sue conoscenze. I dati
 * arrivano di solito dalla cache, già riempita dalla dashboard.
 */
class LoadTripKnowledgeUseCase(
    private val getSeasonalHighlights: GetSeasonalHighlightsUseCase,
    private val getTripEvents: GetTripEventsUseCase,
    private val getTripWeather: GetTripWeatherUseCase? = null,
    private val maxPlaces: Int = DEFAULT_MAX_PLACES,
) {

    suspend operator fun invoke(destination: Destination, from: LocalDate, to: LocalDate, travellers: Int = 1): TripKnowledge =
        coroutineScope {
            val highlights = async { getSeasonalHighlights(destination.center, from, areaName = destination.name) }
            val events = async { getTripEvents(destination, from, to) }
            val weather = async { getTripWeather?.invoke(destination.center, from, to)?.getOrNull() }
            TripKnowledge(
                destination = destination,
                from = from,
                to = to,
                travellers = travellers,
                places = highlights.await().getOrNull()?.recommendations?.map { it.poi }.orEmpty().take(maxPlaces),
                events = events.await().getOrNull()?.events.orEmpty(),
                weather = weather.await(),
            )
        }

    private companion object {
        /** Abbastanza luoghi per scegliere, senza appesantire la richiesta al modello. */
        const val DEFAULT_MAX_PLACES = 25
    }
}

/**
 * Itinerario giorno per giorno proposto dall'assistente, con lista per la valigia e consigli.
 *
 * La proposta del modello viene ripulita: si tengono solo i giorni del viaggio, in ordine e senza
 * doppioni, e ogni luogo o evento dell'app compare una volta sola.
 */
class PlanTripUseCase(
    private val assistant: TravelAssistantRepository,
    private val loadTripKnowledge: LoadTripKnowledgeUseCase,
) {

    val isAvailable: Boolean get() = assistant.isAvailable

    suspend operator fun invoke(
        destination: Destination,
        from: LocalDate,
        to: LocalDate,
        preferences: TripPreferences = TripPreferences(),
        forceRefresh: Boolean = false,
    ): DataResult<TripPlan> {
        val knowledge = loadTripKnowledge(destination, from, to)
        return when (val result = assistant.planTrip(knowledge, preferences, forceRefresh)) {
            is DataResult.Failure -> result
            is DataResult.Success -> {
                val plan = result.data.cleanedFor(from, to)
                if (plan.days.isEmpty()) DataResult.Failure(DataError.InvalidResponse) else DataResult.Success(plan, result.origin)
            }
        }
    }

    private fun TripPlan.cleanedFor(from: LocalDate, to: LocalDate): TripPlan {
        val seenTargets = mutableSetOf<String>()
        val days = days
            .filter { day -> !day.date.isBefore(from) && !day.date.isAfter(to) }
            .sortedBy { it.date }
            .distinctBy { it.date }
            .map { day -> day.withUniqueStops(seenTargets) }
            .filter { it.stops.isNotEmpty() }
        return copy(
            days = days,
            packing = packing.map { group -> group.copy(items = group.items.filter { it.isNotBlank() }.distinct()) }
                .filter { it.category.isNotBlank() && it.items.isNotEmpty() },
            tips = tips.filter { it.isNotBlank() }.distinct(),
        )
    }

    /** Tappe in ordine di momento della giornata (mattina, pomeriggio, sera), senza luoghi già visitati. */
    private fun DayPlan.withUniqueStops(seenTargets: MutableSet<String>): DayPlan = copy(
        stops = stops
            .filter { it.name.isNotBlank() }
            .filter { stop -> stop.target?.let { seenTargets.add(it.key) } ?: true }
            .sortedBy { it.dayPart },
    )

    private val StopTarget.key: String
        get() = when (this) {
            is StopTarget.Place -> "place:" + poi.id
            is StopTarget.Event -> "event:" + event.id
        }
}

/**
 * Domanda all'assistente ("Chiedi a PartiMo"): risponde tenendo conto del viaggio e della
 * conversazione. Si inviano solo gli ultimi messaggi, sufficienti per il contesto.
 */
class AskTravelAssistantUseCase(
    private val assistant: TravelAssistantRepository,
    private val maxMessages: Int = DEFAULT_MAX_MESSAGES,
) {

    val isAvailable: Boolean get() = assistant.isAvailable

    suspend operator fun invoke(knowledge: TripKnowledge, conversation: List<ChatMessage>): DataResult<String> {
        val question = conversation.lastOrNull()
        require(question != null && question.role == ChatRole.USER && question.text.isNotBlank()) {
            "La conversazione deve finire con una domanda dell'utente"
        }
        // La conversazione inviata comincia sempre con una domanda, come richiedono i modelli.
        val recent = conversation.takeLast(maxMessages).dropWhile { it.role != ChatRole.USER }
        return when (val result = assistant.answer(knowledge, recent)) {
            is DataResult.Failure -> result
            is DataResult.Success ->
                if (result.data.isBlank()) DataResult.Failure(DataError.InvalidResponse) else DataResult.Success(result.data.trim(), result.origin)
        }
    }

    private companion object {
        const val DEFAULT_MAX_MESSAGES = 20
    }
}

/** Voci della lista per la valigia già messe in valigia, salvate per ciascun viaggio. */
class PackingChecklistUseCase(private val repository: ChecklistRepository) {

    fun packedItems(destination: Destination, from: LocalDate): Flow<Set<String>> =
        repository.checkedItems(listId(destination, from))

    suspend fun setPacked(destination: Destination, from: LocalDate, item: String, packed: Boolean) {
        repository.setChecked(listId(destination, from), item, packed)
    }

    /** Un viaggio è identificato da meta e giorno di partenza. */
    private fun listId(destination: Destination, from: LocalDate): String =
        "packing:${destination.countryCode}:${destination.name}:$from"
}
