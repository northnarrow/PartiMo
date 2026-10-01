package com.partimo.app.ui.translator

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Pronuncia di un testo in una lingua (codice ISO 639-1). */
interface Speaker {
    /** `false` finché la sintesi vocale del telefono non è pronta. */
    val isReady: Boolean

    /** `false` se il telefono non ha una voce per la lingua. */
    fun speak(text: String, language: String): Boolean
}

/**
 * Sintesi vocale del telefono (gratuita e offline con le voci installate), chiusa quando la schermata
 * esce dalla composizione.
 */
@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { TextToSpeechSpeaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}

private class TextToSpeechSpeaker(context: Context) : Speaker, TextToSpeech.OnInitListener {

    override var isReady by mutableStateOf(false)
        private set

    private val engine: TextToSpeech? = runCatching { TextToSpeech(context.applicationContext, this) }
        .onFailure { Log.w(TAG, "Sintesi vocale non disponibile", it) }
        .getOrNull()

    override fun onInit(status: Int) {
        isReady = status == TextToSpeech.SUCCESS
    }

    override fun speak(text: String, language: String): Boolean {
        val tts = engine?.takeIf { isReady } ?: return false
        val available = tts.setLanguage(Locale.forLanguageTag(language))
        if (available == TextToSpeech.LANG_MISSING_DATA || available == TextToSpeech.LANG_NOT_SUPPORTED) return false
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) == TextToSpeech.SUCCESS
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
    }

    private companion object {
        const val TAG = "Speaker"
        const val UTTERANCE_ID = "partimo-translator"
    }
}
