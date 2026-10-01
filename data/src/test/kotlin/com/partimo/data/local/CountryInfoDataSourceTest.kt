package com.partimo.data.local

import com.partimo.domain.model.guide.DrivingSide
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountryInfoDataSourceTest {

    private val source = BundledCountryInfoDataSource("it")

    @Test
    fun `l'Austria ha nome, valuta e lingua in italiano e i dati pratici del catalogo`() = runTest {
        val austria = source.countryInfo("at").data

        assertEquals("Austria", austria.name)
        assertEquals("AUT", austria.countryCode3)
        assertEquals("EUR", austria.currencyCode)
        assertEquals("euro", austria.currencyName?.lowercase())
        assertEquals("€", austria.currencySymbol)
        assertEquals(listOf("tedesco"), austria.languages)
        assertEquals("+43", austria.callingCode)
        assertEquals(DrivingSide.RIGHT, austria.drivingSide)
        assertTrue(austria.power!!.fitsItalianPlugs)
        assertEquals("112", austria.emergency?.general)
        assertTrue(austria.usesEuro)
    }

    @Test
    fun `in Giappone e nel Regno Unito servono adattatore e attenzione alla guida`() = runTest {
        val japan = source.countryInfo("JP").data
        assertEquals("Giappone", japan.name)
        assertEquals("JPY", japan.currencyCode)
        assertEquals(DrivingSide.LEFT, japan.drivingSide)
        assertFalse(japan.power!!.fitsItalianPlugs)
        assertTrue(japan.power!!.lowVoltage)
        assertEquals("110", japan.emergency?.police)

        val uk = source.countryInfo("GB").data
        assertEquals("GBP", uk.currencyCode)
        assertEquals(listOf("G"), uk.power?.plugTypes)
        assertEquals("999", uk.emergency?.general)
    }

    @Test
    fun `fuori dal catalogo restano i dati del sistema e il rimando a Viaggiare Sicuri`() = runTest {
        val bhutan = source.countryInfo("BT").data

        assertEquals("BTN", bhutan.countryCode3)
        assertEquals("BTN", bhutan.currencyCode)
        assertNull(bhutan.power)
        assertNull(bhutan.emergency)
        assertNull(bhutan.callingCode)
    }

    @Test
    fun `il catalogo copre tutte le mete di Consigliami e i codici non validi sono rifiutati`() = runTest {
        val catalogCountries = setOf(
            "AE", "AR", "AT", "AU", "BR", "CA", "CU", "CZ", "DK", "EG", "ES", "FI", "FR", "GB", "GR", "HK", "HR", "HU", "ID",
            "IS", "IT", "JP", "KR", "MA", "MX", "NL", "NO", "NZ", "PE", "PL", "PT", "SG", "TH", "TR", "TZ", "US", "VN", "ZA",
        )
        assertTrue(CountryFactsCatalog.countryCodes.containsAll(catalogCountries), (catalogCountries - CountryFactsCatalog.countryCodes).toString())
        CountryFactsCatalog.countryCodes.forEach { code ->
            val facts = CountryFactsCatalog.factsFor(code)!!
            assertTrue(facts.callingCode.startsWith("+") && facts.power.plugTypes.isNotEmpty() && !facts.emergency.isEmpty && facts.languages.isNotEmpty(), code)
        }
        assertFailsWith<IllegalArgumentException> { source.countryInfo("Austria") }
    }
}
