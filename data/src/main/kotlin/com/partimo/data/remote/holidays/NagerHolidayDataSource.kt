package com.partimo.data.remote.holidays

import com.partimo.data.cache.CacheKey
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.network.Fetched
import com.partimo.data.network.NetworkJson
import com.partimo.data.network.mapNotNullSafely
import com.partimo.data.source.HolidayDataSource
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.event.EventKind
import com.partimo.domain.model.event.EventTiming
import com.partimo.domain.model.event.TripEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate
import java.util.Locale

/**
 * Festività nazionali da Nager.Date: gratuito, senza chiave, oltre cento paesi. Si tengono quelle
 * valide in tutto il paese (le regionali dipendono da dove si alloggia). I nomi arrivano nella lingua
 * del paese e in inglese: in italiano si traducono quelli più comuni.
 * https://date.nager.at/Api
 */
class NagerHolidayDataSource internal constructor(
    private val client: HttpClient,
    private val cache: ResponseCache,
    private val userAgent: String,
    languageCode: String,
    private val baseUrl: String = BASE_URL,
) : HolidayDataSource {

    private val italian = languageCode.lowercase(Locale.ROOT) == "it"

    override suspend fun publicHolidays(countryCode: String, year: Int, forceRefresh: Boolean): Fetched<List<TripEvent>> {
        val country = countryCode.uppercase(Locale.ROOT).takeIf { COUNTRY_CODE.matches(it) }
            ?: return Fetched(emptyList(), DataOrigin.REMOTE)
        return cache.getOrFetch(
            key = CacheKey.of("nager-holidays", country, year),
            ttl = CachePolicy.HOLIDAYS,
            forceRefresh = forceRefresh,
            fetch = { download(country, year) },
            parse = { body ->
                if (body.isBlank()) {
                    emptyList()
                } else {
                    NetworkJson.decodeFromString(ListSerializer(NagerHoliday.serializer()), body)
                        .filter { it.global && (PUBLIC in it.types || it.type == PUBLIC) }
                        .mapNotNullSafely { it.toEvent(country) }
                }
            },
        )
    }

    /** Un paese che il servizio non conosce risponde 404: nessuna festività da mostrare, non un errore. */
    private suspend fun download(country: String, year: Int): String = try {
        client.get("$baseUrl/PublicHolidays/$year/$country") { header(HttpHeaders.UserAgent, userAgent) }.bodyAsText()
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.NotFound) "[]" else throw e
    }

    private fun NagerHoliday.toEvent(country: String): TripEvent {
        val day = LocalDate.parse(date)
        val displayName = when {
            italian && country == ITALY -> localName
            italian -> ITALIAN_NAMES[name.lowercase(Locale.ROOT)] ?: name
            else -> name
        }
        return TripEvent(
            id = "holiday:$country:$date:$name",
            name = displayName,
            kind = EventKind.PUBLIC_HOLIDAY,
            timing = EventTiming.OnDates(day, day),
            localName = localName.takeUnless { it.equals(displayName, ignoreCase = true) },
        )
    }

    internal companion object {
        const val BASE_URL = "https://date.nager.at/api/v3"
        private const val PUBLIC = "Public"
        private const val ITALY = "IT"
        private val COUNTRY_CODE = Regex("[A-Z]{2}")

        /** Nomi inglesi delle festività più comuni (in minuscolo) → nome italiano. */
        private val ITALIAN_NAMES = mapOf(
            "new year's day" to "Capodanno",
            "epiphany" to "Epifania",
            "good friday" to "Venerdì santo",
            "holy saturday" to "Sabato santo",
            "easter sunday" to "Pasqua",
            "easter" to "Pasqua",
            "easter monday" to "Lunedì dell'Angelo",
            "labour day" to "Festa dei lavoratori",
            "labor day" to "Festa del lavoro",
            "may day" to "Primo maggio",
            "ascension day" to "Ascensione",
            "whit sunday" to "Pentecoste",
            "pentecost" to "Pentecoste",
            "whit monday" to "Lunedì di Pentecoste",
            "corpus christi" to "Corpus Domini",
            "assumption day" to "Assunzione (Ferragosto)",
            "assumption of mary" to "Assunzione (Ferragosto)",
            "assumption" to "Assunzione (Ferragosto)",
            "all saints' day" to "Ognissanti",
            "all saints day" to "Ognissanti",
            "all souls' day" to "Commemorazione dei defunti",
            "immaculate conception" to "Immacolata Concezione",
            "christmas eve" to "Vigilia di Natale",
            "christmas day" to "Natale",
            "christmas" to "Natale",
            "st. stephen's day" to "Santo Stefano",
            "saint stephen's day" to "Santo Stefano",
            "boxing day" to "Santo Stefano",
            "new year's eve" to "San Silvestro",
            "national day" to "Festa nazionale",
            "national holiday" to "Festa nazionale",
            "international workers day" to "Festa dei lavoratori",
            "st. francis of assisi's day" to "San Francesco d'Assisi",
            "independence day" to "Festa dell'indipendenza",
            "liberation day" to "Festa della Liberazione",
            "republic day" to "Festa della Repubblica",
            "constitution day" to "Festa della Costituzione",
            "german unity day" to "Festa dell'unità tedesca",
            "bastille day" to "Presa della Bastiglia",
            "armistice day" to "Anniversario dell'armistizio",
            "victory in europe day" to "Festa della vittoria",
            "reformation day" to "Festa della Riforma",
            "saint joseph's day" to "San Giuseppe",
            "thanksgiving day" to "Giorno del Ringraziamento",
        )
    }
}

/** Festività della risposta di Nager.Date (API v3). */
@Serializable
internal data class NagerHoliday(
    val date: String,
    val localName: String,
    val name: String,
    /** `true` se vale in tutto il paese, `false` se solo in alcune regioni. */
    val global: Boolean = true,
    val types: List<String> = emptyList(),
    /** Campo delle versioni precedenti dell'API, al posto di [types]. */
    val type: String? = null,
)
