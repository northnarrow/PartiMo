// Data layer: client HTTP (Ktor), cache locale (Room), preferenze (DataStore) e implementazioni
// dei repository di dominio.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.partimo.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":domain"))

    // Networking
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    // Cache locale
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Preferenze dell'utente e viaggi seguiti
    implementation(libs.androidx.datastore.preferences)

    // Traduttore offline
    implementation(libs.mlkit.translate)
    // Testo di foto, screenshot e PDF sul telefono (prenotazioni, traduzione con la fotocamera): modelli scaricati
    // da Google Play Services, per l'alfabeto latino e per le scritture cinese, devanagari, giapponese e coreana.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.text.recognition.devanagari)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.text.recognition.korean)
    // Orientamento delle foto su Android 8 (da Android 9 lo applica ImageDecoder).
    implementation(libs.androidx.exifinterface)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
