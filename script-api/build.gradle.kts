plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm("desktop")
    // Align JVM toolchain with other modules (Java 24)
    jvmToolchain(24)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("app.cash.turbine:turbine:1.1.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }

        val jvmMain by creating {
            dependsOn(commonMain)
            // Reuse existing layout
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
        }
        val jvmTest by creating { dependsOn(commonTest) }

        val desktopMain by getting {
            dependsOn(jvmMain)
            dependencies {
                implementation("org.slf4j:slf4j-api:2.0.9")
                implementation("net.botwithus.api:api:1.0.+")
                implementation("net.botwithus.imgui:imgui:1.0.+")
                implementation("net.botwithus.xapi:xapi:2.0.+")
                implementation("botwithus.navigation:nav-api:1.+")
                implementation("com.google.code.gson:gson:2.10.1")
            }
        }
        val desktopTest by getting { dependsOn(jvmTest) }
    }
}

// Ensure module-info.java sees Kotlin classes when compiling the desktop target
tasks.named<JavaCompile>("compileDesktopMainJava").configure {
    dependsOn(tasks.named("compileKotlinDesktop"))
    // Patch the module with Kotlin-compiled classes so exported packages resolve
    val kotlinOut = layout.buildDirectory.dir("classes/kotlin/desktop/main")
    options.compilerArgs.addAll(listOf("--patch-module", "UberScriptAPI.main=${kotlinOut.get().asFile.path}"))
}

// Keep desktopJar build enabled for downstream consumers, but we do not copy it anywhere
