package com.partimo.data.network

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Configurazione JSON condivisa: ignora i campi sconosciuti per resistere all'evoluzione delle API. */
internal val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/** Crea l'[HttpClient] Ktor condiviso da tutti i provider. */
object HttpClientFactory {

    private const val LOG_TAG = "PartiMoHttp"
    private const val GOOGLE_API_KEY_HEADER = "X-Goog-Api-Key"
    private const val TRAVELPAYOUTS_TOKEN_HEADER = "X-Access-Token"

    /**
     * @param engine motore HTTP (OkHttp in produzione, MockEngine nei test)
     * @param enableLogging log delle richieste, da attivare solo nelle build di debug
     * @param maxRetries tentativi aggiuntivi su errori di rete o HTTP 5xx, con backoff esponenziale
     */
    fun create(
        engine: HttpClientEngine = OkHttp.create(),
        enableLogging: Boolean = false,
        maxRetries: Int = 2,
    ): HttpClient = HttpClient(engine) {
        // Le risposte non 2xx diventano eccezioni tipizzate, convertite poi in DataError.
        expectSuccess = true

        install(ContentNegotiation) { json(NetworkJson) }

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 25_000
            socketTimeoutMillis = 25_000
        }

        if (maxRetries > 0) {
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(maxRetries)
                exponentialDelay()
            }
        }

        if (enableLogging) {
            install(Logging) {
                level = LogLevel.INFO
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d(LOG_TAG, message)
                    }
                }
                // Le credenziali non devono mai finire nei log.
                sanitizeHeader { name ->
                    name.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                        name.equals(GOOGLE_API_KEY_HEADER, ignoreCase = true) ||
                        name.equals(TRAVELPAYOUTS_TOKEN_HEADER, ignoreCase = true)
                }
            }
        }

        defaultRequest {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
    }
}
