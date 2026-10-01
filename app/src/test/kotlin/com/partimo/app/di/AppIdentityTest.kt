package com.partimo.app.di

import com.partimo.data.config.AndroidAppIdentity
import com.partimo.data.network.AndroidAppHeaders
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppIdentityTest {

    private val identity = AndroidAppIdentity(packageName = "com.partimo.app", certificateSha1 = "A9993E364706816ABA3E25717850C26C9CD0D89D")

    /** Esegue la richiesta con l'intercettore e restituisce ciò che sarebbe partito verso la rete. */
    private fun sentRequest(url: String, interceptor: AppIdentityInterceptor): Request {
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().close()
        return requireNotNull(sent)
    }

    @Test
    fun `le foto di Wikipedia partono con lo User-Agent di PartiMo, senza intestazioni Android`() {
        val request = sentRequest(
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/d/de/Colosseo.jpg/960px-Colosseo.jpg",
            AppIdentityInterceptor(androidApp = identity),
        )

        assertEquals(PARTIMO_USER_AGENT, request.header("User-Agent"))
        assertTrue(PARTIMO_USER_AGENT.startsWith("PartiMo/") && "https://" in PARTIMO_USER_AGENT, "Serve un contatto: $PARTIMO_USER_AGENT")
        assertNull(request.header(AndroidAppHeaders.PACKAGE), "Package e certificato vanno solo a Google")
    }

    @Test
    fun `le foto di Google Places dichiarano package e certificato dell'app`() {
        val request = sentRequest(
            "https://places.googleapis.com/v1/places/p1/photos/abc/media?maxWidthPx=640&key=test",
            AppIdentityInterceptor(androidApp = identity),
        )

        assertEquals("com.partimo.app", request.header(AndroidAppHeaders.PACKAGE))
        assertEquals("A9993E364706816ABA3E25717850C26C9CD0D89D", request.header(AndroidAppHeaders.CERTIFICATE))
    }

    @Test
    fun `l'impronta del certificato è in esadecimale maiuscolo senza separatori`() {
        assertEquals("A9993E364706816ABA3E25717850C26C9CD0D89D", certificateSha1("abc".toByteArray()))
    }
}
