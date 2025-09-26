import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

tasks.matching { it.name == "copyJar" }.configureEach { enabled = false }
tasks.withType<Jar>().configureEach { enabled = false }
tasks.withType<Test>().configureEach { enabled = false }
