package com.partimo.domain.model.hours

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay

/**
 * Fascia di apertura in minuti dalla mezzanotte: [endMinute] può superare 1440 quando si chiude dopo
 * mezzanotte (es. 18:00-02:00 → 1080..1560). [openEnd] indica un orario di chiusura non precisato ("18:00+").
 */
data class TimeSpan(val startMinute: Int, val endMinute: Int, val openEnd: Boolean = false) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "Inizio fuori dal giorno: $startMinute" }
        require(endMinute > startMinute && endMinute <= 2 * MINUTES_PER_DAY) { "Fine non valida: $startMinute–$endMinute" }
    }

    val start: LocalTime get() = LocalTime.of(startMinute / 60, startMinute % 60)

    /** Ora di chiusura (sul quadrante: 26:00 diventa 02:00). */
    val end: LocalTime get() = (endMinute % MINUTES_PER_DAY).let { LocalTime.of(it / 60, it % 60) }

    val isWholeDay: Boolean get() = startMinute == 0 && endMinute == MINUTES_PER_DAY

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        val WHOLE_DAY = TimeSpan(0, MINUTES_PER_DAY)
    }
}

/** Regola degli orari: in certi mesi e giorni della settimana, aperto nelle fasce indicate oppure chiuso. */
data class OpeningRule(
    /** Mesi in cui vale; vuoto = tutto l'anno. */
    val months: Set<Month> = emptySet(),
    /** Giorni specifici (es. 25 dicembre) in cui vale, al posto dei giorni della settimana; vuoto = nessuno. */
    val dates: Set<MonthDay> = emptySet(),
    /** Giorni della settimana in cui vale; vuoto = tutti. */
    val days: Set<DayOfWeek> = emptySet(),
    /** Fasce di apertura; vuota con [closed] = chiuso. */
    val spans: List<TimeSpan> = emptyList(),
    val closed: Boolean = false,
    /** `true` per una regola che si aggiunge alla precedente (separata da virgola) invece di sostituirla. */
    val additional: Boolean = false,
) {
    fun appliesTo(date: LocalDate): Boolean {
        if (dates.isNotEmpty()) return MonthDay.from(date) in dates
        return (months.isEmpty() || date.month in months) && (days.isEmpty() || date.dayOfWeek in days)
    }
}

/** Stato di apertura in un momento preciso. */
sealed interface OpenState {
    /** Aperto; [closesAt] è `null` se non chiude (24/7 o orario di chiusura non indicato). */
    data class Open(val closesAt: LocalDateTime?) : OpenState

    /** Chiuso; [opensAt] è la prossima apertura entro una settimana, se c'è. */
    data class Closed(val opensAt: LocalDateTime?) : OpenState
}

/**
 * Orari di apertura nel formato di OpenStreetMap (`opening_hours`, es. "Mo-Fr 09:00-18:00; Sa 10:00-14:00").
 * Le regole separate da ";" si applicano in ordine e quelle successive sostituiscono le precedenti per i
 * giorni che descrivono; quelle separate da "," si aggiungono. Si costruisce con [OpeningHoursParser].
 */
class OpeningHours internal constructor(
    val rules: List<OpeningRule>,
    val alwaysOpen: Boolean = false,
    /** Testo originale, da mostrare se serve. */
    val source: String = "",
) {

    /** Fasce di apertura di un giorno (dalla sua mezzanotte; possono finire dopo la mezzanotte successiva). */
    fun spansOn(date: LocalDate): List<TimeSpan> {
        if (alwaysOpen && rules.none { it.appliesTo(date) }) return listOf(TimeSpan.WHOLE_DAY)
        var spans: List<TimeSpan>? = if (alwaysOpen) listOf(TimeSpan.WHOLE_DAY) else null
        for (rule in rules) {
            if (!rule.appliesTo(date)) continue
            val ruleSpans = if (rule.closed) emptyList() else rule.spans
            spans = if (rule.additional && !rule.closed) spans.orEmpty() + ruleSpans else ruleSpans
        }
        return spans.orEmpty().sortedBy { it.startMinute }
    }

    fun stateAt(moment: LocalDateTime): OpenState {
        val date = moment.toLocalDate()
        val minute = moment.hour * 60 + moment.minute
        // Fasce di oggi e quelle di ieri che proseguono dopo la mezzanotte.
        val current = spansOn(date).map { it to date } +
            spansOn(date.minusDays(1)).filter { it.endMinute > TimeSpan.MINUTES_PER_DAY }.map { it to date.minusDays(1) }
        current.firstOrNull { (span, day) ->
            val offset = (date.toEpochDay() - day.toEpochDay()).toInt() * TimeSpan.MINUTES_PER_DAY
            minute + offset >= span.startMinute && (span.openEnd || minute + offset < span.endMinute)
        }?.let { (span, day) -> return OpenState.Open(closingTime(span, day)) }
        return OpenState.Closed(nextOpening(moment))
    }

    /** Chiusura della fascia; se a mezzanotte prosegue nella fascia del giorno dopo, la chiusura è quella. */
    private fun closingTime(span: TimeSpan, day: LocalDate): LocalDateTime? {
        if (span.openEnd) return null
        if (span.endMinute == TimeSpan.MINUTES_PER_DAY) {
            val next = spansOn(day.plusDays(1)).firstOrNull { it.startMinute == 0 }
            if (next != null) return if (next.isWholeDay || next.openEnd) null else day.plusDays(1).atStartOfDay().plusMinutes(next.endMinute.toLong())
        }
        return day.atStartOfDay().plusMinutes(span.endMinute.toLong())
    }

    private fun nextOpening(moment: LocalDateTime): LocalDateTime? {
        for (offset in 0..DAYS_TO_LOOK_AHEAD) {
            val day = moment.toLocalDate().plusDays(offset.toLong())
            spansOn(day).map { day.atStartOfDay().plusMinutes(it.startMinute.toLong()) }.firstOrNull { it.isAfter(moment) }?.let { return it }
        }
        return null
    }

    private companion object {
        const val DAYS_TO_LOOK_AHEAD = 7
    }
}
