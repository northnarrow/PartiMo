package com.partimo.data.remote.currency

import com.partimo.data.cache.CacheKey
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.network.Fetched
import com.partimo.data.network.NetworkJson
import com.partimo.data.source.ExchangeRateDataSource
import com.partimo.domain.model.guide.ExchangeRates
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.util.Locale

/**
 * Tassi di cambio di [ExchangeRate-API](https://www.exchangerate-api.com/docs/free) (accesso aperto):
 * gratuito, senza chiave, oltre 160 valute, aggiornato una volta al giorno. Chiede di citare la fonte
 * e di non interrogarlo più di una volta l'ora: le risposte restano in cache 12 ore.
 */
internal class ExchangeRateApiDataSource(
    private val client: HttpClient,
    private val cache: ResponseCache,
    private val userAgent: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : ExchangeRateDataSource {

    override suspend fun latestRates(base: String, forceRefresh: Boolean): Fetched<ExchangeRates> {
        val currency = base.uppercase(Locale.ROOT)
        require(CURRENCY_CODE.matches(currency)) { "Valuta non valida: $base" }
        return cache.getOrFetch(
            key = CacheKey.of("fx-rates", currency),
            ttl = CachePolicy.EXCHANGE_RATES,
            forceRefresh = forceRefresh,
            fetch = { client.get("$baseUrl/latest/$currency") { header(HttpHeaders.UserAgent, userAgent) }.bodyAsText() },
            parse = { body -> parse(body, currency) },
        )
    }

    private fun parse(body: String, base: String): ExchangeRates {
        val response = NetworkJson.decodeFromString(ExchangeRateResponse.serializer(), body)
        require(response.result == "success" && response.baseCode == base) { "Risposta non valida: ${response.errorType}" }
        val rates = response.rates.mapNotNull { (code, value) -> value.content.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.let { code to it } }.toMap()
        require(rates.isNotEmpty()) { "Nessun tasso di cambio" }
        return ExchangeRates(base = base, rates = rates, updatedAt = response.lastUpdateUnix?.let(Instant::ofEpochSecond))
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://open.er-api.com/v6"

        /** Pagina da citare come fonte dei tassi (richiesto dalle condizioni d'uso). */
        const val ATTRIBUTION_URL = "https://www.exchangerate-api.com"
        private val CURRENCY_CODE = Regex("[A-Z]{3}")
    }
}

@Serializable
internal data class ExchangeRateResponse(
    val result: String,
    @SerialName("base_code") val baseCode: String? = null,
    @SerialName("time_last_update_unix") val lastUpdateUnix: Long? = null,
    @SerialName("error-type") val errorType: String? = null,
    /** I valori arrivano come numeri JSON: letti come testo per non perdere precisione. */
    val rates: Map<String, JsonPrimitive> = emptyMap(),
)
