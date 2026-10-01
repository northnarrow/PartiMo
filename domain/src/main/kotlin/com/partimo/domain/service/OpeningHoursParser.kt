package com.partimo.domain.service

import com.partimo.domain.model.hours.OpeningHours
import com.partimo.domain.model.hours.OpeningRule
import com.partimo.domain.model.hours.TimeSpan
import java.time.DayOfWeek
import java.time.Month
import java.time.MonthDay

/**
 * Interpreta gli orari di OpenStreetMap (`opening_hours`) nei casi più comuni:
 * "24/7", "Mo-Fr 09:00-18:00; Sa 10:00-14:00", "Tu-Su 10:00-12:00,14:00-18:00", "18:00-02:00",
 * "Nov-Mar Mo-Su 10:00-16:00; Apr-Oct 09:00-18:00", "Mo off", "Dec 25 off", "Mo-Fr 09:00-12:00, We 14:00-18:00".
 *
 * Le regole sulle festività ("PH", "SH") vengono ignorate, così come le eccezioni di chiusura che non si
 * sanno leggere. Con orari variabili (alba, tramonto), settimane o anni il risultato è `null`: meglio
 * mostrare il testo originale che un orario sbagliato.
 * https://wiki.openstreetmap.org/wiki/Key:opening_hours/specification
 */
object OpeningHoursParser {

    private val DAY_CODES = mapOf(
        "mo" to DayOfWeek.MONDAY, "tu" to DayOfWeek.TUESDAY, "we" to DayOfWeek.WEDNESDAY, "th" to DayOfWeek.THURSDAY,
        "fr" to DayOfWeek.FRIDAY, "sa" to DayOfWeek.SATURDAY, "su" to DayOfWeek.SUNDAY,
    )
    private val MONTH_CODES = Month.entries.associateBy { it.name.take(3).lowercase() }
    private val HOLIDAY_CODES = setOf("ph", "sh")
    private val CLOSED_WORDS = setOf("off", "closed")

    private val QUOTED_COMMENT = Regex("\"[^\"]*\"")
    private val RULE_SEPARATOR = Regex("""\s*(?:;|\|\|)\s*""")

    /** Una virgola dopo un orario (o "off") seguita da un giorno o un mese apre una regola aggiuntiva. */
    private val ADDITIONAL_RULE = Regex("""(?<=\d|off|closed)\s*,\s*(?=[A-Za-z]{2})""")
    private val LIST_COMMA = Regex(""",\s+""")
    private val TIME = Regex("""(\d{1,2}):(\d{2})""")
    private val SPAN = Regex("""^(\d{1,2}:\d{2})(?:-(\d{1,2}:\d{2}))?(\+)?$""")

    fun parse(text: String?): OpeningHours? {
        val source = text?.trim().orEmpty()
        if (source.isEmpty()) return null
        val rules = mutableListOf<OpeningRule>()
        var alwaysOpen = false
        for (segment in source.replace(QUOTED_COMMENT, " ").split(RULE_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }) {
            segment.split(ADDITIONAL_RULE).map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { index, ruleText ->
                if (ruleText == "24/7") {
                    if (rules.isEmpty()) alwaysOpen = true else rules += OpeningRule(spans = listOf(TimeSpan.WHOLE_DAY), additional = index > 0)
                    return@forEachIndexed
                }
                when (val parsed = parseRule(ruleText)) {
                    is RuleResult.Parsed -> rules += parsed.rule.copy(additional = index > 0)
                    RuleResult.Skip -> Unit
                    // Un'eccezione di chiusura illeggibile si ignora; un orario illeggibile rende inaffidabile tutto.
                    RuleResult.Unsupported -> if (CLOSED_WORDS.none { ruleText.lowercase().endsWith(it) }) return null
                }
            }
        }
        if (rules.isEmpty() && !alwaysOpen) return null
        return OpeningHours(rules = rules, alwaysOpen = alwaysOpen, source = source)
    }

    private sealed interface RuleResult {
        data class Parsed(val rule: OpeningRule) : RuleResult

        /** Regola valida ma irrilevante per l'app (festività). */
        data object Skip : RuleResult

        data object Unsupported : RuleResult
    }

    private fun parseRule(text: String): RuleResult {
        // "Sa, Su 09:00-23:00" e "11:30-14:30, 18:00-22:00": gli spazi dopo le virgole non separano nulla.
        val tokens = text.replace(LIST_COMMA, ",").split(Regex("""\s+""")).filter { it.isNotEmpty() }.toMutableList()
        // "Apr-Oct: Mo-Su 09:00-18:00": i due punti dopo i mesi sono facoltativi.
        if (tokens.isNotEmpty() && tokens[0].endsWith(":") && monthOf(tokens[0].take(3)) != null) tokens[0] = tokens[0].dropLast(1)
        var months = emptySet<Month>()
        var dates = emptySet<MonthDay>()
        // Mesi o date: "Nov-Mar", "Jul,Aug", "Dec 24", "Dec 24-26", "Dec 24,25,31".
        if (tokens.isNotEmpty() && monthOf(tokens[0].take(3)) != null && !tokens[0].contains(':')) {
            val monthToken = tokens.removeAt(0)
            val dayToken = tokens.firstOrNull()?.takeIf { it.first().isDigit() && !it.contains(':') }
            if (dayToken != null) {
                tokens.removeAt(0)
                val month = monthOf(monthToken) ?: return RuleResult.Unsupported
                dates = daysOfMonth(month, dayToken) ?: return RuleResult.Unsupported
            } else {
                months = monthSelector(monthToken) ?: return RuleResult.Unsupported
            }
        }
        // Giorni della settimana: "Mo-Fr", "Mo,We,Fr", "Sa,Su,PH"; solo festività = regola da ignorare.
        var days = emptySet<DayOfWeek>()
        val dayToken = tokens.firstOrNull()?.takeIf { token -> token.split(',', '-').all { it.lowercase() in DAY_CODES || it.lowercase() in HOLIDAY_CODES } }
        if (dayToken != null) {
            tokens.removeAt(0)
            val selected = weekdaySelector(dayToken) ?: return RuleResult.Unsupported
            if (selected.isEmpty()) return RuleResult.Skip
            days = selected
        }
        val rest = tokens.joinToString(" ").lowercase()
        return when {
            rest in CLOSED_WORDS -> RuleResult.Parsed(OpeningRule(months, dates, days, closed = true))
            rest.isEmpty() || rest == "open" -> if (months.isEmpty() && dates.isEmpty() && days.isEmpty()) {
                RuleResult.Unsupported
            } else {
                RuleResult.Parsed(OpeningRule(months, dates, days, spans = listOf(TimeSpan.WHOLE_DAY)))
            }
            else -> timeSpans(rest)?.let { RuleResult.Parsed(OpeningRule(months, dates, days, spans = it)) } ?: RuleResult.Unsupported
        }
    }

    private fun monthOf(token: String): Month? = MONTH_CODES[token.lowercase()]

    /** "Nov-Mar" (anche a cavallo dell'anno), "Jul,Aug", "Apr-Jun,Sep". */
    private fun monthSelector(token: String): Set<Month>? {
        val result = mutableSetOf<Month>()
        for (part in token.split(',')) {
            val bounds = part.split('-')
            val start = monthOf(bounds[0]) ?: return null
            val end = if (bounds.size == 2) monthOf(bounds[1]) ?: return null else start
            if (bounds.size > 2) return null
            var month = start
            while (true) {
                result += month
                if (month == end) break
                month = month.plus(1)
            }
        }
        return result
    }

    /** "24", "24-26", "24,25,31" nel mese indicato. */
    private fun daysOfMonth(month: Month, token: String): Set<MonthDay>? {
        val result = mutableSetOf<MonthDay>()
        for (part in token.split(',')) {
            val bounds = part.split('-').map { it.toIntOrNull() ?: return null }
            if (bounds.size > 2) return null
            val range = bounds.first()..bounds.last()
            for (day in range) result += runCatching { MonthDay.of(month, day) }.getOrNull() ?: return null
        }
        return result
    }

    /** Giorni della settimana; le festività (PH, SH) si tolgono: insieme vuoto = solo festività. */
    private fun weekdaySelector(token: String): Set<DayOfWeek>? {
        val result = mutableSetOf<DayOfWeek>()
        for (part in token.lowercase().split(',')) {
            if (part in HOLIDAY_CODES) continue
            val bounds = part.split('-')
            val start = DAY_CODES[bounds[0]] ?: return null
            val end = if (bounds.size == 2) DAY_CODES[bounds[1]] ?: return null else start
            if (bounds.size > 2) return null
            var day = start
            while (true) {
                result += day
                if (day == end) break
                day = day.plus(1)
            }
        }
        return result
    }

    /** "09:00-12:00,14:00-18:00", "18:00-02:00", "10:00+"; `null` per orari variabili o malformati. */
    private fun timeSpans(text: String): List<TimeSpan>? {
        val spans = mutableListOf<TimeSpan>()
        for (part in text.replace(" ", "").split(',')) {
            val match = SPAN.matchEntire(part) ?: return null
            val start = minutes(match.groupValues[1]) ?: return null
            val openEnd = match.groupValues[3].isNotEmpty()
            val endText = match.groupValues[2]
            if (start >= TimeSpan.MINUTES_PER_DAY) return null
            val span = if (endText.isEmpty()) {
                if (!openEnd) return null
                TimeSpan(start, TimeSpan.MINUTES_PER_DAY, openEnd = true)
            } else {
                var end = minutes(endText) ?: return null
                if (end <= start) end += TimeSpan.MINUTES_PER_DAY
                if (end > 2 * TimeSpan.MINUTES_PER_DAY) return null
                TimeSpan(start, end, openEnd = openEnd)
            }
            spans += span
        }
        return spans.takeIf { it.isNotEmpty() }
    }

    private fun minutes(time: String): Int? {
        val match = TIME.matchEntire(time) ?: return null
        val hours = match.groupValues[1].toInt()
        val minutes = match.groupValues[2].toInt()
        if (hours > 24 || minutes > 59 || (hours == 24 && minutes > 0)) return null
        return hours * 60 + minutes
    }
}
