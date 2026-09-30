pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PartiMo"

// Clean Architecture: ogni layer è un modulo Gradle separato, così le dipendenze
// tra layer sono verificate dal compilatore (il dominio non vede Android né il data layer).
include(":domain") // Kotlin puro: modelli, interfacce repository, casi d'uso
include(":data")   // Android library: Ktor, Room, implementazioni repository
include(":app")    // Presentation: Compose + ViewModel, composition root (DI manuale)
