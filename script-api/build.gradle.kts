dependencies {
    // Kotlin stdlib for language operators and primitives
    implementation(kotlin("stdlib"))

    // BotWithUs runtime provides these; depend at compile time only
    compileOnly("net.botwithus.api:api:1.2.2-20250823.233014-1")

    // Logging API; provided by platform, no need to package
    compileOnly("org.slf4j:slf4j-api:2.0.9")
}
