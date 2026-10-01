package com.partimo.data.local

import com.partimo.domain.model.guide.DrivingSide
import com.partimo.domain.model.guide.EmergencyNumbers
import com.partimo.domain.model.guide.PowerInfo

/** Dati pratici di un paese che le librerie del sistema non conoscono. */
internal data class CountryFacts(
    val callingCode: String,
    val drivingSide: DrivingSide,
    val power: PowerInfo,
    val emergency: EmergencyNumbers,
    /** Lingue principali (ISO 639-1), la prima è la più diffusa. */
    val languages: List<String>,
)

/**
 * Dati pratici curati per le mete più frequenti: prefisso, lato di guida, prese (tipi IEC), tensione e
 * frequenza, numeri di emergenza e lingue principali. Sono conoscenza editoriale inclusa nell'app (nessuna
 * rete); per gli altri paesi la Guida mostra solo ciò che si ricava dal sistema e rimanda a Viaggiare Sicuri.
 * Fonti: IEC World Plugs, Commissione europea (112), siti dei governi.
 */
internal object CountryFactsCatalog {

    fun factsFor(countryCode: String): CountryFacts? = FACTS[countryCode.uppercase()]

    val countryCodes: Set<String> get() = FACTS.keys

    private val EU_230 = PowerInfo(listOf("C", "F"), "230", "50")
    private val EU_230_E = PowerInfo(listOf("C", "E"), "230", "50")
    private val UK_230 = PowerInfo(listOf("G"), "230", "50")
    private val US_120 = PowerInfo(listOf("A", "B"), "120", "60")
    private val EU_112 = EmergencyNumbers(general = "112")

    private fun facts(
        callingCode: String,
        power: PowerInfo,
        emergency: EmergencyNumbers,
        vararg languages: String,
        drivingSide: DrivingSide = DrivingSide.RIGHT,
    ) = CountryFacts(callingCode, drivingSide, power, emergency, languages.toList())

    private val LEFT = DrivingSide.LEFT

    private val FACTS: Map<String, CountryFacts> = mapOf(
        // Europa: nell'Unione europea il 112 vale ovunque, gratis anche dal cellulare.
        "AT" to facts("+43", EU_230, EmergencyNumbers("112", police = "133", ambulance = "144", fire = "122"), "de"),
        "BE" to facts("+32", EU_230_E, EmergencyNumbers("112", police = "101"), "nl", "fr", "de"),
        "BG" to facts("+359", EU_230, EU_112, "bg"),
        "HR" to facts("+385", EU_230, EmergencyNumbers("112", police = "192", ambulance = "194", fire = "193"), "hr"),
        "CY" to facts("+357", UK_230, EmergencyNumbers("112"), "el", "tr", drivingSide = LEFT),
        "CZ" to facts("+420", EU_230_E, EmergencyNumbers("112", police = "158", ambulance = "155", fire = "150"), "cs"),
        "DK" to facts("+45", PowerInfo(listOf("C", "E", "F", "K"), "230", "50"), EU_112, "da"),
        "EE" to facts("+372", EU_230, EU_112, "et"),
        "FI" to facts("+358", EU_230, EU_112, "fi", "sv"),
        "FR" to facts("+33", EU_230_E, EmergencyNumbers("112", police = "17", ambulance = "15", fire = "18"), "fr"),
        "DE" to facts("+49", EU_230, EmergencyNumbers("112", police = "110"), "de"),
        "GR" to facts("+30", EU_230, EmergencyNumbers("112", police = "100", ambulance = "166", fire = "199"), "el"),
        "HU" to facts("+36", EU_230, EU_112, "hu"),
        "IE" to facts("+353", UK_230, EmergencyNumbers("112"), "en", "ga", drivingSide = LEFT),
        "IT" to facts("+39", PowerInfo(listOf("C", "F", "L"), "230", "50"), EU_112, "it"),
        "LV" to facts("+371", EU_230, EU_112, "lv"),
        "LT" to facts("+370", EU_230, EU_112, "lt"),
        "LU" to facts("+352", EU_230, EmergencyNumbers("112", police = "113"), "lb", "fr", "de"),
        "MT" to facts("+356", UK_230, EU_112, "mt", "en", drivingSide = LEFT),
        "NL" to facts("+31", EU_230, EU_112, "nl"),
        "PL" to facts("+48", EU_230_E, EmergencyNumbers("112", police = "997", ambulance = "999", fire = "998"), "pl"),
        "PT" to facts("+351", EU_230, EU_112, "pt"),
        "RO" to facts("+40", EU_230, EU_112, "ro"),
        "SK" to facts("+421", EU_230_E, EmergencyNumbers("112", police = "158", ambulance = "155", fire = "150"), "sk"),
        "SI" to facts("+386", EU_230, EmergencyNumbers("112", police = "113"), "sl"),
        "ES" to facts("+34", EU_230, EmergencyNumbers("112", police = "091", ambulance = "061", fire = "080"), "es"),
        "SE" to facts("+46", EU_230, EU_112, "sv"),
        "IS" to facts("+354", EU_230, EU_112, "is"),
        "NO" to facts("+47", EU_230, EmergencyNumbers(police = "112", ambulance = "113", fire = "110"), "no"),
        "CH" to facts("+41", PowerInfo(listOf("C", "J"), "230", "50"), EmergencyNumbers("112", police = "117", ambulance = "144", fire = "118"), "de", "fr", "it"),
        "GB" to facts("+44", UK_230, EmergencyNumbers("999"), "en", drivingSide = LEFT),
        "MC" to facts("+377", PowerInfo(listOf("C", "E", "F"), "230", "50"), EU_112, "fr"),
        "SM" to facts("+378", PowerInfo(listOf("C", "F", "L"), "230", "50"), EU_112, "it"),
        "AD" to facts("+376", EU_230, EU_112, "ca"),
        "AL" to facts("+355", EU_230, EmergencyNumbers(police = "129", ambulance = "127", fire = "128"), "sq"),
        "BA" to facts("+387", EU_230, EmergencyNumbers(police = "122", ambulance = "124", fire = "123"), "bs", "hr", "sr"),
        "ME" to facts("+382", EU_230, EmergencyNumbers("112", police = "122", ambulance = "124", fire = "123"), "sr"),
        "MK" to facts("+389", EU_230, EmergencyNumbers("112", police = "192", ambulance = "194", fire = "193"), "mk"),
        "RS" to facts("+381", EU_230, EmergencyNumbers(police = "192", ambulance = "194", fire = "193"), "sr"),
        "TR" to facts("+90", EU_230, EU_112, "tr"),
        "GE" to facts("+995", PowerInfo(listOf("C", "F"), "220", "50"), EU_112, "ka"),
        "AM" to facts("+374", EU_230, EU_112, "hy"),
        // Americhe
        "US" to facts("+1", US_120, EmergencyNumbers("911"), "en"),
        "CA" to facts("+1", US_120, EmergencyNumbers("911"), "en", "fr"),
        "MX" to facts("+52", PowerInfo(listOf("A", "B"), "127", "60"), EmergencyNumbers("911"), "es"),
        "CU" to facts("+53", PowerInfo(listOf("A", "B", "C", "L"), "110/220", "60"), EmergencyNumbers(police = "106", ambulance = "104", fire = "105"), "es"),
        "DO" to facts("+1", US_120, EmergencyNumbers("911"), "es"),
        "CR" to facts("+506", US_120, EmergencyNumbers("911"), "es"),
        "CO" to facts("+57", PowerInfo(listOf("A", "B"), "110", "60"), EmergencyNumbers("123"), "es"),
        "PE" to facts("+51", PowerInfo(listOf("A", "C"), "220", "60"), EmergencyNumbers(police = "105", fire = "116"), "es"),
        "BR" to facts("+55", PowerInfo(listOf("C", "N"), "127/220", "60"), EmergencyNumbers(police = "190", ambulance = "192", fire = "193"), "pt"),
        "AR" to facts("+54", PowerInfo(listOf("C", "I"), "220", "50"), EmergencyNumbers(police = "911", ambulance = "107", fire = "100"), "es"),
        "CL" to facts("+56", PowerInfo(listOf("C", "L"), "220", "50"), EmergencyNumbers(police = "133", ambulance = "131", fire = "132"), "es"),
        // Asia e Oceania
        "JP" to facts("+81", PowerInfo(listOf("A", "B"), "100", "50/60"), EmergencyNumbers(police = "110", ambulance = "119", fire = "119"), "ja", drivingSide = LEFT),
        "CN" to facts("+86", PowerInfo(listOf("A", "C", "I"), "220", "50"), EmergencyNumbers(police = "110", ambulance = "120", fire = "119"), "zh"),
        "HK" to facts("+852", PowerInfo(listOf("G"), "220", "50"), EmergencyNumbers("999"), "zh", "en", drivingSide = LEFT),
        "KR" to facts("+82", PowerInfo(listOf("C", "F"), "220", "60"), EmergencyNumbers(police = "112", ambulance = "119", fire = "119"), "ko"),
        "TH" to facts("+66", PowerInfo(listOf("A", "B", "C", "O"), "230", "50"), EmergencyNumbers(police = "191", ambulance = "1669", fire = "199"), "th", drivingSide = LEFT),
        "VN" to facts("+84", PowerInfo(listOf("A", "C"), "220", "50"), EmergencyNumbers(police = "113", ambulance = "115", fire = "114"), "vi"),
        "ID" to facts("+62", EU_230, EmergencyNumbers("112", police = "110"), "id", drivingSide = LEFT),
        "MY" to facts("+60", PowerInfo(listOf("G"), "240", "50"), EmergencyNumbers("999"), "ms", drivingSide = LEFT),
        "SG" to facts("+65", UK_230, EmergencyNumbers(police = "999", ambulance = "995", fire = "995"), "en", "ms", "zh", "ta", drivingSide = LEFT),
        "PH" to facts("+63", PowerInfo(listOf("A", "B", "C"), "220", "60"), EmergencyNumbers("911"), "fil", "en"),
        "IN" to facts("+91", PowerInfo(listOf("C", "D", "M"), "230", "50"), EmergencyNumbers("112"), "hi", "en", drivingSide = LEFT),
        "LK" to facts("+94", PowerInfo(listOf("D", "G", "M"), "230", "50"), EmergencyNumbers(police = "119", ambulance = "1990"), "si", "ta", drivingSide = LEFT),
        "MV" to facts("+960", PowerInfo(listOf("C", "D", "G", "J", "K", "L"), "230", "50"), EmergencyNumbers(police = "119", ambulance = "102", fire = "118"), "dv", drivingSide = LEFT),
        "NP" to facts("+977", PowerInfo(listOf("C", "D", "M"), "230", "50"), EmergencyNumbers(police = "100", ambulance = "102", fire = "101"), "ne", drivingSide = LEFT),
        "AE" to facts("+971", PowerInfo(listOf("C", "D", "G"), "230", "50"), EmergencyNumbers(police = "999", ambulance = "998", fire = "997"), "ar"),
        "QA" to facts("+974", PowerInfo(listOf("D", "G"), "240", "50"), EmergencyNumbers("999"), "ar"),
        "JO" to facts("+962", PowerInfo(listOf("B", "C", "D", "F", "G", "J"), "230", "50"), EmergencyNumbers("911"), "ar"),
        "IL" to facts("+972", PowerInfo(listOf("C", "H", "M"), "230", "50"), EmergencyNumbers(police = "100", ambulance = "101", fire = "102"), "he"),
        "AU" to facts("+61", PowerInfo(listOf("I"), "230", "50"), EmergencyNumbers("000"), "en", drivingSide = LEFT),
        "NZ" to facts("+64", PowerInfo(listOf("I"), "230", "50"), EmergencyNumbers("111"), "en", drivingSide = LEFT),
        // Africa
        "MA" to facts("+212", EU_230_E.copy(voltage = "220"), EmergencyNumbers(police = "19", ambulance = "15", fire = "15"), "ar", "fr"),
        "TN" to facts("+216", EU_230_E, EmergencyNumbers(police = "197", ambulance = "190", fire = "198"), "ar", "fr"),
        "EG" to facts("+20", EU_230.copy(voltage = "220"), EmergencyNumbers(police = "122", ambulance = "123", fire = "180"), "ar"),
        "CV" to facts("+238", EU_230.copy(voltage = "220"), EmergencyNumbers(police = "132", ambulance = "130", fire = "131"), "pt"),
        "ZA" to facts("+27", PowerInfo(listOf("C", "D", "M", "N"), "230", "50"), EmergencyNumbers("112", police = "10111", ambulance = "10177"), "en", "af", "zu", drivingSide = LEFT),
        "KE" to facts("+254", PowerInfo(listOf("G"), "240", "50"), EmergencyNumbers("999"), "sw", "en", drivingSide = LEFT),
        "TZ" to facts("+255", PowerInfo(listOf("D", "G"), "230", "50"), EmergencyNumbers("112"), "sw", "en", drivingSide = LEFT),
        "SC" to facts("+248", PowerInfo(listOf("G"), "240", "50"), EmergencyNumbers("999"), "en", "fr", drivingSide = LEFT),
        "MU" to facts("+230", PowerInfo(listOf("C", "G"), "230", "50"), EmergencyNumbers("999", ambulance = "114", fire = "115"), "en", "fr", drivingSide = LEFT),
    )
}
