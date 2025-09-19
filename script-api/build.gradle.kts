import org.gradle.api.tasks.compile.JavaCompile

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()
    jvmToolchain(24)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("net.botwithus.kxapi:kxapi:0.1-SNAPSHOT")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("app.cash.turbine:turbine:1.1.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        val jvmMain by getting {
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
            dependencies {
                implementation("org.slf4j:slf4j-api:2.0.9")
                implementation("net.botwithus.api:api:1.0.+")
                implementation("net.botwithus.imgui:imgui:1.0.+")
                implementation("net.botwithus.xapi:xapi:2.0.+")
                implementation("net.botwithus.kxapi:kxapi:0.1-SNAPSHOT")
                implementation("botwithus.navigation:nav-api:1.+")
                implementation("com.google.code.gson:gson:2.10.1")
            }
        }
    }
}

tasks.named<JavaCompile>("compileJvmMainJava").configure {
    source("src/main/java")
    dependsOn(tasks.named("compileKotlinJvm"))
    val kotlinOut = layout.buildDirectory.dir("classes/kotlin/jvm/main")
    options.compilerArgs.addAll(listOf("--patch-module", "UberScriptAPI.main=${kotlinOut.get().asFile.path}"))
}
