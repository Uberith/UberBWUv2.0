dependencies {
    // Ensure Kotlin stdlib is on classpath for coroutine primitives
    implementation(kotlin("stdlib"))
    // Core BotWithUs API for Client, Script, etc.
    implementation("net.botwithus.api:api:1.2.2-20250823.233014-1")
    // Optionally expose to dependents
    api("org.slf4j:slf4j-api:2.0.9")
}
