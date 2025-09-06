plugins {
    // Apply but don't activate; subprojects will apply Kotlin JVM
    kotlin("jvm") version "2.2.0" apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    group = "com.botwithus.examples"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://nexus.botwithus.net/repository/maven-releases/")
        maven("https://nexus.botwithus.net/repository/maven-snapshots/")
        maven("https://nexus.botwithus.net/repository/maven-public/")
    }

    // Java toolchain for JDK 24 (compile and target Java 24)
    extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(24))
    }

    // Ensure both Java and Kotlin compile to the same target (24)
    tasks.withType<JavaCompile>().configureEach {
        // Use JDK 24 toolchain and target Java 24 bytecode
        options.release.set(24)
    }

    // Kotlin bytecode target (runs on JDK 24)
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
        }
    }

    // Ensure Kotlin compiler runs with JDK 24 toolchain
    extensions.configure(org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java) {
        jvmToolchain(24)
    }

    // Make dependency resolution request JVM 24 variants
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
}
