package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.testing.FakeTranslatorRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslatorUseCasesTest {

    private val supported = setOf("en", "it", "de", "fr", "nl", "no", "he")

    @Test
    fun `la lingua proposta è quella del paese, altrimenti l'inglese`() {
        assertEquals("de", TranslatorLanguages.defaultForeignLanguage(listOf("de"), "it", supported))
        // In Belgio la prima lingua conosciuta dal traduttore: l'olandese.
        assertEquals("nl", TranslatorLanguages.defaultForeignLanguage(listOf("nl", "fr", "de"), "it", supported))
        // In Svizzera si salta l'italiano, che l'utente parla già.
        assertEquals("de", TranslatorLanguages.defaultForeignLanguage(listOf("it", "de", "fr"), "it", supported))
        // Viaggio in Italia o lingua sconosciuta al traduttore: l'inglese.
        assertEquals("en", TranslatorLanguages.defaultForeignLanguage(listOf("it"), "it", supported))
        assertEquals("en", TranslatorLanguages.defaultForeignLanguage(listOf("mn"), "it", supported))
        // Chi usa l'app in inglese riceve la lingua del paese.
        assertEquals("de", TranslatorLanguages.defaultForeignLanguage(listOf("de"), "en", supported))
    }

    @Test
    fun `i codici delle lingue diventano quelli del traduttore`() {
        assertEquals("no", TranslatorLanguages.normalize("nb"))
        assertEquals("he", TranslatorLanguages.normalize("iw"))
        assertEquals("pt", TranslatorLanguages.normalize("pt-BR"))
        assertEquals("no", TranslatorLanguages.defaultForeignLanguage(listOf("nb"), "it", supported))
    }

    @Test
    fun `traduce solo quando serve`() = runTest {
        val repository = FakeTranslatorRepository(downloaded = setOf("en", "it", "de"))
        val translate = TranslateTextUseCase(repository)

        assertEquals(DataResult.Success("[de] Il conto, per favore", DataOrigin.LOCAL), translate("  Il conto, per favore ", "it", "de"))
        assertEquals("", (translate("   ", "it", "de") as DataResult.Success).data)
        assertEquals("Ciao", (translate("Ciao", "it", "it") as DataResult.Success).data)
        assertEquals(listOf(Triple("Il conto, per favore", "it", "de")), repository.translations)

        val tooLong = translate("a".repeat(TranslateTextUseCase.MAX_LENGTH + 1), "it", "de")
        assertEquals(DataResult.Failure(DataError.InvalidQuery(QueryIssue.TEXT_TOO_LONG)), tooLong)
    }

    @Test
    fun `i pacchetti mancanti si scaricano una volta`() = runTest {
        val repository = FakeTranslatorRepository(downloaded = setOf("en"))
        val packs = LanguagePacksUseCase(repository)

        assertEquals(setOf("it", "de"), (packs.missing("it", "de") as DataResult.Success).data)
        assertEquals(emptySet(), (packs.missing("en", "en") as DataResult.Success).data, "L'inglese c'è sempre")

        assertTrue(packs.download("it", "de") is DataResult.Success)
        assertEquals(listOf("it" to "de"), repository.downloads)
        assertEquals(emptySet(), (packs.missing("de", "it") as DataResult.Success).data)
    }
}
