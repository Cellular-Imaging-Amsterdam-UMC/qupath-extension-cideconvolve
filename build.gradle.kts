plugins {
    // To optionally create a shadow/fat jar that bundle up any non-core dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
}

qupathExtension {
    name = "qupath-extension-cideconvolve"
    group = "nl.amsterdamumc"
    version = "0.1.0-SNAPSHOT"
    description = "QuPath extension to run CIDeconvolve from an existing Python environment"
    automaticModule = "nl.amsterdamumc.qupath.extension.cideconvolve"
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
