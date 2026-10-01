package com.partimo.data.remote.gemini

import com.partimo.data.config.AndroidAppIdentity
import com.partimo.data.network.ApiKeyRejectedException
import com.partimo.data.network.NetworkJson
import com.partimo.data.network.toDataError
import com.partimo.data.testing.bodyText
import com.partimo.data.testing.jsonHeaders
import com.partimo.data.testing.mockHttpClient
import com.partimo.domain.common.DataError
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

// Con le risposte immediate del MockEngine le richieste "di riserva" non partono mai: i test usano
// il tempo reale (runBlocking), perché quello virtuale farebbe scattare subito il timer.
class GeminiApiTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val request = GeminiRequest(
        contents = listOf(GeminiContent.user("Ciao")),
        generationConfig = GeminiGenerationConfig(maxOutputTokens = 100),
    )

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = GeminiApi(
        client = mockHttpClient { request ->
            requests += request
            handler(request)
        },
        apiKey = "chiave-di-prova",
        androidApp = AndroidAppIdentity("com.partimo.app", "6EAD203C"),
        baseUrl = "https://gemini.test/v1beta/",
    )

    private fun MockRequestHandleScope.answer(text: String): HttpResponseData = respond(geminiResponse(text), HttpStatusCode.OK, jsonHeaders)

    private fun HttpRequestData.json(): JsonObject = NetworkJson.parseToJsonElement(bodyText()).jsonObject

    private val HttpRequestData.model: String get() = url.encodedPath.substringAfterLast('/').substringBefore(':')

    @Test
    fun `con i modelli sovraccarichi passa al successivo e la chiave non finisce nell'URL`() = runBlocking {
        val output = api { request ->
            if (request.model == "gemini-flash-latest") answer("Ciao!") else respond(HIGH_DEMAND, HttpStatusCode.ServiceUnavailable, jsonHeaders)
        }.generate(request) { it }

        assertEquals("Ciao!", output.value)
        assertEquals("gemini-flash-latest", output.model)
        assertEquals(listOf("gemini-flash-lite-latest", "gemini-3.1-flash-lite", "gemini-flash-latest"), requests.map { it.model })
        requests.forEach { request ->
            assertEquals("https://gemini.test/v1beta/models/${request.model}:generateContent", request.url.toString())
            assertEquals("chiave-di-prova", request.headers["x-goog-api-key"])
            assertEquals("com.partimo.app", request.headers["X-Android-Package"])
            assertEquals("6EAD203C", request.headers["X-Android-Cert"])
        }
        // Il ragionamento si limita solo per il modello che lo prevede; il resto della richiesta resta uguale.
        assertNull(requests[0].json()["generationConfig"]!!.jsonObject["thinkingConfig"])
        val flashConfig = requests[2].json()["generationConfig"]!!.jsonObject
        assertEquals("minimal", flashConfig["thinkingConfig"]!!.jsonObject["thinkingLevel"]!!.jsonPrimitive.content)
        assertEquals("100", flashConfig["maxOutputTokens"]!!.jsonPrimitive.content)
    }

    @Test
    fun `una chiave rifiutata non fa provare altri modelli`() = runBlocking {
        val error = assertFailsWith<ApiKeyRejectedException> {
            api { respond(KEY_INVALID, HttpStatusCode.BadRequest, jsonHeaders) }.generate(request) { it }
        }

        assertEquals(1, requests.size)
        assertEquals(DataError.Unauthorized, error.toDataError())
    }

    @Test
    fun `una risposta non conforme fa provare il modello successivo`() = runBlocking {
        var calls = 0
        val output = api { if (calls++ == 0) answer("""{"days": [""") else answer("""{"days": []}""") }
            .generate(request) { text -> NetworkJson.parseToJsonElement(text).jsonObject }

        assertEquals(2, requests.size)
        assertFalse(output.value["days"] == null)
    }

    @Test
    fun `senza testo è una risposta non valida, dopo aver provato tutti i modelli`() = runBlocking {
        val error = runCatching {
            api { respond("""{"promptFeedback":{"blockReason":"SAFETY"}}""", HttpStatusCode.OK, jsonHeaders) }.generate(request) { it }
        }.exceptionOrNull()!!

        assertEquals(3, requests.size)
        assertEquals(DataError.InvalidResponse, error.toDataError())
    }

    @Test
    fun `con i limiti gratuiti esauriti su tutti i modelli l'errore è il limite di richieste`() = runBlocking {
        val error = runCatching {
            api { respond("""{"error":{"code":429,"status":"RESOURCE_EXHAUSTED"}}""", HttpStatusCode.TooManyRequests, jsonHeaders) }
                .generate(request) { it }
        }.exceptionOrNull()!!

        assertEquals(3, requests.size)
        assertEquals(DataError.RateLimited, error.toDataError())
    }

    private companion object {
        const val HIGH_DEMAND = """{"error":{"code":503,"message":"This model is currently experiencing high demand.","status":"UNAVAILABLE"}}"""
        const val KEY_INVALID = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"API_KEY_INVALID","domain":"googleapis.com"}]}}"""
    }
}

/** Risposta di generateContent con un solo testo, come quelle reali. */
internal fun geminiResponse(text: String): String = NetworkJson.encodeToString(
    GeminiResponse.serializer(),
    GeminiResponse(candidates = listOf(GeminiCandidate(content = GeminiContent.model(text), finishReason = "STOP"))),
)
