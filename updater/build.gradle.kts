plugins {
    kotlin("jvm")
}

group = "de.pantastix"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {}

kotlin {
    jvmToolchain(21)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "de.pantastix.UpdaterKt"
    }
    // Dieser Teil bündelt alle Abhängigkeiten in die JAR
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}