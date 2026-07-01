plugins {
    // To optionally create a shadow/fat jar that bundle up any non-core dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
}

val extensionVersion = providers.gradleProperty("releaseVersion")
    .orElse("0.2.1-SNAPSHOT")
    .get()

qupathExtension {
    name = "qupath-extension-cideconvolve"
    group = "nl.amsterdamumc"
    version = extensionVersion
    description = "QuPath extension to run CIDeconvolve from an existing Python environment"
    automaticModule = "nl.amsterdamumc.qupath.extension.cideconvolve"
}

tasks.shadowJar {
    archiveClassifier.set("")
}

dependencies {

    // Main dependencies for most QuPath extensions
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)

    // For testing
    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}
