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
    val languageCode: String = "it",
    val enableHttpLogging: Boolean = false,
) {
    val hasDuffelToken: Boolean get() = duffelAccessToken.isNotBlank()
    val hasGoogleMapsKey: Boolean get() = googleMapsApiKey.isNotBlank()

    override fun toString(): String =
        "ApiConfig(duffel=${mask(duffelAccessToken)}, googleMaps=${mask(googleMapsApiKey)}, language=$languageCode)"

    private fun mask(secret: String): String = if (secret.isBlank()) "<non configurata>" else "****"
}
