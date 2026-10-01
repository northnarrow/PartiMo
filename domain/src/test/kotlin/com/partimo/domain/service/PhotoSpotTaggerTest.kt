package com.partimo.domain.service

import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.testing.TestData.poi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoSpotTaggerTest {

    private val tagger = PhotoSpotTagger()

    @Test
    fun `un belvedere è panoramico, instagrammabile e adatto al tramonto`() {
        val tags = tagger.inferTags(poi("kahlenberg", "Kahlenberg", category = PoiCategory.VIEWPOINT))

        assertEquals(setOf(PoiTag.PANORAMIC, PoiTag.INSTAGRAMMABLE, PoiTag.SUNSET_SPOT), tags)
    }

    @Test
    fun `le parole chiave riconoscono i luoghi panoramici`() {
        val tower = poi("tower", "Donauturm", category = PoiCategory.ATTRACTION, description = "Torre panoramica sul Danubio")

        assertTrue(PoiTag.PANORAMIC in tagger.inferTags(tower))
    }

    @Test
    fun `un museo senza segnali fotografici non riceve etichette`() {
        val museum = poi("nhm", "Museo di Storia Naturale", category = PoiCategory.MUSEUM, rating = 4.4, reviewCount = 5_000)

        assertTrue(tagger.inferTags(museum).isEmpty())
    }

    @Test
    fun `ottime valutazioni con poche recensioni indicano una chicca nascosta`() {
        val garden = poi("garden", "Giardino segreto", category = PoiCategory.PARK, rating = 4.8, reviewCount = 120)

        val tags = tagger.inferTags(garden)

        assertTrue(PoiTag.HIDDEN_GEM in tags)
        assertTrue(PoiTag.INSTAGRAMMABLE in tags)
    }

    @Test
    fun `un monumento famoso senza recensioni è comunque instagrammabile`() {
        val famous = poi("colosseo", "Colosseo", category = PoiCategory.MONUMENT, rating = null, reviewCount = null, popularity = 0.9)
        val lesserKnown = famous.copy(id = "arco", name = "Arco di Druso", popularity = 0.3)

        assertTrue(PoiTag.INSTAGRAMMABLE in tagger.inferTags(famous))
        assertTrue(tagger.inferTags(lesserKnown).isEmpty())
    }

    @Test
    fun `un luogo al chiuso non è uno spot per il tramonto`() {
        val indoorView = poi("sky-bar", "Sky bar panoramico", category = PoiCategory.OTHER, isIndoor = true)

        val tags = tagger.inferTags(indoorView)

        assertTrue(PoiTag.PANORAMIC in tags)
        assertFalse(PoiTag.SUNSET_SPOT in tags)
    }

    @Test
    fun `tag conserva le etichette già presenti`() {
        val tagged = tagger.tag(poi("x", "Stephansdom", tags = setOf(PoiTag.HIDDEN_GEM)))

        assertTrue(PoiTag.HIDDEN_GEM in tagged.tags)
    }
}
