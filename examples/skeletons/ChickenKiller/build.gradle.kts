dependencies {
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("net.botwithus.api:api:1.2.2-20250823.233014-1")
    implementation("net.botwithus.imgui:imgui:1.0.2-20250818.161536-3")

    implementation("net.botwithus.xapi.public:api:1.1.9")
    configurations["includeInJar"].dependencies.add(
        dependencies.create("net.botwithus.xapi.public:api:1.1.9")
    )
}

