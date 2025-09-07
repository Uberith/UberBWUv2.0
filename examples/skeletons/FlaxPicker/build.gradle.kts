dependencies {
    implementation(project(":script-api"))

    // Avoid packaging platform APIs to prevent JPMS split packages
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    compileOnly("net.botwithus.api:api:1.2.2-20250823.233014-1")
    compileOnly("net.botwithus.imgui:imgui:1.0.2-20250818.161536-3")
    compileOnly("net.botwithus.xapi.public:api:1.1.9")

    // Only include our script-api classes in the final jar
    configurations["includeInJar"].dependencies.add(
        dependencies.create(project(":script-api"))
    )
}
