import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage

// Configuration to consume script-api classes (directory) for embedding
val embedScriptApi by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
    attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
}

dependencies {
    // Kotlin stdlib for language operators and primitives
    implementation(kotlin("stdlib"))

    // Compile against shared code classes (directory), but don't add to runtime
    compileOnly(project(path = ":script-api", configuration = "classesElements"))

    // Add shared code classes into our jar to satisfy runtime without a separate jar
    embedScriptApi(project(path = ":script-api", configuration = "classesElements"))

    // Platform-provided APIs at runtime; do not package
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    compileOnly("net.botwithus.api:api:1.2.2-20250823.233014-1")
    compileOnly("net.botwithus.imgui:imgui:1.0.2-20250818.161536-3")
    compileOnly("net.botwithus.xapi.public:api:1.1.9")
}

// Disable tests to avoid variant resolution on testRuntimeClasspath
tasks.withType<Test>().configureEach { enabled = false }

// Package script-api compiled classes (directory) into this jar
tasks.named<Jar>("jar").configure {
    from(embedScriptApi)
}
