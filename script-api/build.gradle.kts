dependencies {
    // Kotlin stdlib for language operators and primitives
    implementation(kotlin("stdlib"))

    // BotWithUs runtime provides these; depend at compile time only
    compileOnly("net.botwithus.api:api:1.2.2-20250823.233014-1")
    compileOnly("net.botwithus.imgui:imgui:1.0.2-20250818.161536-3")
    compileOnly("net.botwithus.xapi.public:api:1.1.9")

    // Logging API; provided by platform, no need to package
    compileOnly("org.slf4j:slf4j-api:2.0.17")

    // Persistence helpers
    implementation("com.google.code.gson:gson:2.10.1")
}

// Build jar for shading into UberChop, but do not copy to scripts
tasks.named<Jar>("jar").configure { enabled = true }
tasks.matching { it.name == "copyJar" }.configureEach { enabled = false }

// Still publish compiled classes to consumers so project dependencies work
val sourceSets = extensions.getByName("sourceSets") as org.gradle.api.tasks.SourceSetContainer
val mainSourceSet = sourceSets.getByName("main")

val publishClasses = tasks.register<Sync>("publishClasses") {
    from(mainSourceSet.output)
    into(layout.buildDirectory.dir("published-classes"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val classesElements by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes.attribute(org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE, objects.named(org.gradle.api.attributes.Usage.JAVA_API))
    attributes.attribute(org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE, objects.named(org.gradle.api.attributes.Category.LIBRARY))
    attributes.attribute(org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(org.gradle.api.attributes.LibraryElements.CLASSES))
    val publishedDir = layout.buildDirectory.dir("published-classes")
    outgoing.artifact(publishedDir) {
        builtBy(publishClasses)
    }
}
