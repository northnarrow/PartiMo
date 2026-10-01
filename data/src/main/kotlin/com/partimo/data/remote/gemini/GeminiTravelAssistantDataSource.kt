package com.partimo.data.remote.gemini

import com.partimo.data.cache.CacheKey
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.network.Fetched
import com.partimo.data.network.NetworkJson
import com.partimo.data.network.UnusableResponseException
import com.partimo.data.source.TravelAssistantDataSource
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.plan.ChatMessage
import com.partimo.domain.model.plan.ChatRole
import com.partimo.domain.model.plan.DayPart
import com.partimo.domain.model.plan.DayPlan
import com.partimo.domain.model.plan.PackingGroup
import com.partimo.domain.model.plan.PlanStop
import com.partimo.domain.model.plan.StopTarget
import com.partimo.domain.model.plan.TripKnowledge
import com.partimo.domain.model.plan.TripPlan
import com.partimo.domain.model.plan.TripPreferences
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.LocalDate

/**
 * Assistente di viaggio con Google Gemini.
 *
 * L'itinerario è chiesto in JSON secondo uno schema e resta in cache: riaprirlo non consuma la quota
 * gratuita, "Rigenera" ne chiede uno nuovo. Le risposte alle domande non vengono salvate.
 */
internal class GeminiTravelAssistantDataSource(
    private val api: GeminiApi,
    private val cache: ResponseCache,
    private val clock: Clock = Clock.systemDefaultZone(),
) : TravelAssistantDataSource {

    override suspend fun planTrip(knowledge: TripKnowledge, preferences: TripPreferences, forceRefresh: Boolean): Fetched<TripPlan> {
        val prompt = TravelPrompts.plan(knowledge, preferences)
        val request = GeminiRequest(
            systemInstruction = GeminiContent.system(prompt.system),
            contents = listOf(GeminiContent.user(prompt.user)),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = JSON_MIME_TYPE,
                responseSchema = TravelPrompts.PLAN_SCHEMA,
                temperature = PLAN_TEMPERATURE,
                maxOutputTokens = PLAN_MAX_OUTPUT_TOKENS,
            ),
        )
        return cache.getOrFetch(
            key = CacheKey.of("ai-plan", TravelPrompts.VERSION, prompt.system, prompt.user),
            ttl = CachePolicy.AI_PLAN,
            forceRefresh = forceRefresh,
            // Un JSON troncato o senza giorni fa provare il modello successivo.
            fetch = { api.generate(request) { text -> parsePlan(text, knowledge, prompt.targets) }.text },
            parse = { text -> parsePlan(text, knowledge, prompt.targets) },
        )
    }

    override suspend fun answer(knowledge: TripKnowledge, conversation: List<ChatMessage>): Fetched<String> {
        val request = GeminiRequest(
            systemInstruction = GeminiContent.system(TravelPrompts.chatSystem(knowledge, LocalDate.now(clock))),
            contents = conversation.map { message ->
                when (message.role) {
                    ChatRole.USER -> GeminiContent.user(message.text)
                    ChatRole.ASSISTANT -> GeminiContent.model(message.text)
                }
            },
            generationConfig = GeminiGenerationConfig(temperature = CHAT_TEMPERATURE, maxOutputTokens = CHAT_MAX_OUTPUT_TOKENS),
        )
        val output = api.generate(request, hedgeAfterMillis = CHAT_HEDGE_AFTER_MILLIS) { text ->
            plainText(text).ifBlank { throw UnusableResponseException("Risposta vuota") }
        }
        return Fetched(output.value, DataOrigin.REMOTE)
    }

    private fun parsePlan(text: String, knowledge: TripKnowledge, targets: Map<String, StopTarget>): TripPlan {
        val dto = NetworkJson.decodeFromString(PlanDto.serializer(), text)
        val days = dto.days.mapNotNull { day ->
            if (day.day !in 1..knowledge.dayCount) return@mapNotNull null
            val stops = day.stops.mapNotNull { stop -> stop.toDomain(targets) }
            if (stops.isEmpty()) null else DayPlan(
                date = knowledge.from.plusDays(day.day - 1L),
                title = day.title.trim(),
                stops = stops,
                tip = day.tip?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        if (days.isEmpty()) throw UnusableResponseException("Itinerario senza giorni")
        return TripPlan(
            days = days,
            packing = dto.packing.map { group -> PackingGroup(group.category.trim(), group.items.map { it.trim() }) },
            tips = dto.tips.map { it.trim() },
        )
    }

    /** Tappa del modello: per i luoghi e gli eventi dell'app si usa il loro nome, come nel resto dell'app. */
    private fun StopDto.toDomain(targets: Map<String, StopTarget>): PlanStop? {
        val target = targets[ref.trim().uppercase()]
        val stopName = when (target) {
            is StopTarget.Place -> target.poi.name
            is StopTarget.Event -> target.event.name
            null -> name.trim()
        }
        if (stopName.isEmpty()) return null
        return PlanStop(
            dayPart = DayPart.entries.firstOrNull { it.name == time.trim().uppercase() } ?: DayPart.AFTERNOON,
            name = stopName,
            activity = activity.trim(),
            durationMinutes = minutes?.takeIf { it in 1..MAX_STOP_MINUTES },
            target = target,
        )
    }

    companion object {
        private const val JSON_MIME_TYPE = "application/json"
        private const val PLAN_TEMPERATURE = 0.7
        private const val PLAN_MAX_OUTPUT_TOKENS = 8_192
        private const val CHAT_TEMPERATURE = 0.7
        private const val CHAT_MAX_OUTPUT_TOKENS = 2_048
        private const val CHAT_HEDGE_AFTER_MILLIS = 8_000L
        private const val MAX_STOP_MINUTES = 12 * 60

        private val BULLET = Regex("""^\s*[*-]\s+""", RegexOption.MULTILINE)
        private val HEADING = Regex("""^\s*#{1,6}\s*""", RegexOption.MULTILINE)
        private val EMPHASIS = Regex("""\*\*(.+?)\*\*|__(.+?)__""")

        /** Testo senza la formattazione markdown che i modelli usano spesso anche se chiesto di evitarla. */
        internal fun plainText(text: String): String = text
            .replace(EMPHASIS) { match -> match.groupValues[1].ifEmpty { match.groupValues[2] } }
            .replace(BULLET, "• ")
            .replace(HEADING, "")
            .trim()
    }
}

@Serializable
internal data class PlanDto(
    val days: List<DayDto> = emptyList(),
    val packing: List<PackingDto> = emptyList(),
    val tips: List<String> = emptyList(),
)

@Serializable
internal data class DayDto(
    val day: Int,
    val title: String = "",
    val stops: List<StopDto> = emptyList(),
    val tip: String? = null,
)

@Serializable
internal data class StopDto(
    val time: String = "",
    val ref: String = "",
    val name: String = "",
    val activity: String = "",
    val minutes: Int? = null,
)

@Serializable
internal data class PackingDto(val category: String = "", val items: List<String> = emptyList())
