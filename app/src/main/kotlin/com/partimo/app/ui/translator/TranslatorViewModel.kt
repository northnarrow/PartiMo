package com.partimo.app.ui.translator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.app.ui.common.UiState
import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.Destination
import com.partimo.domain.usecase.GetCountryInfoUseCase
import com.partimo.domain.usecase.LanguagePacksUseCase
import com.partimo.domain.usecase.TranslateTextUseCase
import com.partimo.domain.usecase.TranslatorLanguages
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pacchetti lingua della coppia scelta. */
sealed interface LanguagePackState {
    data object Checking : LanguagePackState

    data class Missing(val languages: Set<String>) : LanguagePackState

    data object Downloading : LanguagePackState

    data object Ready : LanguagePackState

    data class Failed(val error: DataError) : LanguagePackState
}

/** Testo tradotto, con la lingua della traduzione per la pronuncia. */
data class TranslatedText(val source: String, val translation: String, val language: String)

data class TranslatorUiState(
    val destination: Destination,
    /** Lingua dell'app e del frasario. */
    val userLanguage: String,
    /** Lingua del posto; `null` finché non si conoscono le lingue del paese. */
    val foreignLanguage: String? = null,
    /** Lingue del paese che il traduttore conosce, in cima alla scelta. */
    val countryLanguages: List<String> = emptyList(),
    val supportedLanguages: List<String> = emptyList(),
    /** `true` per tradurre dalla lingua del posto (un cartello, un menù) verso quella dell'utente. */
    val reversed: Boolean = false,
    val packs: LanguagePackState = LanguagePackState.Checking,
    val input: String = "",
    /** `null` finché non si traduce nulla. */
    val result: UiState<TranslatedText>? = null,
    /** Traduzioni del frasario, per lingua e frase (vedi [phraseKey]). */
    val phraseTranslations: Map<String, String> = emptyMap(),
) {
    val from: String? get() = if (reversed) foreignLanguage else userLanguage
    val to: String? get() = if (reversed) userLanguage else foreignLanguage

    val canTranslate: Boolean get() = packs == LanguagePackState.Ready && input.isNotBlank() && result != UiState.Loading

    fun phraseTranslation(phrase: String): String? = foreignLanguage?.let { phraseTranslations[phraseKey(it, phrase)] }

    companion object {
        fun phraseKey(language: String, phrase: String): String = "$language|$phrase"
    }
}

/**
 * ViewModel del traduttore: lingua del posto proposta dal paese, pacchetti lingua da scaricare una
 * volta, traduzione del testo scritto e del frasario, tutto sul telefono.
 */
class TranslatorViewModel(
    private val getCountryInfo: GetCountryInfoUseCase,
    private val translateText: TranslateTextUseCase,
    private val languagePacks: LanguagePacksUseCase,
    destination: Destination,
    userLanguage: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TranslatorUiState(
            destination = destination,
            userLanguage = TranslatorLanguages.normalize(userLanguage),
            supportedLanguages = translateText.supportedLanguages.sorted(),
        ),
    )
    val uiState: StateFlow<TranslatorUiState> = _uiState.asStateFlow()

    private var packsJob: Job? = null
    private var phrasesJob: Job? = null

    init {
        viewModelScope.launch {
            val state = _uiState.value
            val supported = translateText.supportedLanguages
            val codes = (getCountryInfo(state.destination.countryCode) as? DataResult.Success)?.data?.languageCodes.orEmpty()
            val countryLanguages = codes.map(TranslatorLanguages::normalize).filter { it in supported && it != state.userLanguage }.distinct()
            val foreign = TranslatorLanguages.defaultForeignLanguage(codes, state.userLanguage, supported)
            _uiState.update { it.copy(foreignLanguage = foreign, countryLanguages = countryLanguages) }
            checkPacks()
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(input = text.take(TranslateTextUseCase.MAX_LENGTH)) }
    }

    fun onClear() {
        _uiState.update { it.copy(input = "", result = null) }
    }

    /** Inverte le lingue: il testo tradotto diventa quello da tradurre, come nei traduttori più noti. */
    fun onSwap() {
        _uiState.update { state ->
            val translated = (state.result as? UiState.Success)?.data?.translation
            state.copy(reversed = !state.reversed, input = translated ?: state.input, result = null)
        }
    }

    fun onForeignLanguageSelected(language: String) {
        if (language == _uiState.value.foreignLanguage || language == _uiState.value.userLanguage) return
        _uiState.update { it.copy(foreignLanguage = language, result = null) }
        checkPacks()
    }

    fun onDownloadPacks() {
        val state = _uiState.value
        val foreign = state.foreignLanguage ?: return
        packsJob?.cancel()
        _uiState.update { it.copy(packs = LanguagePackState.Downloading) }
        packsJob = viewModelScope.launch {
            when (val download = languagePacks.download(state.userLanguage, foreign)) {
                is DataResult.Failure -> _uiState.update { it.copy(packs = LanguagePackState.Failed(download.error)) }
                is DataResult.Success -> _uiState.update { it.copy(packs = packStateOf(languagePacks.missing(state.userLanguage, foreign))) }
            }
        }
    }

    fun onTranslate() {
        val state = _uiState.value
        val from = state.from ?: return
        val to = state.to ?: return
        if (!state.canTranslate) return
        val text = state.input.trim()
        _uiState.update { it.copy(result = UiState.Loading) }
        viewModelScope.launch {
            val result = when (val translation = translateText(text, from, to)) {
                is DataResult.Success -> UiState.Success(TranslatedText(text, translation.data, to), translation.origin)
                is DataResult.Failure -> UiState.Error(translation.error)
            }
            _uiState.update { it.copy(result = result) }
        }
    }

    /** Traduce le frasi di una categoria del frasario aperta (una volta per lingua). */
    fun onTranslatePhrases(phrases: List<String>) {
        val state = _uiState.value
        val foreign = state.foreignLanguage ?: return
        if (state.packs != LanguagePackState.Ready) return
        val missing = phrases.filter { state.phraseTranslations[TranslatorUiState.phraseKey(foreign, it)] == null }
        if (missing.isEmpty()) return
        phrasesJob?.cancel()
        phrasesJob = viewModelScope.launch {
            missing.forEach { phrase ->
                val translation = translateText(phrase, state.userLanguage, foreign)
                if (translation is DataResult.Success) {
                    _uiState.update { it.copy(phraseTranslations = it.phraseTranslations + (TranslatorUiState.phraseKey(foreign, phrase) to translation.data)) }
                }
            }
        }
    }

    private fun checkPacks() {
        val state = _uiState.value
        val foreign = state.foreignLanguage ?: return
        packsJob?.cancel()
        _uiState.update { it.copy(packs = LanguagePackState.Checking) }
        packsJob = viewModelScope.launch {
            val packs = packStateOf(languagePacks.missing(state.userLanguage, foreign))
            _uiState.update { it.copy(packs = packs) }
        }
    }

    private fun packStateOf(missing: DataResult<Set<String>>): LanguagePackState = when (missing) {
        is DataResult.Failure -> LanguagePackState.Failed(missing.error)
        is DataResult.Success -> if (missing.data.isEmpty()) LanguagePackState.Ready else LanguagePackState.Missing(missing.data)
    }

    companion object {
        fun factory(container: AppContainer, destination: Destination, userLanguage: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TranslatorViewModel(
                    getCountryInfo = container.getCountryInfo,
                    translateText = container.translateText,
                    languagePacks = container.languagePacks,
                    destination = destination,
                    userLanguage = userLanguage,
                )
            }
        }
    }
}
