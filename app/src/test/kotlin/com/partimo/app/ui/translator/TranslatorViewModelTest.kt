package com.partimo.app.ui.translator

import com.partimo.app.testing.MainDispatcherRule
import com.partimo.app.testing.successData
import com.partimo.app.ui.common.UiState
import com.partimo.app.ui.dashboard.SampleDestinations
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.guide.CountryInfo
import com.partimo.domain.testing.FakeCountryInfoRepository
import com.partimo.domain.testing.FakeTranslatorRepository
import com.partimo.domain.usecase.GetCountryInfoUseCase
import com.partimo.domain.usecase.LanguagePacksUseCase
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

    private fun createViewModel(translator: FakeTranslatorRepository) = TranslatorViewModel(
        getCountryInfo = GetCountryInfoUseCase(countries),
        translateText = TranslateTextUseCase(translator),
        languagePacks = LanguagePacksUseCase(translator),
        destination = SampleDestinations.VIENNA,
        userLanguage = "it",
    )

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
