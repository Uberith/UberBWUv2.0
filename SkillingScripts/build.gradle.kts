// Parent aggregator project: do not produce or copy a jar
tasks.matching { it.name == "copyJar" }.configureEach { enabled = false }
tasks.withType<Jar>().configureEach { enabled = false }
tasks.withType<Test>().configureEach { enabled = false }

