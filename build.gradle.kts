import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

group = "uberith"
version = "1.0.0"

allprojects {
    if (project.path == ":script-api") return@allprojects
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

    // Repositories are managed centrally in settings.gradle.kts

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
            implementation("net.botwithus.kxapi:kxapi:0.1-SNAPSHOT")
            implementation("botwithus.navigation:nav-api:1.+")
            implementation("com.google.code.gson:gson:2.10.1")
            add("includeInJar", "net.botwithus.xapi:xapi:2.0.+")
            add("includeInJar", "net.botwithus.kxapi:kxapi:0.1-SNAPSHOT")

            if (hasKotlin) {
                // Compile against kotlin-stdlib 2.2.0 (platform provides this at runtime)
                compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                // Use the Jvm variant to avoid duplicate module names on the module path
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.1")
                testImplementation(kotlin("test"))
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
                // Kotlin 2.2 language and API, target JVM 24
                languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
                apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)

                jvmTarget.set(JvmTarget.JVM_24)
                freeCompilerArgs.add("-Xjdk-release=24")
            }
        }

        // Ensure module-info.java (Java) sees Kotlin classes like com.uberith.uberchop.UberChop
        tasks.named<JavaCompile>("compileJava") {
            dependsOn(tasks.named("compileKotlin"))
        }
    }

    // Avoid bringing both kotlinx-coroutines-core and -core-jvm onto the module path
    configurations.configureEach {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        // Pin only stdlib artifacts to 2.2.0; leave Build Tools artifacts to the KGP version
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                if (requested.name == "kotlin-stdlib" ||
                    requested.name.startsWith("kotlin-stdlib-") ||
                    requested.name == "kotlin-reflect") {
                    useVersion("2.2.0")
                }
            }
        }
        resolutionStrategy.force(
            "org.jetbrains.kotlin:kotlin-stdlib:2.2.0",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.0",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.0",
            "org.jetbrains.kotlin:kotlin-reflect:2.2.0"
        )
    }

    if (hasSources) {
        tasks.withType<Jar>().configureEach {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            from(configurations["includeInJar"].map { zipTree(it) }) {
                // Exclude module descriptors and signature files from shaded jars only
                exclude("module-info.class")
                exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
                // Never embed kotlin runtime in scripts
                exclude("kotlin/**", "META-INF/*.kotlin_module", "META-INF/kotlin*")
            }
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

    // Always prevent creating/copying a root jar (root name contains a dot) regardless of sources
    if (project == rootProject) {
        tasks.withType<Jar>().configureEach { enabled = false }
        tasks.matching { it.name == "copyJar" }.configureEach { enabled = false }
        // Also catch tasks created later
        tasks.whenTaskAdded {
            if (name == "jar" || name == "copyJar") enabled = false
        }
    }
}
