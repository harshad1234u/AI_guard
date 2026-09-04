pluginManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "TrustLens"

// Application
include(":app")

// Pure Kotlin/JVM modules — no Android dependency, fast unit tests.
// These hold the deterministic core of TrustLens.
include(":domain")
include(":riskengine")
include(":investigation")

// Android library modules
include(":core")
include(":ai")
include(":threatintel")

// Feature modules (UI)
include(":feature:home")
include(":feature:lensscan")
include(":feature:qrscan")
include(":feature:urlcheck")
include(":feature:textcheck")
include(":feature:voicecheck")
include(":feature:result")
