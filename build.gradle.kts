// File di build di primo livello: dichiara i plugin (senza applicarli) per fissarne le versioni.
// AGP 9 integra il supporto a Kotlin ("built-in Kotlin"): il plugin org.jetbrains.kotlin.android
// non va applicato. Dichiarare kotlin-jvm qui porta sul classpath la versione di KGP del catalogo,
// che sostituisce la versione minima (2.2.10) richiesta a runtime da AGP.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
