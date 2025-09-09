import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.0"
}

group = "uberith"
version = "1.0.0"

allprojects {
    val hasKotlin = file("src/main/kotlin").exists() || file("src/test/kotlin").exists()
    val hasJava = file("src/main/java").exists() || file("src/test/java").exists()
    val hasSources = hasKotlin || hasJava

    if (hasSources) {
        if (hasKotlin) {
            apply(plugin = "kotlin")
        } else {
            apply(plugin = "java")
        }
    }

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://nexus.botwithus.net/repository/maven-releases/")
        maven("https://nexus.botwithus.net/repository/maven-snapshots/")
    }

    if (hasSources) {
        configurations {
            create("includeInJar") {
                isTransitive = false
            }
        }
    }

    if (hasSources) {
        extensions.configure<JavaPluginExtension> {
            val javaModuleInfo = file("src/main/java/module-info.java")
            if (javaModuleInfo.exists()) {
                modularity.inferModulePath.set(true)
            } else if (project != rootProject) {
                throw GradleException("ERROR: No module-info.java found in src/main/java/ or src/main/kotlin/ for project ${project.name}")
            }

            toolchain {
                languageVersion.set(JavaLanguageVersion.of(24))
            }
        }
    }

    if (hasSources) {
        dependencies {
            implementation("org.slf4j:slf4j-api:2.0.9")
            implementation("net.botwithus.api:api:1.0.+")
            implementation("net.botwithus.imgui:imgui:1.0.+")
            implementation("net.botwithus.xapi:xapi:2.0.+")
            implementation("botwithus.navigation:nav-api:1.+")
            implementation("com.google.code.gson:gson:2.10.1")
            add("includeInJar", "net.botwithus.xapi:xapi:2.0.+")

            if (hasKotlin) {
                implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
                testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.0")
            }
        }
    }

    if (hasKotlin) {
        java {
            modularity.inferModulePath.set(true)
        }

        tasks.compileKotlin {
            destinationDirectory.set(tasks.compileJava.get().destinationDirectory)
        }

        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_24)
                freeCompilerArgs.add("-Xjdk-release=24")
            }
        }
    }

    if (hasSources) {
        tasks.withType<Jar>().configureEach {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            from(configurations["includeInJar"].map { zipTree(it) })
        }
    }

    if (hasSources) {
        val copyJar by tasks.register<Copy>("copyJar") {
            from(tasks.named("jar"))
            into("${System.getProperty("user.home")}\\.BotWithUs\\scripts")
        }

        tasks.named<Jar>("jar") {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            finalizedBy(copyJar)
        }
    }
}
