package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.poi.ArticleSection
import com.partimo.domain.model.poi.HistoryChapter
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.testing.FakePoiArticleRepository
import com.partimo.domain.testing.TestData
import com.partimo.domain.testing.TestData.poi
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetPoiDetailsUseCaseTest {

    private val cathedral = poi(
        "stephansdom",
        "Duomo di Santo Stefano",
        category = PoiCategory.RELIGIOUS_SITE,
        description = "Cattedrale gotica nel cuore di Vienna.",
        photoUrl = "https://places.test/stephansdom.jpg",
    )
    private val repository = FakePoiArticleRepository(DataResult.Success(TestData.article()))
    private val useCase = GetPoiDetailsUseCase(repository)

    @Test
    fun `la scheda riassume la voce con descrizione, capitoli della storia e fonti`() = runTest {
        val details = useCase(cathedral).successData()

        assertEquals(
            "Il duomo di Santo Stefano è la cattedrale di Vienna, capolavoro del gotico austriaco.\n\n" +
                "Con la sua torre sud di 136 metri domina il centro storico della città.",
            details.summary,
        )
        assertEquals(listOf("Origini", "Età moderna"), details.history.map { it.title })
        assertEquals("https://upload.test/stephansdom.jpg", details.imageUrl)
        assertEquals("Mario Rossi", details.imageCredit?.author)
        assertEquals("https://it.wikipedia.org/wiki/Duomo_di_Santo_Stefano", details.sourceUrl)
        assertEquals(cathedral, repository.requestedPois.single())
    }

    @Test
    fun `la descrizione resta breve anche per voci molto lunghe`() = runTest {
        val longIntro = List(6) { index -> "Paragrafo numero $index della lunga introduzione, ricco di dettagli sulla storia del luogo." }
        repository.result = DataResult.Success(TestData.article(introduction = longIntro))

        val summary = useCase(cathedral).successData().summary.orEmpty()

        assertEquals(2, summary.split("\n\n").size)
        assertTrue(summary.length <= GetPoiDetailsUseCase.Limits().summaryMaxChars)
    }

    @Test
    fun `ogni capitolo mostra il suo primo paragrafo, fino a cinque capitoli`() = runTest {
        val chapters = List(7) { index ->
            ArticleSection(
                title = "Epoca $index",
                paragraphs = listOf("Primo paragrafo dell'epoca $index, con i fatti principali.", "Dettagli dell'epoca $index da non mostrare."),
            )
        }
        repository.result = DataResult.Success(TestData.article(history = chapters))

        val history = useCase(cathedral).successData().history

        assertEquals(5, history.size)
        assertEquals(HistoryChapter("Epoca 0", "Primo paragrafo dell'epoca 0, con i fatti principali."), history.first())
    }

    @Test
    fun `una storia senza sottosezioni è un racconto unico con più paragrafi`() = runTest {
        val story = ArticleSection(
            title = null,
            paragraphs = listOf(
                "La chiesa fu costruita nel XII secolo sulle rovine di un tempio.",
                "Nel Seicento fu ampliata con due cappelle laterali in stile barocco.",
            ),
        )
        repository.result = DataResult.Success(TestData.article(history = listOf(story, ArticleSection("Note", emptyList()))))

        val history = useCase(cathedral).successData().history

        assertEquals(1, history.size)
        assertNull(history.single().title)
        assertEquals(story.paragraphs.joinToString("\n\n"), history.single().text)
    }

    @Test
    fun `senza voce enciclopedica restano descrizione e foto del provider`() = runTest {
        repository.result = DataResult.Success(null)

        val details = useCase(cathedral).successData()

        assertEquals(cathedral.description, details.summary)
        assertEquals(cathedral.photoUrl, details.imageUrl)
        assertTrue(details.history.isEmpty())
        assertNull(details.imageCredit)
        assertNull(details.sourceUrl)
    }

    @Test
    fun `se la voce non ha foto si usa quella del luogo senza attribuirle i crediti della voce`() = runTest {
        repository.result = DataResult.Success(TestData.article(imageUrl = null))

        val details = useCase(cathedral).successData()

        assertEquals(cathedral.photoUrl, details.imageUrl)
        assertNull(details.imageCredit)
    }

    @Test
    fun `gli errori del repository arrivano alla presentation`() = runTest {
        repository.result = DataResult.Failure(DataError.NoConnection)

        assertEquals(DataError.NoConnection, useCase(cathedral, forceRefresh = true).failureError())
        assertEquals(listOf(true), repository.forceRefreshFlags)
    }
}
