package com.partimo.data.remote.wikipedia

import com.partimo.domain.model.poi.PoiCategory

/**
 * Riconosce, dalla descrizione breve di una voce (es. "antico anfiteatro romano a Roma"), se si
 * tratta di un luogo da visitare e di che tipo.
 *
 * Le voci con coordinate non sono tutte luoghi turistici: ci sono la città stessa, enti, università,
 * stazioni, stadi, eventi storici ed edifici scomparsi, che vengono scartati. Le descrizioni iniziano
 * quasi sempre con la classe del luogo ("chiesa di…", "museo civico…"), quindi vince la parola chiave
 * che compare per prima; a parità di posizione vince la più lunga ("parco archeologico" batte "parco").
 * Parole chiave in italiano e inglese, le lingue di Wikipedia usate dall'app.
 */
internal object WikipediaPlaceClassifier {

    /** Lingue di cui il classificatore conosce le parole chiave. */
    val SUPPORTED_LANGUAGES: Set<String> = setOf("it", "en")

    /**
     * Categoria del luogo, oppure `null` se la voce non descrive un luogo da visitare.
     *
     * Se la descrizione non basta si guarda il titolo ("Attentato di via Rasella"). Una voce senza
     * descrizione si tiene solo se il titolo indica chiaramente un luogo ("Chiesa dei Minoriti"):
     * senza indizi potrebbe essere un'opera d'arte, un ente o un quartiere amministrativo.
     */
    fun classify(title: String, description: String?): PoiCategory? {
        val descriptionText = description?.takeIf { it.isNotBlank() }?.lowercase()
        val titleText = title.lowercase()
        if (listOfNotNull(descriptionText, titleText).any { text -> GONE.any { it.containsMatchIn(text) } }) return null
        val titleRule = firstRule(titleText)
        if (descriptionText == null) return titleRule?.category
        val descriptionRule = firstRule(descriptionText)
        return when {
            descriptionRule != null -> descriptionRule.category
            titleRule != null -> titleRule.category
            else -> PoiCategory.ATTRACTION
        }
    }

    private fun firstRule(text: String): Rule? = RULES
        .mapNotNull { rule -> rule.pattern.find(text)?.let { match -> match.range.first to rule } }
        .minWithOrNull(compareBy<Pair<Int, Rule>> { it.first }.thenByDescending { it.second.keyword.length })
        ?.second

    /** Regola: parola chiave (a inizio parola) → categoria; `null` = voce da scartare. */
    private class Rule(val keyword: String, val category: PoiCategory?, wholeWord: Boolean) {
        val pattern = Regex("(?<!\\p{L})" + Regex.escape(keyword) + if (wholeWord) "(?!\\p{L})" else "")
    }

    private class RuleSet {
        val rules = mutableListOf<Rule>()

        /** Parole chiave confrontate come prefisso ("chies" → chiesa, chiese). */
        fun prefix(category: PoiCategory?, vararg keywords: String) {
            keywords.forEach { rules += Rule(it, category, wholeWord = false) }
        }

        /** Parole chiave brevi o ambigue confrontate come parola intera ("via", "porto", "state"). */
        fun word(category: PoiCategory?, vararg keywords: String) {
            keywords.forEach { rules += Rule(it, category, wholeWord = true) }
        }
    }

    /** Luoghi che non esistono più (o non ancora): non si possono visitare, qualunque sia la loro categoria. */
    private val GONE: List<Regex> = listOf(
        "scompars", "demolit", "distrutt", "abbattut", "non più esistent", "perdut",
        "former ", "demolished", "destroyed", "defunct", "no longer", "proposed", "planned",
        "under construction", "never built",
    ).map { Regex("(?<!\\p{L})" + Regex.escape(it)) }

    private val RULES: List<Rule> = RuleSet().apply {
        // ---- Da scartare: territori, enti, trasporti, sport, eventi, attività commerciali ----
        prefix(
            null,
            "città", "capitale", "capoluogo", "comune", "frazione", "località", "region", "provinci", "nazione",
            "contea", "distrett", "circoscrizion", "suddivision", "diocesi", "arcidiocesi", "prefettura", "cantone",
            "dipartimento", "universit", "ateneo", "politecnic", "accademia", "istitut", "scuol", "liceo",
            "consiglio", "ministero", "agenzia", "autorità", "tribunale", "ospedal", "policlinic", "clinica",
            "azienda", "società", "impresa", "compagnia", "partito", "sindacat", "associazion", "fondazion",
            "organizzazion", "biblioteca", "archivio", "ambasciat", "consolato", "quartier generale", "caserma",
            "stazion", "fermata", "aeroport", "eliporto", "interporto", "metropolitan", "ferrovi", "tranvi",
            "autostrad", "tangenzial", "strada statale", "strada provinciale", "svincolo", "galleria stradale",
            "galleria ferroviaria", "parcheggi", "capolinea", "stadio", "impianto sportivo", "palazzetto", "palasport",
            "ippodromo", "autodromo", "velodromo", "circuito", "squadra", "palestra", "piscina", "eccidio", "strage",
            "massacr", "attentat", "assedi", "battaglia", "sequestr", "rapiment", "omicidi", "incendi", "terremot",
            "alluvion", "esplosion", "bombardament", "rivolt", "insurrezion", "sommossa", "manifestazion", "evento",
            "festival", "concilio", "conclave", "trattato", "incidente", "naufragi", "disastro", "epidemi", "guerra",
            "elezion", "edizione", "competizion", "torneo", "gran premio", "maratona", "concerto", "rivoluzion",
            "occupazion", "sciopero", "cerimoni", "albergo", "ristorant", "negozio", "supermercat", "edificio residenziale",
            "complesso residenziale", "condominio", "centrale elettrica", "centrale termoelettrica", "fabbrica",
            "stabiliment", "fiume", "torrente", "famiglia", "dinastia", "delitto", "cronaca", "ufficio", "circondario",
            "regime", "repubblica", "federazione", "polizia", "gendarmeria", "forze armate", "patriarcato", "emittente",
            // Opere d'arte custodite in musei e chiese: si visita il luogo che le ospita.
            "scultur", "affresc", "dipint", "pala d'altare", "polittico", "trittico", "arazz", "manoscritt",
        )
        word(
            null,
            "stato", "paese", "municipio", "ente", "enti", "porto", "linea", "club", "sacco", "caso", "gara", "partita",
            "mostra", "processo", "hotel", "rio", "quadro", "tela", "regno", "impero",
        )
        prefix(
            null,
            "city", "town", "village", "capital", "municipalit", "commune", "county", "province", "region", "country",
            "district", "borough", "prefecture", "metropolitan area", "suburb", "civil parish", "universit", "college",
            "school", "academy", "institut", "hospital", "company", "corporation", "agency", "ministry", "embassy",
            "consulate", "headquarters", "library", "archive", "station", "airport", "railway", "subway", "highway",
            "motorway", "expressway", "tunnel", "interchange", "stadium", "arena", "sports venue", "racecourse",
            "velodrome", "massacre", "attack", "bombing", "battle", "siege", "earthquake", "flood", "explosion",
            "riot", "protest", "event", "festival", "concert", "exhibition", "treaty", "election", "murder",
            "assassination", "kidnapping", "disaster", "accident", "shipwreck", "hotel", "restaurant", "office building",
            "residential", "apartment", "housing", "power station", "power plant", "factory", "river", "stream", "creek",
            "parking", "car park", "sculpture", "painting", "fresco", "tapestry", "manuscript", "united nations",
            "regime", "kingdom", "republic", "governing body", "federation", "derailment", "police", "gendarmerie",
            "armed forces", "diocese", "archdiocese", "patriarchate", "retailer", "broadcast",
        )
        word(null, "state", "ward", "bank", "port", "metro", "line", "road", "team", "club", "fire", "shop", "store", "crash", "parish", "office", "empire")

        // ---- Luoghi da visitare ----
        prefix(PoiCategory.MUSEUM, "muse", "pinacotec", "galleria d'arte", "galleria nazionale", "galleria civica", "gipsotec", "casa museo", "planetari", "collezion")
        prefix(PoiCategory.MUSEUM, "museum", "art gallery", "gallery", "planetarium")

        prefix(
            PoiCategory.RELIGIOUS_SITE,
            "chies", "basilic", "cattedral", "duomo", "concattedral", "santuari", "abbazi", "monaster", "convent",
            "cappell", "oratori", "battister", "pieve", "moschea", "sinagog", "tempio", "templi", "pagod", "stupa",
            "luogo di culto", "edificio religioso", "certosa", "eremo", "collegiata", "parrocchi", "cripta",
        )
        prefix(
            PoiCategory.RELIGIOUS_SITE,
            "church", "cathedral", "basilica", "chapel", "abbey", "monastery", "convent", "priory", "minster",
            "mosque", "synagogue", "temple", "shrine", "pagoda", "place of worship", "parish church", "oratory",
        )

        prefix(PoiCategory.PARK, "parco", "parchi", "giardin", "orto botanico", "riserva natural", "oasi", "bosco", "foresta", "pineta", "lago", "laghi", "area verde", "villa comunale")
        prefix(PoiCategory.PARK, "park", "state park", "city park", "garden", "botanical garden", "nature reserve", "forest", "lake")
        word(PoiCategory.PARK, "wood", "woods")

        prefix(PoiCategory.BEACH, "spiagg", "caletta", "litorale", "beach")
        word(PoiCategory.BEACH, "lido", "baia", "bay")

        prefix(PoiCategory.MARKET, "mercat", "bazar", "market", "bazaar", "souk")
        word(PoiCategory.MARKET, "fiera", "suq")

        prefix(PoiCategory.SHOPPING, "centro commercial", "grande magazzin", "galleria commercial", "outlet", "shopping", "department store")
        word(PoiCategory.SHOPPING, "mall")

        prefix(PoiCategory.SKI_AREA, "stazione sciistic", "comprensorio sciistic", "pista da sci", "impianti sciistici", "ski resort", "ski area")

        prefix(
            PoiCategory.VIEWPOINT,
            "belveder", "terrazza panoramic", "punto panoramic", "montagn", "collin", "altura", "promontori", "vulcan",
            "viewpoint", "lookout", "observation deck", "observation tower", "mountain", "cliff", "volcano", "promontory",
        )
        word(PoiCategory.VIEWPOINT, "monte", "colle", "colli", "rupe", "hill", "hills", "mount", "peak")

        prefix(
            PoiCategory.NEIGHBORHOOD,
            "rione", "rioni", "quartier", "sestiere", "centro storico", "città vecchia", "piazz", "viale", "lungomar",
            "lungofium", "lungotever", "lungolago", "lungarno", "passeggiata", "canali", "vicolo", "zona pedonale", "zona urbanistic",
            "neighbourhood", "neighborhood", "old town", "historic district", "historic centre", "historic center",
            "plaza", "avenue", "boulevard", "promenade", "waterfront", "canal", "alley", "town square", "city square",
            "city centre", "city center",
        )
        word(PoiCategory.NEIGHBORHOOD, "borgo", "via", "strada", "corso", "canale", "quarter", "square", "street")

        prefix(
            PoiCategory.MONUMENT,
            "monument", "memorial", "mausole", "sepolcr", "tomba", "necropol", "cimiter", "catacomb", "palazz",
            "castell", "fortezz", "fortificazion", "cittadella", "torre", "torri", "campanil", "obelisc", "colonna",
            "cinta murari", "bastion", "fontan", "statua", "statue", "scultura pubblica", "scultura monumental",
            "anfiteatr", "teatro romano", "sito archeologic", "area archeologic", "parco archeologic", "zona archeologic",
            "scavi", "rovin", "rudere", "acquedott", "complesso monumental", "complesso architettonic", "edificio storico",
            "residenz", "reggia", "villa romana", "mulino", "altare", "public sculpture", "monumental sculpture",
            "fountain", "palace", "castle", "fortress", "citadel", "tower", "bridge", "ruins",
            "archaeological", "amphitheat", "obelisk", "mausoleum", "tomb", "cemetery", "necropolis", "catacomb",
            "aqueduct", "lighthouse", "landmark", "historic building", "triumphal arch", "town hall", "city hall",
            "city gate", "city wall",
        )
        word(
            PoiCategory.MONUMENT,
            "villa", "forte", "rocca", "arco", "archi", "porta", "mura", "ponte", "ponti", "circo", "terme", "foro",
            "fori", "resti", "faro", "domus", "fort", "gate", "wall", "walls", "arch", "column",
        )

        prefix(
            PoiCategory.ATTRACTION,
            "giardino zoologico", "bioparco", "acquari", "parco divertimenti", "parco a tema", "luna park", "ruota panoramica",
            "teatro", "teatri", "auditorium", "sala da concerto", "grattaciel", "funivi", "funicolar", "teleferic",
            "aquarium", "amusement park", "theme park", "theatre", "theater", "opera house", "concert hall",
            "skyscraper", "ferris wheel", "observation wheel", "cable car", "funicular",
            // Classi generiche: evitano che un'area amministrativa citata dopo ("in Lisbon District") scarti la voce.
            "edifici", "isola", "isole", "building", "island",
        )
        word(PoiCategory.ATTRACTION, "zoo")
    }.rules
}
