import java.io.File
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
group = "com.uberith.skillingscripts.uberchop"
version = "1.0.0-SNAPSHOT"

dependencies {
    // Compile against script-api
    implementation(project(":script-api"))
    // Shade script-api desktop jar into UberChop jar (exclude module-info.class via root jar config)
    add("includeInJar", project(mapOf("path" to ":script-api", "configuration" to "desktopRuntimeElements")))
}

// Ensure module-info.java compiles with access to UberChop Kotlin classes and the embedded script-api classes
tasks.named<JavaCompile>("compileJava").configure {
    dependsOn("compileKotlin", ":script-api:compileKotlinDesktop", ":script-api:compileDesktopMainJava")
    val scriptApiKotlinOut = project(":script-api").layout.buildDirectory.dir("classes/kotlin/desktop/main")
    val scriptApiJavaOut = project(":script-api").layout.buildDirectory.dir("classes/java/desktop/main")
    val localKotlinOut = layout.buildDirectory.dir("classes/kotlin/main")
    val localJavaOut = layout.buildDirectory.dir("classes/java/main")
    val paths = listOf(
        localKotlinOut.get().asFile.path,
        localJavaOut.get().asFile.path,
        scriptApiKotlinOut.get().asFile.path,
        scriptApiJavaOut.get().asFile.path,
    )
    val patchPath = paths.joinToString(File.pathSeparator)
    options.compilerArgs.addAll(listOf("--patch-module", "UberChop=${patchPath}"))
}

// Keep Kotlin classes in a separate output directory so javac cleaning does not remove them
tasks.named<KotlinCompile>("compileKotlin").configure {
    destinationDirectory.set(layout.buildDirectory.dir("classes/kotlin/main"))
}
