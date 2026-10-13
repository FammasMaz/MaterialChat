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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MaterialChat"
include(":app", ":seaglass-core", ":seaglass-compose")
project(":seaglass-core").projectDir = file("third_party/seaglass/seaglass-core")
project(":seaglass-compose").projectDir = file("third_party/seaglass/seaglass-compose")
