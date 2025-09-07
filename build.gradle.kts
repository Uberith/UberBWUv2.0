plugins {
    // Apply but don't activate; subprojects will apply Kotlin JVM
    // Note: plugin version must be a constant or resolved via pluginManagement; keep in sync with kotlin.version
    kotlin("jvm") version "2.2.0" apply false
}

// Repositories for root project (used by detached configurations/tasks like cacheImGui)
repositories {
    mavenCentral()
    maven("https://nexus.botwithus.net/repository/maven-releases/")
    maven("https://nexus.botwithus.net/repository/maven-snapshots/")
    maven("https://nexus.botwithus.net/repository/maven-public/")
    // Local fallbacks for offline use
    maven { url = uri(layout.projectDirectory.dir("external/maven").asFile.toURI()) }
    flatDir { dirs(layout.projectDirectory.dir("external/lib").asFile) }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    group = "com.uberith"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://nexus.botwithus.net/repository/maven-releases/")
        maven("https://nexus.botwithus.net/repository/maven-snapshots/")
        maven("https://nexus.botwithus.net/repository/maven-public/")
        // Local fallback repos for offline use
        maven { url = uri(rootProject.layout.projectDirectory.dir("external/maven").asFile.toURI()) }
        flatDir { dirs(rootProject.layout.projectDirectory.dir("external/lib").asFile) }
    }

    // Java toolchain for JDK 24 (runtime), compile to Java 21 bytecode for Kotlin compatibility
    extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(24))
    }

    // Ensure Java targets Java 21 bytecode (Kotlin 2.2 max target is JVM_21)
    tasks.withType<JavaCompile>().configureEach {
        // Use JDK 24 toolchain and target Java 21 bytecode
        options.release.set(21)
    }

    // Kotlin bytecode target. Kotlin 2.2 maximum is JVM_21; this runs fine on JDK 24.
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            allWarningsAsErrors.set(true)
        }
    }

    // Ensure Kotlin compiler runs with JDK 24 toolchain
    extensions.configure(org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java) {
        jvmToolchain(24)
    }

    // Request JVM 24 variants to match upstream BotWithUs artifacts while compiling our code to 21 bytecode.
    configurations.matching { it.name in setOf(
        "compileClasspath",
        "runtimeClasspath",
        "testCompileClasspath",
        "testRuntimeClasspath"
    ) }.configureEach {
        attributes.attribute(
            org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            24
        )
    }

    // Configuration to include selected deps inside jar (like previous includeInJar)
    val includeInJar by configurations.creating {
        isTransitive = false
    }

    // Copy jar to BotWithUs scripts directory after build
    val copyJar by tasks.register<Copy>("copyJar") {
        from(tasks.named<Jar>("jar"))
        into("${System.getProperty("user.home")}/.BotWithUs/scripts")
    }

    tasks.named<Jar>("jar").configure {
        // Unpack includeInJar artifacts into the jar
        from({ includeInJar.map { zipTree(it) } })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        finalizedBy(copyJar)
    }

    // Kotlin BOM + force alignment to a single version across the build
    val kotlinVersion = (findProperty("kotlin.version") as String?) ?: "2.2.0"

    configurations.configureEach {
        resolutionStrategy {
            // Force a single Kotlin on the classpath to avoid metadata mismatches
            force(
                "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion",
                "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion"
            )
        }
    }

    // Add Gradle API to all modules and align Kotlin libs via BOM
    dependencies {
        add("implementation", gradleApi())
        add("implementation", platform("org.jetbrains.kotlin:kotlin-bom:$kotlinVersion"))
    }
}

// Aggregate task to fail fast on any compile error across subprojects
val strictCompile = tasks.register("strictCompile") {
    group = "verification"
    description = "Compiles all subprojects (Kotlin/Java) to fail fast on errors."
}

// Wire each subproject's main compile tasks into the aggregate
subprojects {
    tasks.matching { it.name == "compileKotlin" || it.name == "compileJava" }.configureEach {
        rootProject.tasks.named("strictCompile").configure {
            dependsOn(this@configureEach)
        }
    }
}

// Task to resolve net.botwithus.imgui:imgui and copy the jar to external for offline use
val cacheImGui by tasks.registering(Copy::class) {
    group = "dependency cache"
    description = "Resolves net.botwithus.imgui:imgui and copies jar into external/maven and external/lib"
    val imguiVersion = (findProperty("imgui.version") as String?)
        ?: (findProperty("IMGUI_VERSION") as String?)
        ?: "1.0.2-20250818.161536-3"
    val dep = dependencies.create("net.botwithus.imgui:imgui:$imguiVersion")
    val cfg = configurations.detachedConfiguration(dep).also { it.isTransitive = false }
    val files = cfg.resolve()
    val jar = files.firstOrNull()
    if (jar != null) {
        val groupPath = "net/botwithus/imgui"
        val destMaven = layout.projectDirectory.dir("external/maven/$groupPath/imgui/$imguiVersion").asFile
        val destFlat = layout.projectDirectory.dir("external/lib").asFile
        from(jar)
        into(destMaven)
        doLast {
            destFlat.mkdirs()
            copy {
                from(jar)
                into(destFlat)
            }
        }
    }
}
