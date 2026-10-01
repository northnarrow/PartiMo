package com.partimo.app.ui.common

import com.partimo.domain.service.OpeningHoursParser
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class OpeningHoursTextTest {

    private val week = LocalDate.of(2026, 12, 10)

    private fun weekly(text: String): String = OpeningHoursText.weekly(OpeningHoursParser.parse(text)!!, week, "chiuso")

    @Test
    fun `i giorni con lo stesso orario si raggruppano, prima quelli di apertura`() {
        assertEquals("lun–ven 10:00–23:00 · sab–dom 09:00–23:00", weekly("Mo-Fr 10:00-23:00; Sa, Su 09:00-23:00"))
        assertEquals("mar–dom 10:00–18:00 · lun chiuso", weekly("Tu-Su 10:00-18:00; Mo off"))
        assertEquals("lun–dom 11:00–24:00", weekly("Mo-Su,PH 11:00-24:00"))
        assertEquals(
            "lun–ven 11:30–14:30, 18:00–22:00 · sab 18:00–22:00 · dom chiuso",
            weekly("Mo-Fr 11:30-14:30, 18:00-22:00; Sa 18:00-22:00; Su, PH off"),
        )
        assertEquals("lun–dom 24 h", weekly("24/7"))
        assertEquals("ven–sab dalle 22:00 · lun–gio chiuso · dom chiuso", weekly("Fr-Sa 22:00+"))
    }
}
