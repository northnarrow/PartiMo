package com.partimo.data.cache

import com.partimo.data.network.Fetched
import com.partimo.data.network.isTransient
import com.partimo.domain.common.DataOrigin
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cache delle risposte di ricerca, persistita con Room, per ridurre il consumo di chiamate API.
 *
 * Politica:
 * - entry presente, leggibile e più giovane del TTL (e nessun `forceRefresh`) → dato dalla cache,
 *   nessuna chiamata di rete;
 * - altrimenti chiamata remota: il body viene prima interpretato e solo dopo salvato,
 *   così in cache non finiscono mai risposte non valide;
 * - se la chiamata fallisce per un errore transitorio (offline, timeout, 5xx, 429) e c'è una entry
 *   scaduta, questa viene restituita come fallback con origine [DataOrigin.STALE_CACHE].
 */
class ResponseCache(
    private val dao: CachedResponseDao,
    private val clock: Clock,
) {

    suspend fun <T> getOrFetch(
        key: String,
        ttl: Duration,
        forceRefresh: Boolean = false,
        fetch: suspend () -> String,
        parse: (String) -> T,
    ): Fetched<T> {
        val cached = readCached(key, parse)
        if (!forceRefresh && cached != null && cached.ageMillis() < ttl.toMillis()) {
            return Fetched(cached.value, DataOrigin.CACHE)
        }
        return try {
            val body = fetch()
            val parsed = parse(body)
            dao.upsert(CachedResponseEntity(cacheKey = key, payload = body, storedAtMillis = clock.millis()))
            Fetched(parsed, DataOrigin.REMOTE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached != null && e.isTransient()) Fetched(cached.value, DataOrigin.STALE_CACHE) else throw e
        }
    }

    /** Elimina le entry più vecchie di [maxAge], che non servono più nemmeno come fallback offline. */
    suspend fun purgeOlderThan(maxAge: Duration): Int = dao.deleteOlderThan(clock.millis() - maxAge.toMillis())

    private suspend fun <T> readCached(key: String, parse: (String) -> T): CachedValue<T>? {
        val entity = dao.get(key) ?: return null
        return try {
            CachedValue(parse(entity.payload), entity.storedAtMillis)
        } catch (e: Exception) {
            // Payload non più interpretabile (es. formato cambiato dopo un aggiornamento): si scarta.
            dao.delete(key)
            null
        }
    }

    private fun CachedValue<*>.ageMillis(): Long = clock.millis() - storedAtMillis

    private class CachedValue<T>(val value: T, val storedAtMillis: Long)
}

/** Chiave di cache stabile e compatta: namespace + SHA-256 dei parametri normalizzati della richiesta. */
internal object CacheKey {

    fun of(namespace: String, vararg parts: Any?): String {
        val raw = parts.joinToString(separator = "|") { it?.toString().orEmpty() }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return namespace + ":" + digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

/** Durata di validità delle risposte, calibrata sulla volatilità di ciascun tipo di dato. */
internal object CachePolicy {
    /** Le offerte volo scadono lato provider in circa 20–30 minuti. */
    val FLIGHTS: Duration = Duration.ofMinutes(20)
    val STAYS: Duration = Duration.ofHours(1)

    /** POI e ristoranti cambiano raramente. */
    val POIS: Duration = Duration.ofHours(24)

    /** Testi e crediti delle voci enciclopediche cambiano pochissimo. */
    val ARTICLES: Duration = Duration.ofDays(7)
    val RESTAURANTS: Duration = Duration.ofHours(12)
    val LODGINGS: Duration = Duration.ofHours(24)

    /** Gli eventi ricorrenti e le festività cambiano pochissimo (e il servizio degli eventi limita le richieste). */
    val EVENTS: Duration = Duration.ofDays(7)
    val HOLIDAYS: Duration = Duration.ofDays(30)

    /** Orari in tempo reale: cache minima, solo per evitare richieste duplicate ravvicinate. */
    val TRANSIT: Duration = Duration.ofMinutes(2)
    val WEATHER: Duration = Duration.ofMinutes(15)

    /** Previsioni dei prossimi giorni: Open-Meteo le aggiorna più volte al giorno. */
    val DAILY_FORECAST: Duration = Duration.ofHours(3)

    /** I dati degli anni passati non cambiano: si tengono quanto le altre risposte. */
    val CLIMATE: Duration = Duration.ofDays(30)

    /**
     * Itinerari proposti dall'assistente: restano finché l'utente non chiede di rigenerarli o cambia
     * viaggio o preferenze (che producono un'altra chiave).
     */
    val AI_PLAN: Duration = Duration.ofDays(30)

    /** Le guide di viaggio cambiano poco; i cambi si aggiornano una volta al giorno (e il servizio chiede di non superare una richiesta l'ora). */
    val GUIDES: Duration = Duration.ofDays(7)
    val EXCHANGE_RATES: Duration = Duration.ofHours(12)

    /** I risultati della ricerca città cambiano molto raramente. */
    val GEOCODING: Duration = Duration.ofDays(7)

    /** Oltre questa età le entry vengono eliminate anche come fallback offline. */
    val MAX_ENTRY_AGE: Duration = Duration.ofDays(30)
}
