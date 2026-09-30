package com.partimo.domain.model.poi

import com.partimo.domain.model.Hemisphere
import java.time.Month

/** Stagione meteorologica (inverno = dicembre–febbraio nell'emisfero nord). */
enum class Season {
    WINTER, SPRING, SUMMER, AUTUMN;

    /** Mesi della stagione nell'emisfero indicato: a sud le stagioni sono invertite. */
    fun months(hemisphere: Hemisphere = Hemisphere.NORTHERN): Set<Month> =
        if (hemisphere == Hemisphere.NORTHERN) northernMonths() else opposite().northernMonths()

    fun opposite(): Season = when (this) {
        WINTER -> SUMMER
        SPRING -> AUTUMN
        SUMMER -> WINTER
        AUTUMN -> SPRING
    }

    private fun northernMonths(): Set<Month> = when (this) {
        WINTER -> setOf(Month.DECEMBER, Month.JANUARY, Month.FEBRUARY)
        SPRING -> setOf(Month.MARCH, Month.APRIL, Month.MAY)
        SUMMER -> setOf(Month.JUNE, Month.JULY, Month.AUGUST)
        AUTUMN -> setOf(Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER)
    }

    companion object {
        fun of(month: Month, hemisphere: Hemisphere = Hemisphere.NORTHERN): Season {
            val northern = entries.first { month in it.northernMonths() }
            return if (hemisphere == Hemisphere.NORTHERN) northern else northern.opposite()
        }
    }
}
