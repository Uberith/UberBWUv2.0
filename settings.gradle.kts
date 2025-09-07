plugins {
    // Enables automatic JDK provisioning via Foojay for Gradle toolchains
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "UberBWUv2.0"

include(
    "script-api",
    "SkillingScripts:UberChop",
)
