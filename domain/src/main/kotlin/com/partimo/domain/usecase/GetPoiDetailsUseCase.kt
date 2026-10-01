package com.partimo.domain.usecase

import com.partimo.domain.common.DataResult
import com.partimo.domain.common.map
import com.partimo.domain.model.poi.ArticleSection
import com.partimo.domain.model.poi.HistoryChapter
import com.partimo.domain.model.poi.PoiArticle
import com.partimo.domain.model.poi.PoiDetails
import com.partimo.domain.model.poi.PointOfInterest
import com.partimo.domain.repository.PoiArticleRepository
import com.partimo.domain.service.Excerpt

/**
 * Scheda di un luogo da visitare: breve descrizione e cenni storici ricavati dalla sua voce
 * enciclopedica (Wikipedia) e ridotti a un estratto che si legge in un paio di minuti.
 *
 * La storia diventa una sequenza di capitoli (es. costruzione, Medioevo, restauri), ognuno con il
 * suo primo paragrafo. Se la voce non esiste restano descrizione e foto del provider dei luoghi.
 */
class GetPoiDetailsUseCase(
    private val repository: PoiArticleRepository,
    private val limits: Limits = Limits(),
) {

    data class Limits(
        val summaryMaxChars: Int = 650,
        val summaryMaxParagraphs: Int = 2,
        /** Ogni capitolo della storia mostra il suo primo paragrafo, accorciato a questa lunghezza. */
        val chapterMaxChars: Int = 360,
        val maxChapters: Int = 5,
        /** Storia senza sottosezioni: un unico racconto, con qualche paragrafo in più. */
        val singleStoryMaxChars: Int = 900,
        val singleStoryMaxParagraphs: Int = 3,
    )

    suspend operator fun invoke(poi: PointOfInterest, forceRefresh: Boolean = false): DataResult<PoiDetails> =
        repository.findArticle(poi, forceRefresh).map { article ->
            if (article == null) providerDetails(poi) else articleDetails(article, poi)
        }

    private fun providerDetails(poi: PointOfInterest) = PoiDetails(summary = poi.description, imageUrl = poi.photoUrl)

    private fun articleDetails(article: PoiArticle, poi: PointOfInterest): PoiDetails = PoiDetails(
        summary = Excerpt.of(article.introduction, limits.summaryMaxChars, limits.summaryMaxParagraphs)
            ?: article.shortDescription?.replaceFirstChar { it.titlecase() }
            ?: poi.description,
        history = historyChapters(article.history),
        imageUrl = article.imageUrl ?: poi.photoUrl,
        // I crediti riguardano la foto della voce: non vanno attribuiti alla foto del provider.
        imageCredit = article.imageCredit?.takeIf { article.imageUrl != null },
        sourceUrl = article.url,
        language = article.language,
    )

    private fun historyChapters(sections: List<ArticleSection>): List<HistoryChapter> {
        val withText = sections.filter { Excerpt.hasReadableText(it.paragraphs) }
        if (withText.size == 1) {
            val story = Excerpt.of(withText.single().paragraphs, limits.singleStoryMaxChars, limits.singleStoryMaxParagraphs)
            return listOfNotNull(story?.let { HistoryChapter(title = null, text = it) })
        }
        return withText.take(limits.maxChapters).mapNotNull { section ->
            Excerpt.of(section.paragraphs, limits.chapterMaxChars, maxParagraphs = 1)?.let { HistoryChapter(section.title, it) }
        }
    }
}
