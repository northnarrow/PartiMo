package com.partimo.data.remote.wikipedia

import com.partimo.domain.model.poi.ArticleSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WikipediaArticleParserTest {

    private val extract = """
        La chiesa sorge nel centro storico.
        È una delle più antiche della città.


        == Storia ==
        Fu fondata nel XII secolo.


        === Origini ===
        Le prime notizie risalgono al 1150.


        ==== Il primo edificio ====
        Era una piccola cappella.


        === Età moderna ===
        Nel Seicento fu ricostruita in stile barocco.


        == Descrizione ==
        La facciata è in travertino.


        === Interno ===
        Tre navate.


        == Note ==
    """.trimIndent()

    @Test
    fun `separa l'introduzione e la storia con le sue sottosezioni`() {
        val article = WikipediaArticleParser.parse(extract)

        assertEquals(listOf("La chiesa sorge nel centro storico.", "È una delle più antiche della città."), article.introduction)
        assertEquals(
            listOf(
                ArticleSection(null, listOf("Fu fondata nel XII secolo.")),
                ArticleSection("Origini", listOf("Le prime notizie risalgono al 1150.")),
                ArticleSection("Il primo edificio", listOf("Era una piccola cappella.")),
                ArticleSection("Età moderna", listOf("Nel Seicento fu ricostruita in stile barocco.")),
            ),
            article.history,
        )
    }

    @Test
    fun `riconosce i titoli storici in più lingue`() {
        val english = WikipediaArticleParser.parse("Intro.\n\n== Early history ==\nBuilt in 1200.\n\n== Architecture ==\nGothic.")
        val cenni = WikipediaArticleParser.parse("Intro.\n\n== Cenni storici ==\nCostruita nel 1200.")

        assertEquals(listOf("Built in 1200."), english.history.single().paragraphs)
        assertEquals(listOf("Costruita nel 1200."), cenni.history.single().paragraphs)
    }

    @Test
    fun `senza una sezione storica usa le origini, altrimenti nessuna storia`() {
        val origins = WikipediaArticleParser.parse("Intro.\n\n== Descrizione ==\nBella.\n\n== Origini del nome ==\nDal latino.")
        val none = WikipediaArticleParser.parse("Solo introduzione.\n\n== Descrizione ==\nBella.")

        assertEquals(listOf("Dal latino."), origins.history.single().paragraphs)
        assertTrue(none.history.isEmpty())
        assertEquals(listOf("Solo introduzione."), none.introduction)
    }
}
