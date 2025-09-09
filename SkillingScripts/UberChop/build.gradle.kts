group = "com.uberith.skillingscripts.uberchop"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":script-api"))
}

// Include compiled classes from script-api directly into UberChop jar,
// without requiring script-api to build/publish its own jar.
afterEvaluate {
    val scriptApi = project(":script-api")
    tasks.named<Jar>("jar") {
        // Pull the main outputs (Kotlin/Java classes) from script-api
        val scriptApiOutputs = scriptApi.extensions
            .getByName("sourceSets") as org.gradle.api.tasks.SourceSetContainer
        val mainOut = scriptApiOutputs.getByName("main").output

        from(mainOut) {
            // Avoid duplicate module descriptors
            exclude("module-info.class")
        }
    }
}
