package com.partimo.data.testing

import com.partimo.data.cache.CachedResponseDao
import com.partimo.data.cache.CachedResponseEntity
import com.partimo.data.cache.ResponseCache
import com.partimo.data.network.HttpClientFactory
import com.partimo.domain.testing.TestData
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Orologio controllabile per verificare TTL e scadenze della cache. */
class MutableClock(
    var now: Instant = TestData.NOW,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

/** DAO in memoria con la stessa semantica di quello Room. */
class FakeCachedResponseDao : CachedResponseDao {
    val entries = linkedMapOf<String, CachedResponseEntity>()

    override suspend fun get(key: String): CachedResponseEntity? = entries[key]

    override suspend fun upsert(entity: CachedResponseEntity) {
        entries[entity.cacheKey] = entity
    }

    override suspend fun delete(key: String) {
        entries.remove(key)
    }

    override suspend fun deleteOlderThan(thresholdMillis: Long): Int {
        val expired = entries.values.filter { it.storedAtMillis < thresholdMillis }
        expired.forEach { entries.remove(it.cacheKey) }
        return expired.size
    }
}

fun inMemoryCache(clock: Clock = MutableClock()): ResponseCache = ResponseCache(FakeCachedResponseDao(), clock)

/** Client con la configurazione di produzione ma motore finto e senza retry. */
fun mockHttpClient(handler: MockRequestHandler): HttpClient =
    HttpClientFactory.create(engine = MockEngine(handler), enableLogging = false, maxRetries = 0)

/**
 * Come [mockHttpClient], ma il motore finto gira su [dispatcher] (quello di `runTest`): anche le risposte seguono il
 * tempo virtuale, così le attese (richieste di riserva, intervalli tra le richieste) si misurano esattamente.
 */
fun mockHttpClient(dispatcher: CoroutineDispatcher, handler: MockRequestHandler): HttpClient {
    val config = MockEngineConfig().apply {
        this.dispatcher = dispatcher
        addHandler(handler)
    }
    return HttpClientFactory.create(engine = MockEngine(config), enableLogging = false, maxRetries = 0)
}

val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

/** Body JSON inviato dal client (ContentNegotiation produce un TextContent). */
fun HttpRequestData.bodyText(): String = (body as TextContent).text
