import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Layer di dominio in Kotlin puro: nessuna dipendenza da Android, rete o database.
plugins {
    alias(libs.plugins.kotlin.jvm)
    // Espone fake e dati di esempio (src/testFixtures) ai test degli altri moduli.
    `java-test-fixtures`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Le interfacce pubbliche usano coroutine (suspend/Flow): la dipendenza è esposta come api.
    api(libs.kotlinx.coroutines.core)

    testFixturesApi(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnit()
}
