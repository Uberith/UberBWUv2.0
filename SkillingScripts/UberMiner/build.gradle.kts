import java.io.File
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

group = "com.uberith.skillingscripts.uberminer"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":script-api"))
    add("includeInJar", project(mapOf("path" to ":script-api", "configuration" to "jvmRuntimeElements")))
}

tasks.named<JavaCompile>("compileJava").configure {
    dependsOn("compileKotlin", ":script-api:compileKotlinJvm", ":script-api:compileJvmMainJava")
    val scriptApiKotlinOut = project(":script-api").layout.buildDirectory.dir("classes/kotlin/jvm/main")
    val scriptApiJavaOut = project(":script-api").layout.buildDirectory.dir("classes/java/jvm/main")
    val localKotlinOut = layout.buildDirectory.dir("classes/kotlin/main")
    val localJavaOut = layout.buildDirectory.dir("classes/java/main")
    val paths = listOf(
        localKotlinOut.get().asFile.path,
        localJavaOut.get().asFile.path,
        scriptApiKotlinOut.get().asFile.path,
        scriptApiJavaOut.get().asFile.path,
    )
    val patchPath = paths.joinToString(File.pathSeparator)
    options.compilerArgs.addAll(listOf("--patch-module", "UberMiner=${patchPath}"))
}

tasks.named<KotlinCompile>("compileKotlin").configure {
    destinationDirectory.set(layout.buildDirectory.dir("classes/kotlin/main"))
}
