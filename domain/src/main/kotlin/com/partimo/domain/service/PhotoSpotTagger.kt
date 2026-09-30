package com.partimo.domain.service

import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.model.poi.PointOfInterest

/**
 * Assegna ai POI le etichette fotografiche (Instagrammabile, Panoramico, Tramonto, Chicca nascosta)
 * combinando categoria, parole chiave multilingua e popolarità (valutazione + numero di recensioni).
 */
class PhotoSpotTagger(private val thresholds: Thresholds = Thresholds()) {

    data class Thresholds(
        /** Un luogo fotogenico molto popolare è considerato "instagrammabile". */
        val popularMinRating: Double = 4.5,
        val popularMinReviews: Int = 1_000,
        /** Chicca nascosta: ottime valutazioni ma ancora poco affollata. */
        val hiddenGemMinRating: Double = 4.6,
        val hiddenGemReviews: IntRange = 50..1_000,
    )

    fun tag(poi: PointOfInterest): PointOfInterest = poi.copy(tags = poi.tags + inferTags(poi))

    fun inferTags(poi: PointOfInterest): Set<PoiTag> {
        val text = poi.searchableText
        val rating = poi.rating ?: 0.0
        val reviews = poi.reviewCount ?: 0
        val tags = mutableSetOf<PoiTag>()

        val panoramic = poi.category == PoiCategory.VIEWPOINT || text.containsAnyWordPrefix(PANORAMIC_KEYWORDS)
        if (panoramic) tags += PoiTag.PANORAMIC

        val popularPhotoSpot = poi.category in PHOTOGENIC_CATEGORIES &&
            rating >= thresholds.popularMinRating &&
            reviews >= thresholds.popularMinReviews
        if (panoramic || popularPhotoSpot || text.containsAnyWordPrefix(PHOTOGENIC_KEYWORDS)) {
            tags += PoiTag.INSTAGRAMMABLE
        }

        val sunsetFriendly = panoramic || poi.category == PoiCategory.BEACH || text.containsAnyWordPrefix(SUNSET_KEYWORDS)
        if (!poi.isIndoor && sunsetFriendly) tags += PoiTag.SUNSET_SPOT

        if (rating >= thresholds.hiddenGemMinRating && reviews in thresholds.hiddenGemReviews) {
            tags += PoiTag.HIDDEN_GEM
        }
        return tags
    }

    companion object {
        private val PHOTOGENIC_CATEGORIES = setOf(
            PoiCategory.VIEWPOINT,
            PoiCategory.MONUMENT,
            PoiCategory.BEACH,
            PoiCategory.NEIGHBORHOOD,
            PoiCategory.PARK,
            PoiCategory.SEASONAL_EVENT,
            PoiCategory.RELIGIOUS_SITE,
        )

        val PANORAMIC_KEYWORDS = listOf(
            "panoram", "belvedere", "skyline", "rooftop", "terrazza", "terrace", "torre", "tower", "turm",
            "observation", "osservatorio", "vista", "viewpoint", "collina", "riesenrad", "funivia", "cable car",
        )

        val PHOTOGENIC_KEYWORDS = listOf(
            "murales", "street art", "colorat", "colorful", "colourful", "iconic", "iconico", "fontana",
            "fountain", "ponte", "bridge", "hundertwasser", "castello", "castle", "palazzo", "palace", "schloss",
            "giardin", "garden",
        )

        val SUNSET_KEYWORDS = listOf("tramonto", "sunset", "lungomare", "waterfront", "spiaggia", "beach")
    }
}
