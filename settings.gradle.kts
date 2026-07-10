pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri(rootDir.resolve(".gradle/offline-repo")) }
        google()
        mavenCentral()
    }
}

rootProject.name = "LightReader"
include(":app")
include(":macrobenchmark")
include(":core-reader")
include(":core-formats")
include(":core-data")
include(":feature-download")
include(":feature-reader")
