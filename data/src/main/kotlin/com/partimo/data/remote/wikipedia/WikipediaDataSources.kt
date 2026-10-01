package com.partimo.data.remote.wikipedia

import com.partimo.data.cache.CacheKey
import com.partimo.data.cache.CachePolicy
import com.partimo.data.cache.ResponseCache
import com.partimo.data.network.Fetched
import com.partimo.data.network.NetworkJson
import com.partimo.data.network.bestEffort
import com.partimo.data.network.combinedOrigin
import com.partimo.data.network.mapNotNullSafely
import com.partimo.data.source.PoiArticleDataSource
import com.partimo.data.source.PoiDataSource
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.poi.ImageCredit
import com.partimo.domain.model.poi.PoiArticle
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiQuery
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.model.poi.WikipediaPage
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/** Lingue di Wikipedia da interrogare: quella dell'app se supportata, poi l'inglese come riserva. */
internal fun wikipediaLanguages(appLanguage: String): List<String> =
    listOf(appLanguage.lowercase(Locale.ROOT), FALLBACK_LANGUAGE)
        .distinct()
        .filter { it in WikipediaPlaceClassifier.SUPPORTED_LANGUAGES }

private const val FALLBACK_LANGUAGE = "en"

/**
 * Luoghi da vedere da Wikipedia, gratuita e senza chiave: voci con coordinate vicine al centro,
 * dalla più nota alla meno nota, con foto reali. Città, enti, stazioni, eventi storici ed edifici
 * scomparsi vengono scartati ([WikipediaPlaceClassifier]).
 *
 * Se nella lingua dell'app i luoghi sono pochi (città piccole o lontane) si aggiungono quelli della
 * Wikipedia inglese, senza duplicare le voci che descrivono lo stesso luogo (stesso elemento Wikidata).
 */
class WikipediaPoiDataSource internal constructor(
    private val api: WikipediaApi,
    private val cache: ResponseCache,
    private val languages: List<String>,
) : PoiDataSource {

    init {
        require(languages.isNotEmpty()) { "Serve almeno una lingua di Wikipedia" }
    }

    override suspend fun pointsOfInterest(query: PoiQuery, forceRefresh: Boolean): Fetched<List<PointOfInterest>> {
        val primary = nearby(languages.first(), query, forceRefresh)
        val fallbackLanguage = languages.getOrNull(1)
        val results = if (primary.data.size >= MIN_RESULTS || fallbackLanguage == null) {
            listOf(primary)
        } else {
            // La lingua di riserva è un arricchimento: se non risponde restano i luoghi già trovati.
            listOfNotNull(primary, bestEffort { nearby(fallbackLanguage, query, forceRefresh) })
        }
        val places = results.flatMap { it.data }.distinctBy { it.wikidataId ?: "${it.language}:${it.page.title}" }
        return Fetched(data = rank(places), origin = combinedOrigin(results.map { it.origin }))
    }

    /**
     * Wikipedia filtra per distanza, mentre Google usa il raggio solo come preferenza: si cerca in un
     * raggio doppio (es. 10 km per Vienna, per includere Schönbrunn) e l'ordine per notorietà tiene
     * comunque in cima i luoghi principali.
     */
    private suspend fun nearby(language: String, query: PoiQuery, forceRefresh: Boolean): Fetched<List<WikiPlace>> {
        val radius = (query.radiusMeters * RADIUS_FACTOR).coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        return cache.getOrFetch(
            key = CacheKey.of("wiki-poi", language, coordinateKey(query.location), radius, SEARCH_LIMIT),
            ttl = CachePolicy.POIS,
            forceRefresh = forceRefresh,
            fetch = { api.nearbyPages(language, query.location, radius, SEARCH_LIMIT) },
            parse = { body ->
                parsePages(body)
                    .sortedBy { it.index ?: Int.MAX_VALUE }
                    .mapNotNullSafely { page -> page.toPlace(language, query.areaName) }
            },
        )
    }

    /**
     * Tiene i luoghi con una foto quando sono abbastanza; la notorietà segue l'ordine di rilevanza
     * (1 per il più noto) e serve al dominio per ordinare i luoghi senza recensioni.
     */
    private fun rank(places: List<WikiPlace>): List<PointOfInterest> {
        val withPhotos = places.filter { it.page.thumbnail != null }
        val shown = (if (withPhotos.size >= MIN_RESULTS) withPhotos else places).take(MAX_RESULTS)
        return shown.mapIndexed { index, place -> place.toPointOfInterest(popularity = 1.0 - index.toDouble() / shown.size) }
    }

    private fun WikiPage.toPlace(language: String, areaName: String?): WikiPlace? {
        if (missing) return null
        val coordinate = coordinates.firstOrNull { it.primary } ?: coordinates.firstOrNull() ?: return null
        val location = runCatching { GeoPoint(coordinate.lat, coordinate.lon) }.getOrNull() ?: return null
        // La voce della città stessa non è un luogo da visitare al suo interno.
        if (areaName != null && displayName(title).equals(areaName.trim(), ignoreCase = true)) return null
        val category = WikipediaPlaceClassifier.classify(title, description) ?: return null
        return WikiPlace(language, this, category, location)
    }

    private fun WikiPlace.toPointOfInterest(popularity: Double) = PointOfInterest(
        id = "wikipedia:$language:${page.pageid ?: page.title}",
        name = displayName(page.title),
        category = category,
        location = location,
        description = page.description?.let(::sentenceCase),
        photoUrl = page.thumbnail?.source,
        popularity = popularity,
        wikipediaPage = WikipediaPage(language, page.title),
    )

    /** Voce riconosciuta come luogo da visitare. */
    private class WikiPlace(val language: String, val page: WikiPage, val category: PoiCategory, val location: GeoPoint) {
        val wikidataId: String? get() = page.pageprops?.wikibaseItem
    }

    internal companion object {
        /** Massimo consentito da pageimages per una richiesta: tutti i risultati hanno la foto. */
        const val SEARCH_LIMIT = 50
        const val MAX_RESULTS = 30
        const val MIN_RESULTS = 8
        const val RADIUS_FACTOR = 2
        const val MIN_RADIUS_METERS = 2_000
        const val MAX_RADIUS_METERS = 20_000
    }
}

/**
 * Voce di Wikipedia di un luogo, per la scheda con descrizione, storia e crediti della foto.
 *
 * I luoghi arrivati da Wikipedia portano già il riferimento alla loro voce. Per gli altri (es. Google
 * Places) la voce si cerca per nome vicino alle coordinate e si accetta solo se il titolo corrisponde
 * al nome oppure se la voce più rilevante si trova praticamente nello stesso punto: meglio nessuna
 * storia che la storia di un altro luogo.
 */
class WikipediaArticleDataSource internal constructor(
    private val api: WikipediaApi,
    private val cache: ResponseCache,
    private val languages: List<String>,
) : PoiArticleDataSource {

    override suspend fun findArticle(poi: PointOfInterest, forceRefresh: Boolean): Fetched<PoiArticle?> {
        val origins = mutableListOf<DataOrigin>()
        val reference = poi.wikipediaPage ?: lookUp(poi, forceRefresh, origins)
        val page = reference?.let { articlePage(it, forceRefresh).also { fetched -> origins += fetched.origin }.data }
        val article = if (reference != null && page != null) {
            // I crediti della foto sono un arricchimento: senza, la scheda si mostra comunque.
            val credit = page.pageimage?.let { file -> bestEffort { imageCredit(reference.language, file, forceRefresh) } }
            page.toArticle(reference.language, credit)
        } else {
            null
        }
        return Fetched(article, combinedOrigin(origins))
    }

    private suspend fun lookUp(poi: PointOfInterest, forceRefresh: Boolean, origins: MutableList<DataOrigin>): WikipediaPage? {
        for (language in languages) {
            val candidates = cache.getOrFetch(
                key = CacheKey.of("wiki-lookup", language, poi.name, coordinateKey(poi.location)),
                ttl = CachePolicy.ARTICLES,
                forceRefresh = forceRefresh,
                fetch = { api.searchNearby(language, searchText(poi.name), poi.location, LOOKUP_RADIUS_METERS, LOOKUP_LIMIT) },
                parse = { body -> parsePages(body).sortedBy { it.index ?: Int.MAX_VALUE } },
            )
            origins += candidates.origin
            candidates.data.firstOrNull { it.describes(poi) }?.let { return WikipediaPage(language, it.title) }
        }
        return null
    }

    private fun WikiPage.describes(poi: PointOfInterest): Boolean {
        if (missing || pageprops?.disambiguation != null) return false
        val coordinate = coordinates.firstOrNull() ?: return false
        val distance = runCatching { poi.location.distanceTo(GeoPoint(coordinate.lat, coordinate.lon)) }.getOrNull() ?: return false
        val sameSpot = index == 1 && distance <= SAME_SPOT_METERS
        return sameSpot || (distance <= LOOKUP_RADIUS_METERS && PlaceNames.match(poi.name, displayName(title)))
    }

    private suspend fun articlePage(reference: WikipediaPage, forceRefresh: Boolean): Fetched<WikiPage?> =
        cache.getOrFetch(
            key = CacheKey.of("wiki-article", reference.language, reference.title),
            ttl = CachePolicy.ARTICLES,
            forceRefresh = forceRefresh,
            fetch = { api.article(reference.language, reference.title) },
            parse = { body ->
                parsePages(body).firstOrNull { page ->
                    !page.missing && page.pageprops?.disambiguation == null && !page.extract.isNullOrBlank()
                }
            },
        )

    private suspend fun imageCredit(language: String, fileName: String, forceRefresh: Boolean): ImageCredit? =
        cache.getOrFetch(
            key = CacheKey.of("wiki-image", language, fileName),
            ttl = CachePolicy.ARTICLES,
            forceRefresh = forceRefresh,
            fetch = { api.imageInfo(language, fileName) },
            parse = { body -> parsePages(body).firstOrNull()?.imageinfo?.firstOrNull()?.toCredit() },
        ).data

    private fun WikiPage.toArticle(language: String, credit: ImageCredit?): PoiArticle {
        val parsed = WikipediaArticleParser.parse(extract.orEmpty())
        return PoiArticle(
            title = displayName(title),
            language = language,
            url = fullurl ?: articleUrl(language, title),
            shortDescription = description,
            introduction = parsed.introduction,
            history = parsed.history,
            imageUrl = thumbnail?.source,
            imageCredit = credit,
        )
    }

    private fun WikiImageInfo.toCredit(): ImageCredit? {
        val author = extmetadata["Artist"]?.text()?.let(::plainText)?.take(MAX_AUTHOR_CHARS)
        val license = extmetadata["LicenseShortName"]?.text()?.let(::plainText)
        if (author == null && license == null) return null
        return ImageCredit(author = author, license = license, sourceUrl = descriptionurl)
    }

    private fun WikiMetadataValue.text(): String? = (value as? JsonPrimitive)?.contentOrNull

    internal companion object {
        const val LOOKUP_RADIUS_METERS = 1_000
        const val LOOKUP_LIMIT = 5

        /** Distanza entro cui la voce più rilevante è considerata lo stesso luogo anche con un nome diverso. */
        const val SAME_SPOT_METERS = 60.0
        private const val MAX_AUTHOR_CHARS = 80
    }
}

/** Confronto tra il nome di un luogo (es. da Google Places) e il titolo di una voce. */
internal object PlaceNames {

    private val STOPWORDS = setOf(
        "di", "del", "della", "dello", "dei", "degli", "delle", "da", "dal", "il", "lo", "la", "le", "gli",
        "in", "e", "a", "the", "of", "and", "at", "de", "des", "du", "der", "die", "das", "von",
    )
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
    private val MARKS = Regex("\\p{M}+")

    /**
     * `true` se i nomi indicano lo stesso luogo: stesse parole significative, oppure le parole di un
     * nome (almeno due) tutte contenute nell'altro ("Basilica di San Pietro" ~ "Basilica di San Pietro in Vaticano").
     */
    fun match(first: String, second: String): Boolean {
        val a = tokens(first)
        val b = tokens(second)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val smaller = if (a.size <= b.size) a else b
        val larger = if (smaller === a) b else a
        return smaller.size >= 2 && larger.containsAll(smaller)
    }

    fun tokens(name: String): Set<String> =
        Normalizer.normalize(name.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .split(SEPARATORS)
            .filter { it.length >= 2 && it !in STOPWORDS }
            .toSet()
}

private val DISAMBIGUATION_SUFFIX = Regex("""\s*\([^)]*\)$""")
private val HTML_TAG = Regex("<[^>]*>")
private val WHITESPACE = Regex("\\s+")
private val SEARCH_OPERATORS = Regex("[^\\p{L}\\p{N}' ]+")

internal fun parsePages(body: String): List<WikiPage> {
    val response = NetworkJson.decodeFromString(WikiQueryResponse.serializer(), body)
    require(response.error == null) { "Errore dell'API di Wikipedia: ${response.error?.code}" }
    return response.query?.pages.orEmpty()
}

/** Titolo senza la specificazione tra parentesi: "Pantheon (Roma)" → "Pantheon". */
internal fun displayName(title: String): String = title.replace(DISAMBIGUATION_SUFFIX, "").ifBlank { title }

/** Nome del luogo senza i caratteri che la ricerca di Wikipedia interpreta come operatori (-, ", *, :). */
private fun searchText(name: String): String = name.replace(SEARCH_OPERATORS, " ").replace(WHITESPACE, " ").trim()

private fun sentenceCase(text: String): String = text.trim().replaceFirstChar { it.titlecase(Locale.ROOT) }

/** Coordinate arrotondate a circa 10 m: spostamenti minimi del centro riusano la stessa risposta in cache. */
private fun coordinateKey(point: GeoPoint): String = String.format(Locale.ROOT, "%.4f,%.4f", point.latitude, point.longitude)

private fun articleUrl(language: String, title: String): String =
    "https://$language.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), Charsets.UTF_8.name())

/** Testo semplice da un metadato HTML (es. l'autore di una foto con il link al suo profilo). */
internal fun plainText(html: String): String? = html
    .replace(HTML_TAG, " ")
    .replace("&nbsp;", " ")
    .replace("&quot;", "\"")
    .replace("&#039;", "'")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
    .replace(WHITESPACE, " ")
    .trim()
    .takeIf { it.isNotEmpty() }
