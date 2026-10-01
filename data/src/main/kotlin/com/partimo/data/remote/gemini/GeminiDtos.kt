package com.partimo.data.remote.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Formato REST di generateContent (Gemini API v1beta), limitato ai campi usati dall'app.
// https://ai.google.dev/api/generate-content

@Serializable
internal data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

/** Turno della conversazione: "user" (utente) o "model" (assistente); senza ruolo per le istruzioni di sistema. */
@Serializable
internal data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart> = emptyList(),
) {
    companion object {
        fun user(text: String) = GeminiContent(role = "user", parts = listOf(GeminiPart(text)))
        fun model(text: String) = GeminiContent(role = "model", parts = listOf(GeminiPart(text)))
        fun system(text: String) = GeminiContent(parts = listOf(GeminiPart(text)))
    }
}

/** Parte di testo; [thought] è `true` per i ragionamenti interni del modello, da non mostrare. */
@Serializable
internal data class GeminiPart(
    val text: String? = null,
    val thought: Boolean? = null,
)

@Serializable
internal data class GeminiGenerationConfig(
    /** "application/json" con [responseSchema] per ottenere JSON valido secondo lo schema. */
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
    val temperature: Double? = null,
    /** Comprende anche i token di ragionamento: va lasciato ampio. */
    val maxOutputTokens: Int? = null,
    val thinkingConfig: GeminiThinkingConfig? = null,
)

/** Quanto "ragiona" il modello prima di rispondere: "minimal" è il più rapido. */
@Serializable
internal data class GeminiThinkingConfig(val thinkingLevel: String)

@Serializable
internal data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val promptFeedback: GeminiPromptFeedback? = null,
    val modelVersion: String? = null,
) {
    /** Testo della prima risposta senza i ragionamenti interni; `null` se il modello non ha risposto. */
    fun text(): String? = candidates.firstOrNull()?.content?.parts
        ?.filter { it.thought != true }
        ?.mapNotNull { it.text }
        ?.joinToString(separator = "")
        ?.takeIf { it.isNotBlank() }

    /** Perché manca la risposta (filtri di sicurezza, limite di token...), per i log e i messaggi d'errore. */
    fun missingTextReason(): String =
        promptFeedback?.blockReason ?: candidates.firstOrNull()?.finishReason ?: "nessuna risposta"
}

@Serializable
internal data class GeminiCandidate(
    val content: GeminiContent? = null,
    /** "STOP" se la risposta è completa; "MAX_TOKENS" se è stata troncata. */
    val finishReason: String? = null,
)

@Serializable
internal data class GeminiPromptFeedback(val blockReason: String? = null)
