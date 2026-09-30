package com.partimo.data.local

import com.partimo.data.network.Fetched
import com.partimo.data.source.DestinationCatalogDataSource
import com.partimo.domain.common.DataOrigin
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.place.CatalogDestination
import com.partimo.domain.model.place.CityPlace
import com.partimo.domain.model.place.TravelExperience
import com.partimo.domain.model.place.TravelTheme
import com.partimo.domain.model.place.TravelTheme.BEACH
import com.partimo.domain.model.place.TravelTheme.BLOSSOM
import com.partimo.domain.model.place.TravelTheme.CHRISTMAS_MARKETS
import com.partimo.domain.model.place.TravelTheme.CULTURE
import com.partimo.domain.model.place.TravelTheme.FESTIVAL
import com.partimo.domain.model.place.TravelTheme.FOLIAGE
import com.partimo.domain.model.place.TravelTheme.FOOD
import com.partimo.domain.model.place.TravelTheme.NATURE
import com.partimo.domain.model.place.TravelTheme.NIGHTLIFE
import com.partimo.domain.model.place.TravelTheme.NORTHERN_LIGHTS
import com.partimo.domain.model.place.TravelTheme.WARM_ESCAPE
import com.partimo.domain.model.place.TravelTheme.WINTER_SPORTS
import com.partimo.domain.service.SeasonalCalendar
import java.time.Month
import java.time.Month.APRIL
import java.time.Month.AUGUST
import java.time.Month.DECEMBER
import java.time.Month.FEBRUARY
import java.time.Month.JANUARY
import java.time.Month.JULY
import java.time.Month.JUNE
import java.time.Month.MARCH
import java.time.Month.MAY
import java.time.Month.NOVEMBER
import java.time.Month.OCTOBER
import java.time.Month.SEPTEMBER
import java.time.ZoneId

/**
 * Catalogo curato di mete in tutto il mondo per il motore "Consigliami": per ogni città i mesi con
 * clima piacevole e le esperienze stagionali (mercatini, aurora boreale, fioriture, foliage, mare...).
 * È conoscenza editoriale inclusa nell'app: non richiede rete né chiavi API.
 */
class CuratedDestinationCatalog : DestinationCatalogDataSource {

    override suspend fun destinations(): Fetched<List<CatalogDestination>> = Fetched(DESTINATIONS, DataOrigin.LOCAL)

    internal companion object {

        private fun months(vararg values: Month): Set<Month> = values.toSet()

        private fun between(from: Month, to: Month): Set<Month> = SeasonalCalendar.monthRange(from, to)

        private fun seasonal(theme: TravelTheme, monthsSet: Set<Month>) = TravelExperience(theme, monthsSet)

        private fun yearRound(theme: TravelTheme) = TravelExperience(theme)

        @Suppress("LongParameterList")
        private fun destination(
            id: String,
            name: String,
            country: String,
            countryCode: String,
            latitude: Double,
            longitude: Double,
            timeZone: String,
            pleasantMonths: Set<Month>,
            tagline: String,
            vararg experiences: TravelExperience,
        ) = CatalogDestination(
            city = CityPlace(
                id = "catalog:$id",
                name = name,
                countryCode = countryCode,
                location = GeoPoint(latitude, longitude),
                timeZone = ZoneId.of(timeZone),
                country = country,
            ),
            pleasantMonths = pleasantMonths,
            experiences = experiences.toList(),
            tagline = tagline,
        )

        val DESTINATIONS: List<CatalogDestination> = listOf(
            // ---- Europa -------------------------------------------------------------------
            destination(
                "vienna", "Vienna", "Austria", "AT", 48.2082, 16.3738, "Europe/Vienna",
                months(APRIL, MAY, JUNE, SEPTEMBER, OCTOBER),
                "Palazzi imperiali, caffè storici e i mercatini di Natale più famosi d'Europa.",
                seasonal(CHRISTMAS_MARKETS, months(NOVEMBER, DECEMBER)), yearRound(CULTURE), yearRound(FOOD),
            ),
            destination(
                "praga", "Praga", "Repubblica Ceca", "CZ", 50.0755, 14.4378, "Europe/Prague",
                months(MAY, JUNE, SEPTEMBER),
                "Ponti gotici, birrerie storiche e un centro medievale da fiaba.",
                seasonal(CHRISTMAS_MARKETS, months(NOVEMBER, DECEMBER)), yearRound(CULTURE), yearRound(NIGHTLIFE),
            ),
            destination(
                "strasburgo", "Strasburgo", "Francia", "FR", 48.5734, 7.7521, "Europe/Paris",
                months(MAY, JUNE, SEPTEMBER),
                "La capitale del Natale: case a graticcio e il mercatino più antico di Francia.",
                seasonal(CHRISTMAS_MARKETS, months(NOVEMBER, DECEMBER)), yearRound(FOOD), yearRound(CULTURE),
            ),
            destination(
                "rovaniemi", "Rovaniemi", "Finlandia", "FI", 66.5039, 25.7294, "Europe/Helsinki",
                months(JUNE, JULY, AUGUST),
                "Il villaggio di Babbo Natale, slitte trainate dagli husky e l'aurora boreale.",
                seasonal(CHRISTMAS_MARKETS, months(DECEMBER)), seasonal(NORTHERN_LIGHTS, between(SEPTEMBER, MARCH)),
                seasonal(WINTER_SPORTS, between(DECEMBER, MARCH)), yearRound(NATURE),
            ),
            destination(
                "tromso", "Tromsø", "Norvegia", "NO", 69.6496, 18.9560, "Europe/Oslo",
                months(JUNE, JULY, AUGUST),
                "Caccia all'aurora boreale tra fiordi innevati e il sole di mezzanotte d'estate.",
                seasonal(NORTHERN_LIGHTS, between(SEPTEMBER, MARCH)), seasonal(WINTER_SPORTS, between(DECEMBER, APRIL)),
                yearRound(NATURE),
            ),
            destination(
                "reykjavik", "Reykjavík", "Islanda", "IS", 64.1466, -21.9426, "Atlantic/Reykjavik",
                months(JUNE, JULY, AUGUST),
                "Geyser, cascate e lagune termali: la porta d'ingresso dell'Islanda.",
                seasonal(NORTHERN_LIGHTS, between(SEPTEMBER, MARCH)), yearRound(NATURE),
            ),
            destination(
                "innsbruck", "Innsbruck", "Austria", "AT", 47.2692, 11.4041, "Europe/Vienna",
                months(JUNE, JULY, AUGUST, SEPTEMBER),
                "Piste da sci a pochi minuti dal centro storico e mercatini alpini.",
                seasonal(WINTER_SPORTS, between(DECEMBER, MARCH)), seasonal(CHRISTMAS_MARKETS, months(NOVEMBER, DECEMBER)),
                seasonal(NATURE, between(JUNE, SEPTEMBER)),
            ),
            destination(
                "lisbona", "Lisbona", "Portogallo", "PT", 38.7223, -9.1393, "Europe/Lisbon",
                months(MARCH, APRIL, MAY, JUNE, SEPTEMBER, OCTOBER),
                "Tram gialli, belvederi sul Tago e pastéis de nata appena sfornati.",
                seasonal(BEACH, between(JUNE, SEPTEMBER)), yearRound(FOOD), yearRound(CULTURE),
            ),
            destination(
                "porto", "Porto", "Portogallo", "PT", 41.1579, -8.6291, "Europe/Lisbon",
                between(MAY, SEPTEMBER),
                "Cantine di vino Porto, azulejos e tramonti sul Douro.",
                yearRound(FOOD), yearRound(CULTURE),
            ),
            destination(
                "barcellona", "Barcellona", "Spagna", "ES", 41.3874, 2.1686, "Europe/Madrid",
                months(APRIL, MAY, JUNE, SEPTEMBER, OCTOBER),
                "Gaudí, spiagge urbane e tapas fino a tarda notte.",
                seasonal(BEACH, between(JUNE, SEPTEMBER)), yearRound(CULTURE), yearRound(NIGHTLIFE), yearRound(FOOD),
            ),
            destination(
                "siviglia", "Siviglia", "Spagna", "ES", 37.3891, -5.9845, "Europe/Madrid",
                months(MARCH, APRIL, MAY, OCTOBER, NOVEMBER),
                "Flamenco, patios fioriti e l'Alcázar: l'anima dell'Andalusia.",
                seasonal(FESTIVAL, months(APRIL)), seasonal(BLOSSOM, months(MARCH, APRIL)), yearRound(CULTURE),
            ),
            destination(
                "roma", "Roma", "Italia", "IT", 41.9028, 12.4964, "Europe/Rome",
                months(APRIL, MAY, JUNE, SEPTEMBER, OCTOBER),
                "Duemila anni di storia a cielo aperto, tra fontane, piazze e trattorie.",
                yearRound(CULTURE), yearRound(FOOD),
            ),
            destination(
                "amsterdam", "Amsterdam", "Paesi Bassi", "NL", 52.3676, 4.9041, "Europe/Amsterdam",
                between(MAY, SEPTEMBER),
                "Canali, musei e, in primavera, i campi di tulipani in fiore.",
                seasonal(BLOSSOM, months(APRIL)), yearRound(CULTURE), yearRound(NIGHTLIFE),
            ),
            destination(
                "parigi", "Parigi", "Francia", "FR", 48.8566, 2.3522, "Europe/Paris",
                months(APRIL, MAY, JUNE, SEPTEMBER),
                "Musei leggendari, bistrot e passeggiate lungo la Senna.",
                seasonal(CHRISTMAS_MARKETS, months(DECEMBER)), yearRound(CULTURE), yearRound(FOOD),
            ),
            destination(
                "londra", "Londra", "Regno Unito", "GB", 51.5072, -0.1276, "Europe/London",
                between(MAY, SEPTEMBER),
                "Musei gratuiti, mercati vintage e i teatri del West End.",
                seasonal(CHRISTMAS_MARKETS, months(NOVEMBER, DECEMBER)), yearRound(CULTURE), yearRound(NIGHTLIFE),
            ),
            destination(
                "edimburgo", "Edimburgo", "Regno Unito", "GB", 55.9533, -3.1883, "Europe/London",
                months(JUNE, JULY, AUGUST),
                "Castelli, vicoli medievali e il festival d'arte più grande del mondo in agosto.",
                seasonal(FESTIVAL, months(AUGUST)), seasonal(CHRISTMAS_MARKETS, months(DECEMBER)), yearRound(CULTURE),
            ),
            destination(
                "copenaghen", "Copenaghen", "Danimarca", "DK", 55.6761, 12.5683, "Europe/Copenhagen",
                months(JUNE, JULY, AUGUST),
                "Design, biciclette e i Giardini di Tivoli illuminati a Natale.",
                seasonal(CHRISTMAS_MARKETS, months(NOVEMBER, DECEMBER)), yearRound(FOOD), yearRound(CULTURE),
            ),
            destination(
                "budapest", "Budapest", "Ungheria", "HU", 47.4979, 19.0402, "Europe/Budapest",
                months(MAY, JUNE, SEPTEMBER),
                "Terme storiche, ruin bar e il Danubio illuminato di sera.",
                seasonal(CHRISTMAS_MARKETS, months(NOVEMBER, DECEMBER)), yearRound(CULTURE), yearRound(NIGHTLIFE),
            ),
            destination(
                "cracovia", "Cracovia", "Polonia", "PL", 50.0647, 19.9450, "Europe/Warsaw",
                months(MAY, JUNE, SEPTEMBER),
                "La piazza medievale più grande d'Europa e un mercatino di Natale incantevole.",
                seasonal(CHRISTMAS_MARKETS, months(NOVEMBER, DECEMBER)), yearRound(CULTURE),
            ),
            destination(
                "atene", "Atene", "Grecia", "GR", 37.9838, 23.7275, "Europe/Athens",
                months(APRIL, MAY, JUNE, SEPTEMBER, OCTOBER),
                "L'Acropoli al tramonto e le isole a un'ora di traghetto.",
                seasonal(BEACH, between(JUNE, SEPTEMBER)), yearRound(CULTURE), yearRound(FOOD),
            ),
            destination(
                "santorini", "Santorini", "Grecia", "GR", 36.4166, 25.4322, "Europe/Athens",
                between(MAY, OCTOBER),
                "Case bianche, cupole blu e i tramonti più famosi dell'Egeo.",
                seasonal(BEACH, between(JUNE, SEPTEMBER)), yearRound(NATURE),
            ),
            destination(
                "dubrovnik", "Dubrovnik", "Croazia", "HR", 42.6507, 18.0944, "Europe/Zagreb",
                months(MAY, JUNE, SEPTEMBER, OCTOBER),
                "Mura medievali affacciate sul mare cristallino dell'Adriatico.",
                seasonal(BEACH, between(JUNE, SEPTEMBER)), yearRound(CULTURE),
            ),
            destination(
                "istanbul", "Istanbul", "Turchia", "TR", 41.0082, 28.9784, "Europe/Istanbul",
                months(APRIL, MAY, SEPTEMBER, OCTOBER),
                "Bazar, moschee e crociere sul Bosforo tra Europa e Asia.",
                seasonal(BLOSSOM, months(APRIL)), yearRound(CULTURE), yearRound(FOOD),
            ),
            // ---- Africa e Medio Oriente ---------------------------------------------------
            destination(
                "marrakech", "Marrakech", "Marocco", "MA", 31.6295, -7.9811, "Africa/Casablanca",
                months(MARCH, APRIL, MAY, OCTOBER, NOVEMBER),
                "Souk, riad e il fascino della piazza Jemaa el-Fna.",
                seasonal(WARM_ESCAPE, between(NOVEMBER, FEBRUARY)), yearRound(CULTURE), yearRound(FOOD),
            ),
            destination(
                "cairo", "Il Cairo", "Egitto", "EG", 30.0444, 31.2357, "Africa/Cairo",
                between(OCTOBER, APRIL),
                "Le piramidi di Giza e i tesori dei faraoni.",
                seasonal(WARM_ESCAPE, between(NOVEMBER, FEBRUARY)), yearRound(CULTURE),
            ),
            destination(
                "citta-del-capo", "Città del Capo", "Sudafrica", "ZA", -33.9249, 18.4241, "Africa/Johannesburg",
                between(NOVEMBER, MARCH),
                "Table Mountain, spiagge oceaniche e vigneti a un passo dalla città.",
                seasonal(BEACH, between(DECEMBER, MARCH)), yearRound(NATURE), yearRound(FOOD),
            ),
            destination(
                "dubai", "Dubai", "Emirati Arabi Uniti", "AE", 25.2048, 55.2708, "Asia/Dubai",
                between(NOVEMBER, MARCH),
                "Grattacieli da record, deserto e mare caldo anche in inverno.",
                seasonal(WARM_ESCAPE, between(NOVEMBER, MARCH)), seasonal(BEACH, between(NOVEMBER, APRIL)), yearRound(NIGHTLIFE),
            ),
            destination(
                "zanzibar", "Zanzibar", "Tanzania", "TZ", -6.1659, 39.2026, "Africa/Dar_es_Salaam",
                between(JUNE, OCTOBER) + between(DECEMBER, FEBRUARY),
                "Spiagge bianche, profumo di spezie e la storica Stone Town.",
                seasonal(BEACH, between(JUNE, OCTOBER) + between(DECEMBER, FEBRUARY)),
                seasonal(WARM_ESCAPE, between(DECEMBER, FEBRUARY)),
            ),
            // ---- Asia ---------------------------------------------------------------------
            destination(
                "tokyo", "Tokyo", "Giappone", "JP", 35.6762, 139.6503, "Asia/Tokyo",
                months(MARCH, APRIL, MAY, OCTOBER, NOVEMBER),
                "Templi, quartieri futuristici, ciliegi in fiore in primavera e aceri rossi in autunno.",
                seasonal(BLOSSOM, months(MARCH, APRIL)), seasonal(FOLIAGE, months(NOVEMBER)),
                yearRound(FOOD), yearRound(CULTURE), yearRound(NIGHTLIFE),
            ),
            destination(
                "kyoto", "Kyoto", "Giappone", "JP", 35.0116, 135.7681, "Asia/Tokyo",
                months(MARCH, APRIL, MAY, OCTOBER, NOVEMBER),
                "Oltre mille templi e giardini zen tra ciliegi e aceri rossi.",
                seasonal(BLOSSOM, months(MARCH, APRIL)), seasonal(FOLIAGE, months(NOVEMBER)), yearRound(CULTURE),
            ),
            destination(
                "seoul", "Seul", "Corea del Sud", "KR", 37.5665, 126.9780, "Asia/Seoul",
                months(APRIL, MAY, SEPTEMBER, OCTOBER),
                "Palazzi reali, street food e quartieri che non dormono mai.",
                seasonal(BLOSSOM, months(APRIL)), seasonal(FOLIAGE, months(OCTOBER, NOVEMBER)),
                yearRound(FOOD), yearRound(NIGHTLIFE),
            ),
            destination(
                "bangkok", "Bangkok", "Thailandia", "TH", 13.7563, 100.5018, "Asia/Bangkok",
                between(NOVEMBER, FEBRUARY),
                "Templi dorati, mercati galleggianti e lo street food più famoso al mondo.",
                seasonal(WARM_ESCAPE, between(NOVEMBER, FEBRUARY)), yearRound(FOOD), yearRound(NIGHTLIFE), yearRound(CULTURE),
            ),
            destination(
                "bali", "Bali", "Indonesia", "ID", -8.6705, 115.2126, "Asia/Makassar",
                between(APRIL, OCTOBER),
                "Risaie a terrazza, templi sull'oceano e surf al tramonto.",
                seasonal(BEACH, between(APRIL, OCTOBER)), yearRound(NATURE), yearRound(CULTURE),
            ),
            destination(
                "singapore", "Singapore", "Singapore", "SG", 1.3521, 103.8198, "Asia/Singapore",
                months(FEBRUARY, MARCH, APRIL, JULY, AUGUST),
                "Giardini futuristici, hawker center e un mix unico di culture.",
                seasonal(WARM_ESCAPE, between(DECEMBER, FEBRUARY)), yearRound(FOOD), yearRound(CULTURE),
            ),
            destination(
                "hanoi", "Hanoi", "Vietnam", "VN", 21.0278, 105.8342, "Asia/Ho_Chi_Minh",
                months(OCTOBER, NOVEMBER, MARCH, APRIL),
                "Fascino coloniale, pho fumante e la baia di Ha Long a poche ore.",
                seasonal(NATURE, months(OCTOBER, NOVEMBER, MARCH, APRIL)), yearRound(FOOD), yearRound(CULTURE),
            ),
            destination(
                "hong-kong", "Hong Kong", "Cina", "HK", 22.3193, 114.1694, "Asia/Hong_Kong",
                months(OCTOBER, NOVEMBER, DECEMBER),
                "Skyline mozzafiato, dim sum e sentieri panoramici sulle colline.",
                yearRound(FOOD), yearRound(NIGHTLIFE), yearRound(CULTURE),
            ),
            // ---- Americhe -----------------------------------------------------------------
            destination(
                "new-york", "New York", "Stati Uniti", "US", 40.7128, -74.0060, "America/New_York",
                months(APRIL, MAY, JUNE, SEPTEMBER, OCTOBER),
                "Grattacieli, musei e l'atmosfera natalizia di Manhattan.",
                seasonal(CHRISTMAS_MARKETS, months(DECEMBER)), seasonal(FOLIAGE, months(OCTOBER)),
                yearRound(CULTURE), yearRound(NIGHTLIFE), yearRound(FOOD),
            ),
            destination(
                "montreal", "Montréal", "Canada", "CA", 45.5019, -73.5674, "America/Toronto",
                between(JUNE, SEPTEMBER),
                "Festival estivi, foliage canadese e un'anima francese.",
                seasonal(FESTIVAL, months(JUNE, JULY)), seasonal(FOLIAGE, months(SEPTEMBER, OCTOBER)), yearRound(FOOD),
            ),
            destination(
                "vancouver", "Vancouver", "Canada", "CA", 49.2827, -123.1207, "America/Vancouver",
                between(JUNE, SEPTEMBER),
                "Oceano e montagne nella stessa giornata, tra kayak e sci.",
                seasonal(NATURE, between(JUNE, SEPTEMBER)), seasonal(WINTER_SPORTS, between(DECEMBER, MARCH)), yearRound(FOOD),
            ),
            destination(
                "citta-del-messico", "Città del Messico", "Messico", "MX", 19.4326, -99.1332, "America/Mexico_City",
                months(MARCH, APRIL, MAY, OCTOBER, NOVEMBER),
                "Murales, cucina patrimonio UNESCO e il Día de Muertos.",
                seasonal(FESTIVAL, months(OCTOBER, NOVEMBER)), yearRound(FOOD), yearRound(CULTURE),
            ),
            destination(
                "cancun", "Cancún", "Messico", "MX", 21.1619, -86.8515, "America/Cancun",
                between(DECEMBER, APRIL),
                "Mar dei Caraibi turchese e siti maya a un'ora di strada.",
                seasonal(BEACH, between(DECEMBER, APRIL)), seasonal(WARM_ESCAPE, between(DECEMBER, MARCH)), yearRound(NIGHTLIFE),
            ),
            destination(
                "lavana", "L'Avana", "Cuba", "CU", 23.1136, -82.3666, "America/Havana",
                between(NOVEMBER, APRIL),
                "Auto d'epoca, musica dal vivo e il Malecón al tramonto.",
                seasonal(WARM_ESCAPE, between(DECEMBER, MARCH)), yearRound(CULTURE), yearRound(NIGHTLIFE),
            ),
            destination(
                "rio-de-janeiro", "Rio de Janeiro", "Brasile", "BR", -22.9068, -43.1729, "America/Sao_Paulo",
                months(APRIL, MAY, JUNE, SEPTEMBER, OCTOBER),
                "Copacabana, il Cristo Redentore e il Carnevale più famoso del mondo.",
                seasonal(BEACH, between(DECEMBER, MARCH)), seasonal(FESTIVAL, months(FEBRUARY)),
                seasonal(WARM_ESCAPE, between(DECEMBER, FEBRUARY)),
            ),
            destination(
                "buenos-aires", "Buenos Aires", "Argentina", "AR", -34.6037, -58.3816, "America/Argentina/Buenos_Aires",
                months(OCTOBER, NOVEMBER, MARCH, APRIL),
                "Tango, carne alla griglia e quartieri colorati come La Boca.",
                yearRound(FOOD), yearRound(CULTURE), yearRound(NIGHTLIFE),
            ),
            destination(
                "cusco", "Cusco", "Perù", "PE", -13.5319, -71.9675, "America/Lima",
                between(MAY, SEPTEMBER),
                "La porta di Machu Picchu, tra rovine inca e mercati andini.",
                seasonal(NATURE, between(MAY, SEPTEMBER)), yearRound(CULTURE),
            ),
            // ---- Oceania ------------------------------------------------------------------
            destination(
                "sydney", "Sydney", "Australia", "AU", -33.8688, 151.2093, "Australia/Sydney",
                between(OCTOBER, APRIL),
                "Opera House, spiagge da surf e il Capodanno più spettacolare.",
                seasonal(BEACH, between(DECEMBER, MARCH)), seasonal(FESTIVAL, months(DECEMBER)), yearRound(CULTURE),
            ),
            destination(
                "melbourne", "Melbourne", "Australia", "AU", -37.8136, 144.9631, "Australia/Melbourne",
                between(NOVEMBER, MARCH),
                "Caffè d'autore, street art e la Great Ocean Road.",
                seasonal(FESTIVAL, months(JANUARY)), yearRound(FOOD), yearRound(CULTURE),
            ),
            destination(
                "queenstown", "Queenstown", "Nuova Zelanda", "NZ", -45.0312, 168.6626, "Pacific/Auckland",
                between(DECEMBER, MARCH),
                "La capitale dell'avventura: sci d'inverno, trekking e fiordi.",
                seasonal(WINTER_SPORTS, between(JUNE, SEPTEMBER)), yearRound(NATURE),
            ),
        )
    }
}
