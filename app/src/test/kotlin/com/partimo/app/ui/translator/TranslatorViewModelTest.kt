package com.partimo.app.ui.translator

import com.partimo.app.testing.MainDispatcherRule
import com.partimo.app.testing.successData
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.dashboard.SampleDestinations
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.guide.CountryInfo
import com.partimo.domain.model.ocr.DocumentSource
import com.partimo.domain.model.ocr.RecognizedBlock
import com.partimo.domain.model.ocr.RecognizedText
import com.partimo.domain.model.ocr.TextScript
import com.partimo.domain.testing.FakeCountryInfoRepository
import com.partimo.domain.testing.FakeTextRecognitionRepository
import com.partimo.domain.testing.FakeTranslatorRepository
import com.partimo.domain.usecase.GetCountryInfoUseCase
import com.partimo.domain.usecase.LanguagePacksUseCase
import com.partimo.domain.usecase.TranslatePhotoUseCase
import com.partimo.domain.usecase.TranslateTextUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TranslatorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val austria = CountryInfo("AT", "AUT", "Austria", "EUR", "euro", "€", listOf("tedesco"), listOf("de"))
    private val countries = FakeCountryInfoRepository(DataResult.Success(austria))

    private val ocr = FakeTextRecognitionRepository(
        DataResult.Success(
            RecognizedText(
                text = "Speisekarte\nApfelstrudel 6,90",
                blocks = listOf(RecognizedBlock("Speisekarte", 300, 40, 700, 120), RecognizedBlock("Apfelstrudel 6,90", 60, 200, 900, 260)),
                width = 1000,
                height = 800,
            ),
        ),
    )

    private fun createViewModel(translator: FakeTranslatorRepository, photos: Boolean = false) = TranslatorViewModel(
        getCountryInfo = GetCountryInfoUseCase(countries),
        translateText = TranslateTextUseCase(translator),
        languagePacks = LanguagePacksUseCase(translator),
        destination = SampleDestinations.VIENNA,
        userLanguage = "it",
        translatePhoto = if (photos) TranslatePhotoUseCase(ocr, TranslateTextUseCase(translator)) else null,
    )

    @Test
    fun `la foto di un menù si legge e si traduce dal tedesco, e cambiando lingua si chiude`() = runTest {
        val viewModel = createViewModel(FakeTranslatorRepository(downloaded = setOf("en", "it", "de")), photos = true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canTranslatePhoto)

        viewModel.onPhotoSelected("content://com.partimo.app.files/camera/photo-1.jpg")
        assertEquals(UiState.Loading, viewModel.uiState.value.photo?.translation)
        assertFalse(viewModel.uiState.value.canTranslatePhoto, "Una foto alla volta")
        advanceUntilIdle()

        val translation = viewModel.uiState.value.photo?.translation?.successData()
        assertEquals(listOf("[it] Speisekarte", "[it] Apfelstrudel 6,90"), translation?.blocks?.map { it.translation })
        assertEquals(DocumentSource("content://com.partimo.app.files/camera/photo-1.jpg", "image/jpeg") to TextScript.LATIN, ocr.requests.single())
        viewModel.onPhotoOriginalToggled()
        assertTrue(viewModel.uiState.value.photo?.showOriginal == true)

        viewModel.onForeignLanguageSelected("fr")
        assertNull(viewModel.uiState.value.photo)
        viewModel.onPhotoSelected("content://media/2")
        viewModel.onPhotoClosed()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.photo)
    }

    @Test
    fun `senza pacchetti o con una scrittura illeggibile le foto non si traducono`() = runTest {
        val viewModel = createViewModel(FakeTranslatorRepository(supportedLanguages = setOf("en", "it", "de", "ru"), downloaded = setOf("en")), photos = true)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canTranslatePhoto, "Prima i pacchetti lingua")
        viewModel.onForeignLanguageSelected("ru")
        assertFalse(viewModel.uiState.value.photoScriptSupported)
        assertFalse(createViewModel(FakeTranslatorRepository()).uiState.value.photoAvailable, "Senza riconoscimento del testo niente foto")
    }

    @Test
    fun `a Vienna propone il tedesco e chiede i pacchetti mancanti, poi traduce offline`() = runTest {
        val translator = FakeTranslatorRepository(downloaded = setOf("en"))
        val viewModel = createViewModel(translator)
        advanceUntilIdle()

        assertEquals("de", viewModel.uiState.value.foreignLanguage)
        assertEquals(listOf("de"), viewModel.uiState.value.countryLanguages)
        assertEquals(LanguagePackState.Missing(setOf("it", "de")), viewModel.uiState.value.packs)
        viewModel.onInputChanged("Il conto, per favore")
        assertFalse(viewModel.uiState.value.canTranslate, "Senza pacchetti non si traduce")

        viewModel.onDownloadPacks()
        assertEquals(LanguagePackState.Downloading, viewModel.uiState.value.packs)
        advanceUntilIdle()
        assertEquals(LanguagePackState.Ready, viewModel.uiState.value.packs)
        assertEquals(listOf("it" to "de"), translator.downloads)

        viewModel.onTranslate()
        advanceUntilIdle()
        assertEquals(TranslatedText("Il conto, per favore", "[de] Il conto, per favore", "de"), viewModel.uiState.value.result?.successData())
    }

    @Test
    fun `invertire le lingue traduce dal posto verso l'italiano partendo dalla traduzione`() = runTest {
        val viewModel = createViewModel(FakeTranslatorRepository(downloaded = setOf("en", "it", "de")))
        advanceUntilIdle()
        viewModel.onInputChanged("Grazie")
        viewModel.onTranslate()
        advanceUntilIdle()

        viewModel.onSwap()
        val swapped = viewModel.uiState.value
        assertTrue(swapped.reversed)
        assertEquals("de", swapped.from)
        assertEquals("it", swapped.to)
        assertEquals("[de] Grazie", swapped.input)
        assertNull(swapped.result)

        viewModel.onTranslate()
        advanceUntilIdle()
        assertEquals("it", viewModel.uiState.value.result?.successData()?.language)
    }

    @Test
    fun `il frasario si traduce una volta per lingua e cambia con la lingua scelta`() = runTest {
        val translator = FakeTranslatorRepository(downloaded = setOf("en", "it", "de", "fr"))
        val viewModel = createViewModel(translator)
        advanceUntilIdle()

        viewModel.onTranslatePhrases(listOf("Grazie mille", "Per favore"))
        advanceUntilIdle()
        assertEquals("[de] Grazie mille", viewModel.uiState.value.phraseTranslation("Grazie mille"))
        viewModel.onTranslatePhrases(listOf("Grazie mille", "Per favore"))
        advanceUntilIdle()
        assertEquals(2, translator.translations.size, "Le frasi già tradotte non si ritraducono")

        viewModel.onForeignLanguageSelected("fr")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.phraseTranslation("Grazie mille"))
        viewModel.onTranslatePhrases(listOf("Grazie mille"))
        advanceUntilIdle()
        assertEquals("[fr] Grazie mille", viewModel.uiState.value.phraseTranslation("Grazie mille"))
    }

    @Test
    fun `un download non riuscito si può riprovare`() = runTest {
        val translator = FakeTranslatorRepository(downloaded = setOf("en"), downloadResult = DataResult.Failure(DataError.NoConnection))
        val viewModel = createViewModel(translator)
        advanceUntilIdle()

        viewModel.onDownloadPacks()
        advanceUntilIdle()
        assertEquals(LanguagePackState.Failed(DataError.NoConnection), viewModel.uiState.value.packs)

        translator.downloadResult = DataResult.Success(Unit)
        viewModel.onDownloadPacks()
        advanceUntilIdle()
        assertEquals(LanguagePackState.Ready, viewModel.uiState.value.packs)
    }

    @Test
    fun `una traduzione non riuscita mostra l'errore`() = runTest {
        val translator = FakeTranslatorRepository(
            downloaded = setOf("en", "it", "de"),
            translateResult = { _, _, _ -> DataResult.Failure(DataError.Unknown("rotto")) },
        )
        val viewModel = createViewModel(translator)
        advanceUntilIdle()
        viewModel.onInputChanged("Ciao")
        viewModel.onTranslate()
        advanceUntilIdle()

        assertEquals(UiState.Error(DataError.Unknown("rotto")), viewModel.uiState.value.result)
        viewModel.onClear()
        assertEquals("", viewModel.uiState.value.input)
        assertNull(viewModel.uiState.value.result)
    }
}
