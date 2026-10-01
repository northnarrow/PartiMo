package com.partimo.domain.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExcerptTest {

    private val first = "Il Colosseo è il più grande anfiteatro romano del mondo, nel centro di Roma."
    private val second = "Fu inaugurato da Tito nell'80 d.C. con cento giorni di giochi."

    @Test
    fun `tiene i paragrafi che entrano per intero nel limite`() {
        val excerpt = Excerpt.of(listOf(first, second, "Oggi è uno dei monumenti più visitati al mondo."), maxChars = 150)

        assertEquals("$first\n\n$second", excerpt)
    }

    @Test
    fun `rispetta il numero massimo di paragrafi`() {
        assertEquals(first, Excerpt.of(listOf(first, second), maxChars = 1_000, maxParagraphs = 1))
    }

    @Test
    fun `un paragrafo troppo lungo si taglia alla fine di una frase`() {
        val text = "$first $second Nel Medioevo divenne una fortezza della famiglia Frangipane."

        val excerpt = Excerpt.of(listOf(text), maxChars = 150)

        assertEquals("$first $second", excerpt)
    }

    @Test
    fun `le abbreviazioni e le iniziali non chiudono la frase`() {
        val restoration = "Fu restaurato da G. Valadier nell'Ottocento con grande cura"
        val inauguration = "Fu inaugurato da Tito nell'80 d.C. con cento giorni di giochi"

        assertEquals("Fu restaurato da G. Valadier nell'Ottocento…", Excerpt.shorten(restoration, maxChars = 45))
        assertEquals("Fu inaugurato da Tito nell'80 d.C. con cento…", Excerpt.shorten(inauguration, maxChars = 50))
        assertEquals("$restoration.", Excerpt.shorten("$restoration. Poi arrivarono gli scavi del Novecento.", maxChars = 80))
    }

    @Test
    fun `senza una frase completa taglia all'ultima parola con i puntini`() {
        val text = "Un'unica lunghissima frase senza punti che descrive la costruzione dell'anfiteatro e dei suoi archi"

        val shortened = Excerpt.shorten(text, maxChars = 40)

        assertEquals("Un'unica lunghissima frase senza punti…", shortened)
        assertTrue(shortened.length <= 40)
    }

    @Test
    fun `scarta frammenti, elenchi introdotti dai due punti e spazi superflui`() {
        val paragraphs = listOf(
            "Arena",
            "Il testo dell'iscrizione è stato ricostruito nel modo seguente:",
            "L'anfiteatro  sorge nella valle ( ) tra il Celio , l'Esquilino e il Palatino.",
        )

        assertEquals("L'anfiteatro sorge nella valle tra il Celio, l'Esquilino e il Palatino.", Excerpt.of(paragraphs, maxChars = 200))
        assertFalse(Excerpt.hasReadableText(paragraphs.take(2)))
    }

    @Test
    fun `senza testo leggibile non c'è estratto`() {
        assertNull(Excerpt.of(emptyList(), maxChars = 100))
        assertNull(Excerpt.of(listOf("  ", "Note"), maxChars = 100))
    }
}
