plugins {
    kotlin("multiplatform") version "1.9.24"
    id("com.android.library") version "8.5.1"
    kotlin("plugin.serialization") version "1.9.24"
}

kotlin {
    androidTarget()
    jvm("desktop")

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

        // Shared JVM code (Android/Desktop)
        val jvmMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("androidx.datastore:datastore-preferences:1.1.1")
            }
        }
        val jvmTest by creating {
            dependsOn(commonTest)
            dependencies {
                implementation("junit:junit:4.13.2")
                implementation("androidx.datastore:datastore-preferences:1.1.1")
            }
        }

        val androidMain by getting { dependsOn(jvmMain) }
        val androidUnitTest by getting { dependsOn(jvmTest) }

        val desktopMain by getting { dependsOn(jvmMain) }
        val desktopTest by getting { dependsOn(jvmTest) }
    }
}

android {
    namespace = "com.uberchop.scriptapi.settings"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

