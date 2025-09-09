group = "com.uberith.scriptapi"
version = "1.0.0-SNAPSHOT"

// Keep a local jar for module-path resolution during compilation,
// but do not copy/publish it as a script artifact.
tasks.named<Jar>("jar").configure { enabled = true }
tasks.matching { it.name == "copyJar" }.configureEach { enabled = false }
