package com.partimo.data.remote.wikipedia

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// DTO della MediaWiki Action API (action=query, formatversion=2):
// https://www.mediawiki.org/wiki/API:Query

@Serializable
internal data class WikiQueryResponse(
    val query: WikiQuery? = null,
    /** Errore applicativo: l'API risponde 200 anche per parametri non validi. */
    val error: WikiApiError? = null,
)

@Serializable
internal data class WikiApiError(val code: String? = null, val info: String? = null)

@Serializable
internal data class WikiQuery(val pages: List<WikiPage> = emptyList())

@Serializable
internal data class WikiPage(
    val pageid: Long? = null,
    val title: String,
    /** Posizione nei risultati di ricerca (1 = più rilevante); presente solo con generator=search. */
    val index: Int? = null,
    val missing: Boolean = false,
    val coordinates: List<WikiCoordinate> = emptyList(),
    /** Descrizione breve (Wikidata o locale), es. "antico anfiteatro romano a Roma". */
    val description: String? = null,
    val thumbnail: WikiImage? = null,
    /** Nome del file dell'immagine principale della voce, es. "Colosseo_2020.jpg". */
    val pageimage: String? = null,
    val pageprops: WikiPageProps? = null,
    /** Testo semplice della voce, con i titoli delle sezioni nel formato "== Storia ==". */
    val extract: String? = null,
    val fullurl: String? = null,
    val imageinfo: List<WikiImageInfo> = emptyList(),
)

@Serializable
internal data class WikiCoordinate(val lat: Double, val lon: Double, val primary: Boolean = false)

@Serializable
internal data class WikiImage(val source: String, val width: Int? = null, val height: Int? = null)

@Serializable
internal data class WikiPageProps(
    @SerialName("wikibase_item") val wikibaseItem: String? = null,
    /** Presente (stringa vuota) nelle pagine di disambiguazione. */
    val disambiguation: String? = null,
)

@Serializable
internal data class WikiImageInfo(
    /** Pagina del file su Wikimedia Commons, con autore e licenza completi. */
    val descriptionurl: String? = null,
    val extmetadata: Map<String, WikiMetadataValue> = emptyMap(),
)

/** Valore dei metadati di un file: di solito testo (anche HTML), ma l'API non lo garantisce. */
@Serializable
internal data class WikiMetadataValue(val value: JsonElement? = null)
