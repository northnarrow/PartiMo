package com.partimo.data.remote.wikipedia

import com.partimo.domain.model.poi.PoiCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Descrizioni reali di Wikipedia (it, en) e categoria attesa; `null` = voce da scartare. */
class WikipediaPlaceClassifierTest {

    private fun assertCategory(expected: PoiCategory?, title: String, description: String?) {
        assertEquals(expected, WikipediaPlaceClassifier.classify(title, description), "«$title» – «$description»")
    }

    @Test
    fun `riconosce i luoghi da visitare`() {
        assertCategory(PoiCategory.MONUMENT, "Colosseo", "antico anfiteatro romano a Roma")
        assertCategory(PoiCategory.RELIGIOUS_SITE, "Pantheon (Roma)", "tempio della Roma antica e basilica minore cattolica")
        assertCategory(PoiCategory.MONUMENT, "Fontana di Trevi", "fontana barocca di Roma, Italia")
        assertCategory(PoiCategory.NEIGHBORHOOD, "Piazza Navona", "piazza barocca di Roma, sede dello Stadio di Domiziano")
        assertCategory(PoiCategory.MUSEUM, "Musei Capitolini", "museo civico della città di Roma, Italia")
        assertCategory(PoiCategory.MONUMENT, "Castel Sant'Angelo", "castello e museo a Roma")
        assertCategory(PoiCategory.MONUMENT, "Foro Romano", "principale sito archeologico dell'antica Roma e Patrimonio UNESCO")
        assertCategory(PoiCategory.NEIGHBORHOOD, "Trastevere", "13º rione di Roma")
        assertCategory(PoiCategory.VIEWPOINT, "Gianicolo", "colle di Roma")
        assertCategory(PoiCategory.PARK, "Prater", "parco pubblico di Vienna")
        assertCategory(PoiCategory.ATTRACTION, "Wiener Staatsoper", "teatro d'opera")
        assertCategory(PoiCategory.MONUMENT, "Quartiere Coppedè", "complesso architettonico nel quartiere Trieste di Roma")
        assertCategory(PoiCategory.MONUMENT, "Ex ospedale di San Rocco", "edificio storico di Matera, Italia")
        assertCategory(PoiCategory.NEIGHBORHOOD, "Rossio", "Town square in Lisbon, Portugal")
        assertCategory(PoiCategory.ATTRACTION, "Ascensor da Glória", "Funicular railway in Lisbon, Portugal")
        assertCategory(PoiCategory.MONUMENT, "Rua Augusta Arch", "Arch-like historical bulinding in Lisbon, Portugal")
        assertCategory(PoiCategory.RELIGIOUS_SITE, "Lisbon Cathedral", "Roman Catholic cathedral located in Lisbon, Portugal")
    }

    @Test
    fun `a parità di posizione vince la parola chiave più lunga`() {
        assertCategory(PoiCategory.MONUMENT, "Parco archeologico dell'Appia Antica", "parco archeologico di Roma")
        assertCategory(PoiCategory.ATTRACTION, "Bioparco", "giardino zoologico di Roma")
        assertCategory(null, "PalaSassi", "palazzetto dello sport di Matera")
        assertCategory(PoiCategory.SKI_AREA, "Cervinia", "stazione sciistica della Valle d'Aosta")
    }

    @Test
    fun `scarta città, enti, trasporti, stadi ed eventi`() {
        assertCategory(null, "Roma", "capitale d'Italia, capoluogo dell'omonima città metropolitana e della regione Lazio")
        assertCategory(null, "Città del Vaticano", "stato dell'Europa meridionale")
        assertCategory(null, "Lazio", "regione italiana a statuto ordinario")
        assertCategory(null, "Pontificia Università Gregoriana", "Università pontificia di Roma")
        assertCategory(null, "Consiglio Nazionale delle Ricerche", "ente pubblico di ricerca italiano")
        assertCategory(null, "Stazione di Roma Termini", "stazione ferroviaria italiana")
        assertCategory(null, "Stadio Olimpico (Roma)", "impianto sportivo polivalente di Roma")
        assertCategory(null, "Caso Moro", "sequestro e uccisione di Aldo Moro da parte delle Brigate Rosse")
        assertCategory(null, "Delitto Casati Stampa", "evento di cronaca nera")
        assertCategory(null, "Hotel Sacher", "albergo a Vienna")
        assertCategory(null, "Lisbon", "Capital and largest city of Portugal")
        assertCategory(null, "Siege of Lisbon", "1147 Second Crusade battle")
        assertCategory(null, "Banco de Portugal", "Central Bank of Portugal")
        assertCategory(null, "PIDE", "1933–1969 Portuguese secret police force")
    }

    @Test
    fun `scarta le opere custodite nei musei e i luoghi che non esistono più`() {
        assertCategory(null, "Pietà vaticana", "scultura di Michelangelo Buonarroti nella basilica di San Pietro in Vaticano")
        assertCategory(null, "Giudizio universale (Michelangelo)", "affresco di Michelangelo nella Cappella Sistina")
        assertCategory(null, "Stadio Nazionale", "stadio scomparso di Roma")
        assertCategory(null, "Arco di Tiberio", "arco trionfale scomparso, sito nel Foro romano")
        assertCategory(null, "Ribeira Palace", "Former royal palace in Lisbon (1498–1755)")
    }

    @Test
    fun `se la descrizione non basta decide il titolo`() {
        assertCategory(null, "Attentato di via Rasella", "azione partigiana da parte della resistenza romana")
        assertCategory(PoiCategory.ATTRACTION, "Hundertwasserhaus", "complesso di case popolari a Vienna")
        assertCategory(PoiCategory.RELIGIOUS_SITE, "Chiesa dei Minoriti", null)
        assertCategory(PoiCategory.MONUMENT, "Palazzo Kinsky (Vienna)", "")
        assertNull(WikipediaPlaceClassifier.classify("Bundesversammlung (Austria)", null), "Senza descrizione né indizi nel titolo si scarta")
    }

    @Test
    fun `le parole brevi valgono solo come parole intere`() {
        assertCategory(PoiCategory.NEIGHBORHOOD, "Via del Corso", "via di Roma")
        assertTrue(WikipediaPlaceClassifier.classify("Viadotto", "viadotto autostradale") == null, "«autostrad» scarta il viadotto")
        assertFalse(WikipediaPlaceClassifier.classify("Portico d'Ottavia", "portico monumentale di Roma") == null, "«porto» non è «portico»")
    }
}
