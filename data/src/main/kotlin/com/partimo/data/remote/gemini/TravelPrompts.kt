package com.partimo.data.remote.gemini

import com.partimo.data.network.NetworkJson
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.event.TripEvent
import com.partimo.domain.model.plan.StopTarget
import com.partimo.domain.model.plan.TripInterest
import com.partimo.domain.model.plan.TripKnowledge
import com.partimo.domain.model.plan.TripPace
import com.partimo.domain.model.plan.TripPreferences
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.weather.TripWeather
import com.partimo.domain.model.weather.WeatherCondition
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Richiesta di itinerario pronta da inviare: testi del prompt e codici brevi ("L3", "E1") con cui il
 * modello indica luoghi ed eventi dell'app, più robusti degli id interni.
 */
internal class PlanPrompt(
    val system: String,
    val user: String,
    val targets: Map<String, StopTarget>,
)

/** Testi inviati a Gemini. Cambiando le istruzioni va aumentata [VERSION], che invalida la cache. */
internal object TravelPrompts {

    const val VERSION = 1

    private val italian: Locale = Locale.ITALIAN

    private val PLAN_SYSTEM = """
        Sei PartiMo, l'assistente di viaggio di un'app italiana. Scrivi in italiano corretto, in modo concreto e conciso.
        Regole per l'itinerario:
        - Usa soprattutto i luoghi e gli eventi degli elenchi, indicando il loro codice in "ref" (es. "L3" o "E1"). Puoi aggiungere al massimo una tappa al giorno fuori elenco, per esempio un caffè storico, un ristorante tipico o un quartiere: deve esistere davvero ed essere conosciuto; in quel caso "ref" è una stringa vuota.
        - Ogni giorno raggruppa luoghi vicini tra loro, nell'ordine in cui conviene visitarli.
        - Il primo giorno è quello dell'arrivo (pomeriggio e sera), l'ultimo quello della partenza (solo mattina).
        - Rispetta le date degli eventi e le festività (negozi e alcuni musei chiusi).
        - Non ripetere un luogo in giorni diversi e non inventare prezzi o orari precisi.
        - La lista per la valigia dipende dalla stagione, dal clima e dalle attività. I consigli riguardano trasporti, usanze locali, mance e sicurezza.
    """.trimIndent()

    private val CHAT_SYSTEM = """
        Sei PartiMo, l'assistente di viaggio di un'app italiana. Rispondi in italiano, in modo cordiale, pratico e breve: al massimo 150 parole, salvo quando l'utente chiede dettagli.
        Scrivi testo semplice, senza markdown: niente asterischi né titoli; per gli elenchi usa righe che iniziano con "• ".
        Per le informazioni che cambiano nel tempo (orari, prezzi, regole di ingresso, scioperi) dì che vanno verificate sul sito ufficiale.
        Rispondi solo su viaggio, destinazione e organizzazione; per altri argomenti riporta gentilmente la conversazione sul viaggio.
    """.trimIndent()

    /** Schema JSON della risposta: giorni con tappe, lista per la valigia e consigli. */
    val PLAN_SCHEMA: JsonObject = NetworkJson.parseToJsonElement(
        """
        {"type":"OBJECT","properties":{
          "days":{"type":"ARRAY","items":{"type":"OBJECT","properties":{
            "day":{"type":"INTEGER","description":"Numero del giorno, da 1"},
            "title":{"type":"STRING","description":"Tema della giornata, al massimo 6 parole"},
            "stops":{"type":"ARRAY","items":{"type":"OBJECT","properties":{
              "time":{"type":"STRING","enum":["MORNING","AFTERNOON","EVENING"]},
              "ref":{"type":"STRING","description":"Codice del luogo o dell'evento (L1, E2...), vuoto se fuori elenco"},
              "name":{"type":"STRING"},
              "activity":{"type":"STRING","description":"Cosa fare, una o due frasi"},
              "minutes":{"type":"INTEGER","description":"Durata indicativa in minuti"}},
              "required":["time","ref","name","activity","minutes"],
              "propertyOrdering":["time","ref","name","activity","minutes"]}},
            "tip":{"type":"STRING","description":"Consiglio pratico per la giornata"}},
            "required":["day","title","stops","tip"],
            "propertyOrdering":["day","title","stops","tip"]}},
          "packing":{"type":"ARRAY","items":{"type":"OBJECT","properties":{
            "category":{"type":"STRING"},
            "items":{"type":"ARRAY","items":{"type":"STRING"}}},
            "required":["category","items"],
            "propertyOrdering":["category","items"]}},
          "tips":{"type":"ARRAY","items":{"type":"STRING"}}},
          "required":["days","packing","tips"],
          "propertyOrdering":["days","packing","tips"]}
        """.trimIndent(),
    ).jsonObject

    fun plan(knowledge: TripKnowledge, preferences: TripPreferences): PlanPrompt {
        val targets = linkedMapOf<String, StopTarget>()
        val user = buildString {
            appendLine("Viaggio a " + tripLine(knowledge) + ".")
            appendLine("Ritmo: " + paceText(preferences.pace) + ".")
            knowledge.weather?.let { appendLine(weatherText(it) + ".") }
            if (preferences.interests.isNotEmpty()) {
                appendLine("Interessi: " + preferences.interests.sortedBy { it.ordinal }.joinToString(", ") { interestText(it) } + ".")
            }
            if (knowledge.places.isNotEmpty()) {
                appendLine()
                appendLine("Luoghi da vedere (codice | nome | tipo | descrizione):")
                knowledge.places.forEachIndexed { index, poi ->
                    val code = "L${index + 1}"
                    targets[code] = StopTarget.Place(poi)
                    val description = poi.description?.trim()?.take(MAX_DESCRIPTION_CHARS).orEmpty()
                    appendLine("$code | ${poi.name} | ${categoryText(poi.category)} | $description".trimEnd(' ', '|'))
                }
            }
            if (knowledge.events.isNotEmpty()) {
                appendLine()
                appendLine("Eventi durante il soggiorno (codice | nome | quando):")
                knowledge.events.forEachIndexed { index, event ->
                    val code = "E${index + 1}"
                    targets[code] = StopTarget.Event(event)
                    appendLine("$code | ${event.name} | ${eventWhen(event, knowledge.from, knowledge.to)}")
                }
            }
        }.trim()
        return PlanPrompt(system = PLAN_SYSTEM, user = user, targets = targets)
    }

    /** Istruzioni per le domande: ruolo dell'assistente e tutto ciò che l'app sa del viaggio. */
    fun chatSystem(knowledge: TripKnowledge, today: LocalDate): String = buildString {
        appendLine(CHAT_SYSTEM)
        appendLine()
        appendLine("Viaggio dell'utente: " + tripLine(knowledge) + ".")
        appendLine("Oggi è " + longDate(today) + ".")
        knowledge.weather?.let { appendLine(weatherText(it) + ".") }
        if (knowledge.events.isNotEmpty()) {
            appendLine("Eventi durante il soggiorno:")
            knowledge.events.forEach { event -> appendLine("• ${event.name}: ${eventWhen(event, knowledge.from, knowledge.to)}") }
        }
        if (knowledge.places.isNotEmpty()) {
            appendLine("Luoghi consigliati dall'app: " + knowledge.places.take(MAX_CHAT_PLACES).joinToString(", ") { it.name } + ".")
        }
    }.trim()

    /** "Vienna (Austria) dal 10 al 14 dicembre 2026: 5 giorni, 1 viaggiatore". */
    private fun tripLine(knowledge: TripKnowledge): String {
        val destination = knowledge.destination
        val country = Locale("", destination.countryCode).getDisplayCountry(italian).takeIf { it.isNotBlank() && it != destination.countryCode }
        val place = if (country != null) "${destination.name} ($country)" else destination.name
        val days = if (knowledge.dayCount == 1) "1 giorno" else "${knowledge.dayCount} giorni"
        return "$place ${dateRange(knowledge.from, knowledge.to)}: $days, ${travellersText(knowledge.travellers)}"
    }

    /** "1 viaggiatore", "2 adulti", "2 adulti e 2 bambini (4 e 9 anni)": con i bambini il programma cambia. */
    private fun travellersText(travellers: Travellers): String {
        if (travellers.isSolo) return "1 viaggiatore"
        val adults = if (travellers.adults == 1) "1 adulto" else "${travellers.adults} adulti"
        if (travellers.children == 0) return adults
        val children = if (travellers.children == 1) "1 bambino" else "${travellers.children} bambini"
        val ages = travellers.childAges.sorted().map { if (it == 0) "meno di 1" else "$it" }
        val agesText = if (ages.size == 1) ages.single() else ages.dropLast(1).joinToString(", ") + " e " + ages.last()
        val years = if (travellers.childAges.size == 1 && travellers.childAges.single() == 1) "anno" else "anni"
        return "$adults e $children ($agesText $years)"
    }

    /** "dal 10 al 14 dicembre 2026", "dal 28 dicembre 2026 al 2 gennaio 2027" o "il 10 dicembre 2026". */
    private fun dateRange(from: LocalDate, to: LocalDate): String = when {
        from == to -> "il " + longDate(from)
        from.year == to.year && from.month == to.month -> "dal ${from.dayOfMonth} al ${longDate(to)}"
        from.year == to.year -> "dal ${from.dayOfMonth} ${monthName(from.month)} al ${longDate(to)}"
        else -> "dal ${longDate(from)} al ${longDate(to)}"
    }

    private fun longDate(date: LocalDate): String = "${date.dayOfMonth} ${monthName(date.month)} ${date.year}"

    private fun monthName(month: Month): String = month.getDisplayName(TextStyle.FULL, italian).lowercase(italian)

    /** Quando l'evento è in corso nei giorni del viaggio, più che cosa comporta per il visitatore. */
    private fun eventWhen(event: TripEvent, from: LocalDate, to: LocalDate): String {
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.filter(event.timing::isOn).toList()
        val dates = when {
            days.isEmpty() -> "periodo da verificare"
            days.size > 1 && days.size == (to.toEpochDay() - from.toEpochDay()).toInt() + 1 -> "tutti i giorni del soggiorno"
            else -> days.joinToString(", ") { "${it.dayOfMonth} ${monthName(it.month)}" }
        }
        val kind = when (event.kind) {
            EventKind.CHRISTMAS_MARKET -> "mercatino di Natale"
            EventKind.RECURRING_EVENT -> "evento che si ripete ogni anno"
            EventKind.PUBLIC_HOLIDAY -> "festa nazionale: negozi e uffici chiusi, musei e trasporti con orari ridotti"
        }
        return "$dates ($kind)"
    }

    /** "Previsioni: 10 dicembre pioggia, 4–9 °C; …" oppure "Clima tipico del periodo: massime 5 °C, minime 0 °C, …". */
    private fun weatherText(weather: TripWeather): String = when (weather) {
        is TripWeather.Forecast -> "Previsioni: " + weather.days.joinToString("; ") { day ->
            "${day.date.dayOfMonth} ${monthName(day.date.month)} ${conditionText(day.condition)}, " +
                "${day.minCelsius.roundToInt()}–${day.maxCelsius.roundToInt()} °C" +
                (day.precipitationProbability?.takeIf { it >= RAIN_PROBABILITY_WORTH_MENTIONING }?.let { ", pioggia $it%" } ?: "")
        }
        is TripWeather.Climate -> "Clima tipico del periodo (media degli ultimi ${weather.normals.years} anni): " +
            "massime ${weather.normals.averageMaxCelsius.roundToInt()} °C, minime ${weather.normals.averageMinCelsius.roundToInt()} °C, " +
            "pioggia o neve in circa ${(weather.normals.wetDaysShare * 100).roundToInt()}% dei giorni"
    }

    private fun conditionText(condition: WeatherCondition): String = when (condition) {
        WeatherCondition.CLEAR -> "sereno"
        WeatherCondition.PARTLY_CLOUDY -> "poco nuvoloso"
        WeatherCondition.OVERCAST -> "nuvoloso"
        WeatherCondition.FOG -> "nebbia"
        WeatherCondition.DRIZZLE -> "pioviggine"
        WeatherCondition.RAIN -> "pioggia"
        WeatherCondition.SNOW -> "neve"
        WeatherCondition.THUNDERSTORM -> "temporali"
        WeatherCondition.UNKNOWN -> "tempo incerto"
    }

    private fun paceText(pace: TripPace): String = when (pace) {
        TripPace.RELAXED -> "rilassato, ${pace.stopsPerDay.first} o ${pace.stopsPerDay.last} tappe al giorno con pause"
        TripPace.BALANCED -> "equilibrato, ${pace.stopsPerDay.first} o ${pace.stopsPerDay.last} tappe al giorno"
        TripPace.INTENSE -> "intenso, ${pace.stopsPerDay.first} o ${pace.stopsPerDay.last} tappe al giorno"
    }

    private fun interestText(interest: TripInterest): String = when (interest) {
        TripInterest.ART -> "arte e musei"
        TripInterest.HISTORY -> "storia e monumenti"
        TripInterest.FOOD -> "cibo e cucina tipica"
        TripInterest.NATURE -> "natura e parchi"
        TripInterest.SHOPPING -> "shopping"
        TripInterest.NIGHTLIFE -> "vita notturna"
        TripInterest.FAMILY -> "viaggio con bambini"
    }

    private fun categoryText(category: PoiCategory): String = when (category) {
        PoiCategory.MUSEUM -> "museo"
        PoiCategory.MONUMENT -> "monumento"
        PoiCategory.RELIGIOUS_SITE -> "luogo di culto"
        PoiCategory.VIEWPOINT -> "punto panoramico"
        PoiCategory.PARK -> "parco"
        PoiCategory.BEACH -> "spiaggia"
        PoiCategory.MARKET -> "mercato"
        PoiCategory.SEASONAL_EVENT -> "evento stagionale"
        PoiCategory.SKI_AREA -> "area sciistica"
        PoiCategory.NEIGHBORHOOD -> "quartiere"
        PoiCategory.ATTRACTION -> "attrazione"
        PoiCategory.SHOPPING -> "shopping"
        PoiCategory.OTHER -> "luogo"
    }

    private const val MAX_DESCRIPTION_CHARS = 80
    private const val RAIN_PROBABILITY_WORTH_MENTIONING = 30
    private const val MAX_CHAT_PLACES = 15
}
