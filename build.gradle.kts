plugins {
    // To optionally create a shadow/fat jar that bundle up any non-core dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
}

val extensionVersion = providers.gradleProperty("releaseVersion")
    .orElse(providers.fileContents(layout.projectDirectory.file("version.txt")).asText.map { it.trim().removePrefix("v") })
    .get()

repositories {
    maven("https://maven.scijava.org/content/groups/public") {
        content { includeGroup("ome") }
    }
}

qupathExtension {
    name = "qupath-extension-cideconvolve"
    group = "nl.amsterdamumc"
    version = extensionVersion
    description = "CI Deconvolve for QuPath"
    automaticModule = "nl.amsterdamumc.qupath.extension.cideconvolve"
}

tasks.withType<Jar>().configureEach {
    archiveBaseName.set("qupath-ci-deconvolve")
}

tasks.shadowJar {
    archiveClassifier.set("")
}

dependencies {

    // Main dependencies for most QuPath extensions
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)
    compileOnly("net.java.dev.jna:jna:5.17.0")
    compileOnly("ome:formats-bsd:8.4.0") { isTransitive = false }
    compileOnly("ome:formats-api:8.4.0") { isTransitive = false }
    compileOnly("org.openmicroscopy:ome-common:6.1.2") { isTransitive = false }
    compileOnly("com.esotericsoftware:kryo:5.4.0") { isTransitive = false }

    // For testing
    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}
