package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.common.DataResult
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.ocr.DocumentSource
import com.partimo.domain.model.ocr.RecognizedBlock
import com.partimo.domain.model.ocr.RecognizedText
import com.partimo.domain.model.ocr.TextScript
import com.partimo.domain.testing.FakeTextRecognitionRepository
import com.partimo.domain.testing.FakeTranslatorRepository
import com.partimo.domain.testing.failureError
import com.partimo.domain.testing.successData
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

    @Test
    fun `una foto di un menù si traduce riga per riga e un cartello come frase, dall'alto in basso`() = runTest {
        val translator = FakeTranslatorRepository(downloaded = setOf("en", "de", "it"))
        val ocr = FakeTextRecognitionRepository(
            DataResult.Success(
                RecognizedText(
                    text = "Speisekarte ...",
                    blocks = listOf(
                        RecognizedBlock("Wiener Schnitzel 18,50\nApfelstrudel 6,90\nKaffee 3,50 €", 40, 300, 900, 520),
                        RecognizedBlock("Bitte nicht\nrauchen", 60, 1200, 700, 1300),
                        RecognizedBlock("Kartoffel-\nsalat", 40, 600, 600, 700),
                        RecognizedBlock("Speisekarte", 200, 80, 800, 160),
                    ),
                    width = 1000,
                    height = 1400,
                ),
            ),
        )
        val photo = DocumentSource("content://com.partimo.app.files/camera/menu.jpg", "image/jpeg")

        val translation = TranslatePhotoUseCase(ocr, TranslateTextUseCase(translator))(photo, "de", "it").successData()

        assertEquals(
            listOf(
                "[it] Speisekarte",
                "[it] Wiener Schnitzel 18,50\n[it] Apfelstrudel 6,90\n[it] Kaffee 3,50 €",
                "[it] Kartoffelsalat",
                "[it] Bitte nicht rauchen",
            ),
            translation.blocks.map { it.translation },
        )
        assertEquals(1000 to 1400, translation.width to translation.height)
        assertTrue(translation.hasLayout)
        assertEquals(photo to TextScript.LATIN, ocr.requests.single())
    }

    @Test
    fun `il giapponese usa il suo modello, il russo non si legge e una foto senza testo è un errore`() = runTest {
        val ocr = FakeTextRecognitionRepository(DataResult.Success(RecognizedText("ラーメン", listOf(RecognizedBlock("ラーメン", 0, 0, 100, 40)), 100, 40)))
        val translate = TranslatePhotoUseCase(ocr, TranslateTextUseCase(FakeTranslatorRepository(supportedLanguages = setOf("en", "ja", "it"), downloaded = setOf("en", "ja", "it"))))
        val photo = DocumentSource("content://media/1", "image/jpeg")

        assertEquals("[it] ラーメン", translate(photo, "ja", "it").successData().blocks.single().translation)
        assertEquals(TextScript.JAPANESE, ocr.requests.single().second)
        assertEquals(DataError.InvalidQuery(QueryIssue.UNSUPPORTED_SCRIPT), translate(photo, "ru", "it").failureError())
        assertEquals(1, ocr.requests.size, "Per il russo la foto non si legge nemmeno")

        ocr.result = DataResult.Success(RecognizedText(" "))
        assertEquals(DataError.InvalidResponse, translate(photo, "ja", "it").failureError())
        ocr.result = DataResult.Failure(DataError.NoConnection)
        assertEquals(DataError.NoConnection, translate(photo, "ja", "it").failureError(), "Modello ancora da scaricare")
        assertEquals(TextScript.DEVANAGARI, TextScript.of("hi"))
        assertEquals(TextScript.CHINESE, TextScript.of("zh-TW"))
        assertEquals(TextScript.LATIN, TextScript.of("cs"))
    }
}
