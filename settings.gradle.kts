pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        // Centralize plugin versions to avoid classpath conflicts
        kotlin("jvm") version "2.2.0"
        kotlin("multiplatform") version "2.2.0"
        kotlin("plugin.serialization") version "2.2.0"
        id("com.android.library") version "8.7.2"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Prefer central, but also allow local artifacts
        mavenLocal()
        google()
        mavenCentral()
        maven("https://nexus.botwithus.net/repository/maven-releases/")
        maven("https://nexus.botwithus.net/repository/maven-snapshots/")
    }
}

rootProject.name = "UberBWU2.0"

// Modules
include(":script-api")
// Aggregate module for scripts
include(":SkillingScripts")
// Individual scripts
include(":SkillingScripts:UberChop")


