package com.partimo.data.remote.gemini

import com.partimo.data.config.AndroidAppIdentity
import com.partimo.data.network.ApiKeyRejectedException
import com.partimo.data.network.NetworkJson
import com.partimo.data.network.UnusableResponseException
import com.partimo.data.network.androidAppHeaders
import com.partimo.data.network.firstSuccessful
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import java.net.SocketTimeoutException

/** Modello Gemini da provare, con il livello di ragionamento da chiedere (`null` = predefinito del modello). */
internal data class GeminiModel(val name: String, val thinkingLevel: String? = null) {
    companion object {
        /**
         * Prima i modelli "Flash-Lite", rapidi (pochi secondi) e con limiti gratuiti più ampi; poi il
         * modello "Flash", più accurato ma spesso sovraccarico. Gli alias "-latest" seguono le nuove
         * versioni; il modello con la versione esplicita ha una capacità separata.
         */
        val DEFAULTS = listOf(
            GeminiModel("gemini-flash-lite-latest"),
            GeminiModel("gemini-3.1-flash-lite"),
            GeminiModel("gemini-flash-latest", thinkingLevel = "minimal"),
        )
    }
}

/** Risposta accettata: il valore ricavato dal testo, il testo stesso e il modello che l'ha prodotta. */
internal data class GeminiOutput<T>(val value: T, val text: String, val model: String)

/**
 * Client di Google Gemini (generateContent). La chiave viaggia nell'intestazione `x-goog-api-key`,
 * mai nell'URL, e con package e certificato dell'app se è limitata alle app Android.
 *
 * Il livello gratuito ha limiti per modello e nei momenti di traffico risponde "503 high demand":
 * per questo prova [models] uno dopo l'altro (vedi [firstSuccessful]). Niente tentativi automatici
 * dello stesso modello: si passa subito al successivo.
 */
internal class GeminiApi(
    private val client: HttpClient,
    private val apiKey: String,
    private val androidApp: AndroidAppIdentity? = null,
    private val models: List<GeminiModel> = GeminiModel.DEFAULTS,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    /**
     * Genera una risposta con il primo modello disponibile. [accept] ricava il valore dal testo e
     * lancia un'eccezione se il testo non è utilizzabile (es. JSON troncato): si prova il modello successivo.
     */
    suspend fun <T> generate(
        request: GeminiRequest,
        hedgeAfterMillis: Long = DEFAULT_HEDGE_AFTER_MILLIS,
        accept: (String) -> T,
    ): GeminiOutput<T> = firstSuccessful(
        attempts = models.map { model -> suspend { generateWith(model, request, accept) } },
        hedgeAfterMillis = hedgeAfterMillis,
        shouldTryNext = ::worthAnotherModel,
    )

    private suspend fun <T> generateWith(model: GeminiModel, request: GeminiRequest, accept: (String) -> T): GeminiOutput<T> {
        val body = try {
            client.post("${baseUrl}models/${model.name}:generateContent") {
                header(API_KEY_HEADER, apiKey)
                androidAppHeaders(androidApp)
                contentType(ContentType.Application.Json)
                setBody(request.forModel(model))
                timeout {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                    socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                }
                retry { noRetry() }
            }.bodyAsText()
        } catch (e: ClientRequestException) {
            // Gemini segnala la chiave non valida con un 400 generico: va distinta da una richiesta errata.
            if (e.response.status == HttpStatusCode.BadRequest && API_KEY_INVALID in e.response.bodyAsText()) {
                throw ApiKeyRejectedException("Chiave Gemini rifiutata")
            }
            throw e
        }
        val response = NetworkJson.decodeFromString(GeminiResponse.serializer(), body)
        val text = response.text() ?: throw UnusableResponseException("Gemini (${model.name}): ${response.missingTextReason()}")
        return GeminiOutput(value = accept(text), text = text, model = model.name)
    }

    /** Il livello di ragionamento dipende dal modello: la richiesta viene adattata a ciascuno. */
    private fun GeminiRequest.forModel(model: GeminiModel): GeminiRequest {
        val thinking = model.thinkingLevel?.let(::GeminiThinkingConfig)
        return copy(generationConfig = (generationConfig ?: GeminiGenerationConfig()).copy(thinkingConfig = thinking))
    }

    /**
     * Un altro modello può riuscire dove questo ha fallito: sovraccarico, limiti, modello ritirato,
     * timeout, risposta troncata o non valida. Non serve invece se la chiave è rifiutata o manca la rete.
     */
    private fun worthAnotherModel(error: Throwable): Boolean = when (error) {
        is ApiKeyRejectedException -> false
        is ResponseException -> error.response.status.value !in NO_RETRY_STATUSES
        is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> true
        is IOException -> false
        else -> true
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/"
        const val API_KEY_HEADER = "x-goog-api-key"

        /** Oltre questo tempo senza risposta parte in parallelo anche il modello successivo. */
        const val DEFAULT_HEDGE_AFTER_MILLIS = 12_000L

        /** Un itinerario lungo può richiedere decine di secondi ai modelli più lenti. */
        private const val REQUEST_TIMEOUT_MILLIS = 90_000L
        private const val API_KEY_INVALID = "API_KEY_INVALID"

        /** Chiave senza permessi (403) o non autorizzata (401): uguale per tutti i modelli. */
        private val NO_RETRY_STATUSES = setOf(401, 403)
    }
}
