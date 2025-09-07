dependencies {
    // Kotlin stdlib for language operators and primitives
    implementation(kotlin("stdlib"))
    // Core API and UI deps similar to skeletons
    implementation(project(":script-api"))
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("net.botwithus.api:api:1.2.2-20250823.233014-1")
    implementation("net.botwithus.imgui:imgui:1.0.2-20250818.161536-3")

    // Optional public XAPI, also include it inside the fat jar
    implementation("net.botwithus.xapi.public:api:1.1.9")

    // Ensure the script-api and XAPI classes are packaged into the output jar
    configurations["includeInJar"].dependencies.add(
        dependencies.create(project(":script-api"))
    )
    configurations["includeInJar"].dependencies.add(
        dependencies.create("net.botwithus.xapi.public:api:1.1.9")
    )
}
