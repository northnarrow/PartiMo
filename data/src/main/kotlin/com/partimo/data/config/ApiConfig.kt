package com.partimo.data.config

/**
 * Configurazione dei provider esterni.
 *
 * Le chiavi arrivano da BuildConfig, che a sua volta le legge da `local.properties`: non sono mai
 * scritte nel codice. Una chiave vuota attiva automaticamente la sorgente demo del relativo modulo.
 *
 * Non è una data class di proposito: il [toString] generato esporrebbe le chiavi in log e crash report.
 */
class ApiConfig(
    val duffelAccessToken: String,
    val googleMapsApiKey: String,
    /** Chiave di Google Gemini (AI Studio) per l'assistente di viaggio; vuota = assistente non disponibile. */
    val geminiApiKey: String = "",
    val languageCode: String = "it",
    val enableHttpLogging: Boolean = false,
    /**
     * User-Agent per i servizi pubblici senza chiave (Wikipedia): Wikimedia chiede di identificare
     * l'app con un contatto, altrimenti applica il limite di richieste più basso.
     */
    val userAgent: String = DEFAULT_USER_AGENT,
    /**
     * Identità dell'app per le chiavi Google limitate alle app Android: nelle chiamate REST la chiave
     * è accettata solo se la richiesta dichiara package e certificato di firma dell'app.
     */
    val androidApp: AndroidAppIdentity? = null,
) {
    val hasDuffelToken: Boolean get() = duffelAccessToken.isNotBlank()
    val hasGoogleMapsKey: Boolean get() = googleMapsApiKey.isNotBlank()
    val hasGeminiKey: Boolean get() = geminiApiKey.isNotBlank()

    override fun toString(): String =
        "ApiConfig(duffel=${mask(duffelAccessToken)}, googleMaps=${mask(googleMapsApiKey)}, " +
            "gemini=${mask(geminiApiKey)}, language=$languageCode)"

    private fun mask(secret: String): String = if (secret.isBlank()) "<non configurata>" else "****"

    companion object {
        const val DEFAULT_USER_AGENT = "PartiMo (https://github.com/northnarrow/partimo) Ktor"
    }
}

/**
 * Package e impronta SHA-1 del certificato di firma (esadecimale maiuscolo, senza separatori),
 * inviati a Google con le intestazioni `X-Android-Package` e `X-Android-Cert`.
 */
data class AndroidAppIdentity(val packageName: String, val certificateSha1: String)
