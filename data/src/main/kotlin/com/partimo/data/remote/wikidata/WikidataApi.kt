package com.partimo.data.remote.wikidata

import com.partimo.domain.model.GeoPoint
import io.ktor.client.HttpClient
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import java.util.Locale

/**
 * Client minimale del servizio SPARQL di Wikidata: gratuito e senza chiave. I dati di Wikidata
 * sono di pubblico dominio (CC0).
 *
 * Wikimedia chiede uno User-Agent identificabile e un uso leggero: le query possono durare decine
 * di secondi e il servizio limita le richieste ripetute, quindi niente tentativi automatici (le
 * risposte restano comunque in cache per giorni).
 * https://www.mediawiki.org/wiki/Wikidata_Query_Service/User_Manual
 */
internal class WikidataApi(
    private val client: HttpClient,
    private val userAgent: String,
    private val endpoint: String = ENDPOINT,
) {

    suspend fun sparql(query: String): String =
        client.get(endpoint) {
            header(HttpHeaders.UserAgent, userAgent)
            parameter("format", "json")
            parameter("query", query)
            timeout {
                requestTimeoutMillis = QUERY_TIMEOUT_MILLIS
                socketTimeoutMillis = QUERY_TIMEOUT_MILLIS
            }
            retry { noRetry() }
        }.bodyAsText()

    companion object {
        const val ENDPOINT = "https://query.wikidata.org/sparql"

        /** Il servizio interrompe le query dopo 60 secondi: si aspetta un poco di più per ricevere l'errore. */
        private const val QUERY_TIMEOUT_MILLIS = 65_000L
    }
}

/** Query SPARQL usate dall'app. */
internal object WikidataQueries {

    /** "Christmas market": classe Wikidata dei mercatini di Natale. */
    const val CHRISTMAS_MARKET_CLASS = "Q57607"

    /**
     * Eventi che si ripetono ogni anno entro [radiusMeters] da [center]: quelli con il mese
     * (P2922, es. Oktoberfest a settembre e ottobre) o il giorno (P837, es. concerto di Capodanno il
     * 1° gennaio) e i mercatini di Natale. La posizione è quella dell'evento, del luogo che lo ospita
     * (P276) o della città in cui si tiene (P131). Per il luogo si scartano città e quartieri (hanno
     * una popolazione) e si chiedono anche data di chiusura, foto e voci di Wikipedia nella lingua
     * dell'app e in inglese. Le etichette ripiegano sulle lingue più diffuse.
     */
    fun recurringEvents(center: GeoPoint, radiusMeters: Int, language: String): String {
        val point = String.format(Locale.ROOT, "Point(%.5f %.5f)", center.longitude, center.latitude)
        val radiusKm = String.format(Locale.ROOT, "%.1f", radiusMeters / 1_000.0)
        return """
            SELECT ?item ?itemLabel ?itemDescription
                   (GROUP_CONCAT(DISTINCT ?month; separator=" ") AS ?months)
                   (GROUP_CONCAT(DISTINCT ?dayLabel; separator="|") AS ?days)
                   (MAX(?market) AS ?christmasMarket)
                   (SAMPLE(?ownCoord) AS ?ownLocation) (SAMPLE(?venueCoord) AS ?venueLocation) (SAMPLE(?coord) AS ?areaLocation)
                   (SAMPLE(?placeLabel) AS ?venue) (SAMPLE(?endedTime) AS ?ended)
                   (SAMPLE(?image) AS ?picture) (SAMPLE(?localArticle) AS ?localTitle) (SAMPLE(?enArticle) AS ?enTitle)
            WHERE {
              { ?item wdt:P2922 ?m . BIND(STRAFTER(STR(?m), "/entity/") AS ?month) }
              UNION { ?item wdt:P837 ?d . ?d rdfs:label ?dayLabel . FILTER(LANG(?dayLabel) = "en") }
              UNION { ?item wdt:P31 wd:$CHRISTMAS_MARKET_CLASS . BIND(1 AS ?market) }
              { ?item wdt:P625 ?coord . } UNION { ?item wdt:P276/wdt:P625 ?coord . } UNION { ?item wdt:P131/wdt:P625 ?coord . }
              FILTER(geof:distance(?coord, "$point"^^geo:wktLiteral) < $radiusKm)
              OPTIONAL { ?item wdt:P625 ?ownCoord . }
              OPTIONAL {
                ?item wdt:P276 ?place .
                FILTER NOT EXISTS { ?place wdt:P1082 [] }
                ?place wdt:P625 ?venueCoord ; rdfs:label ?placeLabel .
                FILTER(LANG(?placeLabel) IN ("$language", "en"))
              }
              OPTIONAL { ?item wdt:P576|wdt:P582 ?endedTime . }
              OPTIONAL { ?item wdt:P18 ?image . }
              OPTIONAL { ?localPage schema:about ?item ; schema:isPartOf <https://$language.wikipedia.org/> ; schema:name ?localArticle . }
              OPTIONAL { ?enPage schema:about ?item ; schema:isPartOf <https://en.wikipedia.org/> ; schema:name ?enArticle . }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "$language,en,mul,$LABEL_FALLBACK_LANGUAGES". ?item rdfs:label ?itemLabel . ?item schema:description ?itemDescription . }
            }
            GROUP BY ?item ?itemLabel ?itemDescription
            LIMIT $MAX_ROWS
        """.trimIndent()
    }

    private const val LABEL_FALLBACK_LANGUAGES = "de,fr,es,nl,pt,pl,cs,sv,da,nb,fi,hu,el,tr,ja,zh"
    private const val MAX_ROWS = 100
}
