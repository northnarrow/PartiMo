package com.partimo.data.cache

import com.partimo.data.network.Fetched
import com.partimo.data.testing.FakeCachedResponseDao
import com.partimo.data.testing.MutableClock
import com.partimo.domain.common.DataOrigin
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResponseCacheTest {

    private val clock = MutableClock()
    private val dao = FakeCachedResponseDao()
    private val cache = ResponseCache(dao, clock)
    private val ttl = Duration.ofMinutes(10)
    private var networkCalls = 0

    private suspend fun load(
        forceRefresh: Boolean = false,
        fetch: suspend () -> String = { networkCalls++; "42" },
    ): Fetched<Int> = cache.getOrFetch("key", ttl, forceRefresh, fetch, parse = String::toInt)

    @Test
    fun `la prima richiesta va in rete e salva la risposta`() = runTest {
        assertEquals(Fetched(42, DataOrigin.REMOTE), load())
        assertEquals(1, networkCalls)
        assertEquals("42", dao.entries["key"]?.payload)
    }

    @Test
    fun `entro il TTL la risposta arriva dalla cache senza chiamate di rete`() = runTest {
        load()
        clock.advance(Duration.ofMinutes(9))

        assertEquals(Fetched(42, DataOrigin.CACHE), load())
        assertEquals(1, networkCalls)
    }

    @Test
    fun `a TTL scaduto la risposta viene riscaricata`() = runTest {
        load()
        clock.advance(Duration.ofMinutes(11))

        assertEquals(DataOrigin.REMOTE, load().origin)
        assertEquals(2, networkCalls)
    }

    @Test
    fun `forceRefresh ignora una cache ancora valida`() = runTest {
        load()

        assertEquals(DataOrigin.REMOTE, load(forceRefresh = true).origin)
        assertEquals(2, networkCalls)
    }

    @Test
    fun `offline restituisce il dato scaduto come fallback`() = runTest {
        load()
        clock.advance(Duration.ofHours(3))

        val result = load { throw IOException("offline") }

        assertEquals(Fetched(42, DataOrigin.STALE_CACHE), result)
    }

    @Test
    fun `gli errori non transitori non usano il fallback`() = runTest {
        load()
        clock.advance(Duration.ofHours(3))

        assertFailsWith<NumberFormatException> { load { "non-numerico" } }
        assertEquals("42", dao.entries["key"]?.payload, "La entry valida non deve essere sovrascritta")
    }

    @Test
    fun `senza cache l'errore di rete viene propagato`() = runTest {
        assertFailsWith<IOException> { load { throw IOException("offline") } }
    }

    @Test
    fun `una risposta non interpretabile non viene salvata`() = runTest {
        assertFailsWith<NumberFormatException> { load { "abc" } }
        assertNull(dao.entries["key"])
    }

    @Test
    fun `un payload corrotto in cache viene scartato e riscaricato`() = runTest {
        dao.entries["key"] = CachedResponseEntity("key", "corrotto", clock.millis())

        assertEquals(Fetched(42, DataOrigin.REMOTE), load())
        assertEquals("42", dao.entries["key"]?.payload)
    }

    @Test
    fun `la pulizia elimina solo le entry troppo vecchie`() = runTest {
        dao.entries["old"] = CachedResponseEntity("old", "1", clock.millis() - Duration.ofDays(8).toMillis())
        dao.entries["recent"] = CachedResponseEntity("recent", "2", clock.millis() - Duration.ofDays(1).toMillis())

        assertEquals(1, cache.purgeOlderThan(Duration.ofDays(7)))
        assertTrue("recent" in dao.entries)
    }

    @Test
    fun `la chiave di cache è stabile e distingue i parametri`() {
        assertEquals(CacheKey.of("flights", "MXP", "VIE"), CacheKey.of("flights", "MXP", "VIE"))
        assertTrue(CacheKey.of("flights", "MXP", "VIE") != CacheKey.of("flights", "VIE", "MXP"))
        assertTrue(CacheKey.of("flights", "MXP").startsWith("flights:"))
    }
}
