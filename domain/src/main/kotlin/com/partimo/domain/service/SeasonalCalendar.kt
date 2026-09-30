package com.partimo.domain.service

import com.partimo.domain.model.Hemisphere
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.Season
import com.partimo.domain.model.poi.SeasonalTheme
import java.time.Month

/**
 * Conoscenza di dominio sulla stagionalità: temi di ricerca per mese e periodi di attività
 * tipici (mercatini di Natale, spiagge, piste da sci) dedotti da categoria o parole chiave.
 */
object SeasonalCalendar {

    /** Il Natale segue il calendario, non la stagione: è a dicembre anche nell'emisfero sud. */
    val CHRISTMAS_MONTHS: Set<Month> = setOf(Month.NOVEMBER, Month.DECEMBER, Month.JANUARY)

    val CHRISTMAS_KEYWORDS = listOf("natale", "natalizi", "christmas", "weihnacht", "christkindl", "advent", "noël", "navidad")
    val SKI_KEYWORDS = listOf("ski", "sci ", "piste da sci", "impianti sciistici")
    val BEACH_KEYWORDS = listOf("spiaggia", "spiagge", "beach", "lido", "strand", "playa", "plage")

    private const val MAX_THEMES = 2

    fun beachMonths(hemisphere: Hemisphere): Set<Month> = when (hemisphere) {
        Hemisphere.NORTHERN -> monthRange(Month.MAY, Month.SEPTEMBER)
        Hemisphere.SOUTHERN -> monthRange(Month.NOVEMBER, Month.MARCH)
    }

    fun skiMonths(hemisphere: Hemisphere): Set<Month> = when (hemisphere) {
        Hemisphere.NORTHERN -> monthRange(Month.DECEMBER, Month.MARCH)
        Hemisphere.SOUTHERN -> monthRange(Month.JUNE, Month.SEPTEMBER)
    }

    /** Temi di ricerca stagionali per il mese del viaggio (massimo due, per contenere le chiamate API). */
    fun themesFor(month: Month, hemisphere: Hemisphere): List<SeasonalTheme> {
        val themes = mutableListOf<SeasonalTheme>()
        if (month in CHRISTMAS_MONTHS) {
            themes += SeasonalTheme("mercatini di Natale", PoiCategory.SEASONAL_EVENT, CHRISTMAS_MONTHS)
        }
        themes += when (Season.of(month, hemisphere)) {
            Season.WINTER -> SeasonalTheme("piste di pattinaggio sul ghiaccio", PoiCategory.SEASONAL_EVENT, Season.WINTER.months(hemisphere))
            Season.SPRING -> SeasonalTheme("giardini fioriti", PoiCategory.PARK, Season.SPRING.months(hemisphere))
            Season.SUMMER -> SeasonalTheme("spiagge e lidi", PoiCategory.BEACH, beachMonths(hemisphere))
            Season.AUTUMN -> SeasonalTheme("parchi con foliage autunnale", PoiCategory.PARK, Season.AUTUMN.months(hemisphere))
        }
        return themes.take(MAX_THEMES)
    }

    /** Intervallo di mesi circolare (es. dicembre–marzo attraversa il cambio d'anno). */
    fun monthRange(from: Month, to: Month): Set<Month> {
        val months = linkedSetOf<Month>()
        var current = from
        while (true) {
            months += current
            if (current == to) break
            current = current.plus(1)
        }
        return months
    }
}
