package com.partimo.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HedgingTest {

    /** Come per i modelli: si riprova con il successivo tranne che per gli errori "definitivi". */
    private val worthAnother = { error: Throwable -> error !is IllegalStateException }

    @Test
    fun `restituisce il primo che riesce senza avviare gli altri`() = runTest {
        val started = mutableListOf<String>()

        val result = firstSuccessful(
            listOf(suspend { started += "primo"; "a" }, suspend { started += "secondo"; "b" }),
            hedgeAfterMillis = 1_000,
            shouldTryNext = worthAnother,
        )

        assertEquals("a", result)
        assertEquals(listOf("primo"), started)
    }

    @Test
    fun `un errore transitorio passa subito al successivo`() = runTest {
        val result = firstSuccessful(
            listOf(suspend { throw IOException("503") }, suspend { "b" }),
            hedgeAfterMillis = 10_000,
            shouldTryNext = worthAnother,
        )

        assertEquals("b", result)
        assertEquals(0L, currentTime, "Nessuna attesa prima del secondo tentativo")
    }

    @Test
    fun `un errore definitivo si propaga senza provare gli altri`() = runTest {
        var secondStarted = false

        assertFailsWith<IllegalStateException> {
            firstSuccessful(
                listOf(suspend { throw IllegalStateException("chiave rifiutata") }, suspend { secondStarted = true; "b" }),
                hedgeAfterMillis = 10_000,
                shouldTryNext = worthAnother,
            )
        }
        assertFalse(secondStarted)
    }

    @Test
    fun `se il primo è lento parte anche il secondo e vince il più rapido`() = runTest {
        var slowCancelled = false

        val result = firstSuccessful(
            listOf(
                suspend {
                    try {
                        delay(60_000)
                        "lento"
                    } catch (e: CancellationException) {
                        slowCancelled = true
                        throw e
                    }
                },
                suspend {
                    delay(2_000)
                    "rapido"
                },
            ),
            hedgeAfterMillis = 5_000,
            shouldTryNext = worthAnother,
        )

        assertEquals("rapido", result)
        assertEquals(7_000L, currentTime, "Il secondo parte dopo 5 s e risponde in 2 s")
        assertTrue(slowCancelled, "Il tentativo lento viene annullato")
    }

    @Test
    fun `se il secondo fallisce resta valido il primo, anche se lento`() = runTest {
        val result = firstSuccessful(
            listOf(suspend { delay(8_000); "primo" }, suspend { throw IOException("sovraccarico") }),
            hedgeAfterMillis = 5_000,
            shouldTryNext = worthAnother,
        )

        assertEquals("primo", result)
        assertEquals(8_000L, currentTime)
    }

    @Test
    fun `se falliscono tutti si propaga l'errore dell'ultimo`() = runTest {
        val error = assertFailsWith<IOException> {
            firstSuccessful(
                listOf(suspend { throw IOException("primo") }, suspend { delay(10); throw IOException("ultimo") }),
                hedgeAfterMillis = 5_000,
                shouldTryNext = worthAnother,
            )
        }

        assertEquals("ultimo", error.message)
    }
}
