package com.partimo.app.ui.common

import com.partimo.domain.model.Money
import com.partimo.domain.model.dining.PriceLevel
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Formattazione dei valori per la UI (locale italiano).
 * Usa solo API disponibili dalla minSdk 26 (niente metodi java.time introdotti dopo Java 8).
 */
object Formatters {

    private val locale: Locale = Locale.ITALY
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
    private val dayFormatter = DateTimeFormatter.ofPattern("d", locale)
    private val weekdayDayMonthFormatter = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val weekdayDayFormatter = DateTimeFormatter.ofPattern("EEE d", locale)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)

    /** Oltre questo importo i decimali non servono (es. 2.443 corone). */
    private const val LARGE_AMOUNT = 1000

    /** Importi interi senza decimali ("129 €"), altrimenti con i centesimi ("129,40 €"). */
    fun money(money: Money): String {
        val hasCents = money.amount.remainder(BigDecimal.ONE).signum() != 0
        val format = NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(money.currencyCode)
            minimumFractionDigits = if (hasCents) 2 else 0
            maximumFractionDigits = if (hasCents) 2 else 0
        }
        return format.format(money.amount)
    }

    fun duration(duration: Duration): String {
        val totalMinutes = duration.toMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> "$minutes min"
            minutes == 0L -> "$hours h"
            else -> "$hours h $minutes min"
        }
    }

    fun time(dateTime: LocalDateTime): String = dateTime.format(timeFormatter)

    fun time(instant: Instant, zone: ZoneId): String = instant.atZone(zone).format(timeFormatter)

    /**
     * "12–16 dic" nello stesso mese, altrimenti "28 dic – 2 gen".
     * In italiano i mesi vanno in minuscolo, ma alcuni dati di localizzazione Android li
     * restituiscono con l'iniziale maiuscola: il testo viene quindi normalizzato.
     */
    fun dateRange(start: LocalDate, end: LocalDate): String {
        val text = if (start.year == end.year && start.month == end.month) {
            "${start.format(dayFormatter)}–${end.format(dayMonthFormatter)}"
        } else {
            "${start.format(dayMonthFormatter)} – ${end.format(dayMonthFormatter)}"
        }
        return text.lowercase(locale)
    }

    /** "8 dic", in minuscolo come [dateRange]. */
    fun dayMonth(date: LocalDate): String = date.format(dayMonthFormatter).lowercase(locale)

    /** "mer 10 dic", per i giorni di un itinerario. */
    fun weekdayDayMonth(date: LocalDate): String = date.format(weekdayDayMonthFormatter).lowercase(locale).replace(".", "")

    /** "1 gen" per un giorno che si ripete ogni anno. */
    fun dayMonth(day: MonthDay): String = day.format(dayMonthFormatter).lowercase(locale)

    fun rating(value: Double): String = String.format(locale, "%.1f", value)

    fun count(value: Int): String = NumberFormat.getIntegerInstance(locale).format(value)

    fun temperature(celsius: Double): String = "${celsius.roundToInt()}°"

    fun priceLevel(level: PriceLevel): String = "€".repeat(level.level.coerceAtLeast(1))

    /** Numero con una cifra decimale al massimo, in formato italiano (es. 2,1). */
    fun compactNumber(value: Double): String =
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(value)

    /** Nome del mese da usare dentro una frase ("viaggio a dicembre"), sempre in minuscolo. */
    fun monthName(month: Month): String = month.getDisplayName(TextStyle.FULL, locale).lowercase(locale)

    /** "dicembre", oppure "gennaio 2027" se l'anno è diverso da [currentYear]. */
    fun monthYear(month: YearMonth, currentYear: Int): String =
        if (month.year == currentYear) monthName(month.month) else "${monthName(month.month)} ${month.year}"

    /** Distanza a piedi o in città: "350 m" sotto il chilometro, altrimenti "1,2 km". */
    fun distance(meters: Double): String {
        val rounded = (meters / 10).roundToInt() * 10
        return if (rounded < 1_000) "$rounded m" else "${compactNumber(meters / 1_000)} km"
    }

    /** Ora del giorno senza data: "15:59". */
    fun time(time: LocalTime): String = time.format(timeFormatter)

    /** Giorno della settimana abbreviato e numero: "gio 10". */
    fun weekdayDay(date: LocalDate): String = date.format(weekdayDayFormatter).lowercase(locale).replace(".", "")

    /** Durata in ore e minuti per i fusi orari: "1 h", "5 h 30 min". */
    fun hoursAndMinutes(minutes: Int): String = duration(Duration.ofMinutes(minutes.toLong()))

    /**
     * Importo in una valuta qualunque con le cifre decimali giuste per quella valuta ("2.443 CZK",
     * "17.836 ¥", "12,50 €"): il simbolo viene dal sistema.
     */
    fun currencyAmount(amount: BigDecimal, currencyCode: String): String {
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        val format = NumberFormat.getCurrencyInstance(locale).apply {
            if (currency != null) this.currency = currency
            val digits = if (amount.abs() >= BigDecimal(LARGE_AMOUNT)) 0 else (currency?.defaultFractionDigits ?: 2).coerceAtLeast(0)
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
        return format.format(amount)
    }

    /** Data breve con l'anno: "1 ott 2026". */
    fun shortDate(date: LocalDate): String = date.format(shortDateFormatter).lowercase(locale).replace(".", "")

    /** Importo senza segno, per le variazioni di prezzo ("12 €"). */
    fun moneyAmount(amount: BigDecimal, currencyCode: String): String = money(Money.of(amount.abs(), currencyCode))
}
