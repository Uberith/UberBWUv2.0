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
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        // Prefer central, but also allow local artifacts
        mavenLocal()
        google()
        mavenCentral()
        maven("https://nexus.botwithus.net/repository/maven-releases/")
        maven("https://nexus.botwithus.net/repository/maven-snapshots/")
    }
    versionCatalogs {
        create("libs") {
            version("slf4j", "2.0.9")
            version("botwithusApi", "1.0.+")
            version("botwithusImgui", "1.0.+")
            version("botwithusXapi", "2.0.+")
            version("botwithusNav", "1.+")
            version("botwithusKxapi", "0.1-SNAPSHOT")
            version("gson", "2.10.1")
            version("kotlinStdlib", "2.2.0")
            version("kotlinxCoroutinesCoreJvm", "1.7.1")
            version("kotlinxCoroutines", "1.8.1")
            version("kotlinxSerialization", "1.6.3")
            version("turbine", "1.1.0")

            library("slf4jApi", "org.slf4j", "slf4j-api").versionRef("slf4j")
            library("botwithusApi", "net.botwithus.api", "api").versionRef("botwithusApi")
            library("botwithusImgui", "net.botwithus.imgui", "imgui").versionRef("botwithusImgui")
            library("botwithusXapi", "net.botwithus.xapi", "xapi").versionRef("botwithusXapi")
            library("botwithusNavApi", "botwithus.navigation", "nav-api").versionRef("botwithusNav")
            library("botwithusKxapi", "net.botwithus.kxapi", "kxapi").versionRef("botwithusKxapi")
            library("gson", "com.google.code.gson", "gson").versionRef("gson")
            library("kotlinStdlib", "org.jetbrains.kotlin", "kotlin-stdlib").versionRef("kotlinStdlib")
            library("kotlinStdlibJdk8", "org.jetbrains.kotlin", "kotlin-stdlib-jdk8").versionRef("kotlinStdlib")
            library("kotlinStdlibJdk7", "org.jetbrains.kotlin", "kotlin-stdlib-jdk7").versionRef("kotlinStdlib")
            library("kotlinReflect", "org.jetbrains.kotlin", "kotlin-reflect").versionRef("kotlinStdlib")
            library("kotlinxCoroutinesCoreJvm", "org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm").versionRef("kotlinxCoroutinesCoreJvm")
            library("kotlinxCoroutinesCore", "org.jetbrains.kotlinx", "kotlinx-coroutines-core").versionRef("kotlinxCoroutines")
            library("kotlinxCoroutinesTest", "org.jetbrains.kotlinx", "kotlinx-coroutines-test").versionRef("kotlinxCoroutines")
            library("kotlinxSerializationJson", "org.jetbrains.kotlinx", "kotlinx-serialization-json").versionRef("kotlinxSerialization")
            library("appCashTurbine", "app.cash.turbine", "turbine").versionRef("turbine")
        }
    }
}

rootProject.name = "UberBWU2.0"

// Modules
include(":script-api")
// Aggregate module for scripts
include(":SkillingScripts")
// Individual scripts
include(":SkillingScripts:UberChop")

include("SkillingScripts:UberTestingUtil")
include("SkillingScripts:UberChop:UberTestingUtil")
include("SkillingScripts:UberTestingUtil")
include(":SkillingScripts:SuspendableDemo")
