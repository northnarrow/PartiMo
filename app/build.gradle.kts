import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Rotte di navigazione type-safe (@Serializable), trasportate anche dentro le notifiche.
    alias(libs.plugins.kotlin.serialization)
}

// Le chiavi API vengono lette da local.properties (escluso dal VCS) o, in CI, da variabili
// d'ambiente, e iniettate in BuildConfig: non sono mai scritte nel codice sorgente.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

fun secret(name: String): String {
    val raw = localProperties.getProperty(name) ?: providers.environmentVariable(name).orNull ?: ""
    // Le chiavi non contengono spazi: un commento scritto dopo il valore ("CHIAVE=abc   # nota"),
    // che nei file .properties farebbe parte del valore, viene ignorato.
    val value = raw.trim().split(Regex("\\s+")).first()
    // Escape per poter inserire il valore in modo sicuro in un letterale String Java.
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.partimo.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.partimo.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.0"

        buildConfigField("String", "DUFFEL_ACCESS_TOKEN", "\"${secret("DUFFEL_ACCESS_TOKEN")}\"")
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"${secret("GOOGLE_MAPS_API_KEY")}\"")
        // Assistente di viaggio (itinerari e domande): Google Gemini, livello gratuito.
        buildConfigField("String", "GEMINI_API_KEY", "\"${secret("GEMINI_API_KEY")}\"")
    }

    // Librerie native (mappa, traduttore) solo per i telefoni ARM: gli emulatori recenti le eseguono con
    // la traduzione ARM. Oltre all'APK universale si generano gli APK per i telefoni a 64 bit (quasi
    // tutti) e a 32 bit, più leggeri da scaricare e installare.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            // R8 toglie le parti inutilizzate delle librerie: APK molto più leggero. Il codice dell'app
            // resta intero e non offuscato (vedi proguard-rules.pro).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // App personale: firmata con la chiave di debug, così si installa sopra le versioni di debug.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Librerie native compresse nell'APK: file da scaricare molto più leggero (Android le estrae all'installazione).
        // Niente librerie x86: anche l'APK universale contiene solo quelle per i telefoni ARM.
        jniLibs {
            useLegacyPackaging = true
            excludes += listOf("lib/x86/**", "lib/x86_64/**")
        }
    }

    testOptions {
        // Necessario per i test Compose eseguiti con Robolectric sulla JVM.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Controlli periodici delle offerte e notifiche degli affari
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Mappa dei luoghi del viaggio
    implementation(libs.maplibre.android)

    // Widget "Prossimo viaggio"
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Perdite di memoria segnalate durante lo sviluppo (non entra nella build di rilascio)
    debugImplementation(libs.leakcanary.android)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // Test UI Compose sulla JVM (Robolectric), senza emulatore.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
