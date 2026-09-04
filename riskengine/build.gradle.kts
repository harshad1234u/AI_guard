// :riskengine — pure Kotlin/JVM, deterministic, no IO, no coroutines.
//
// Architecture Rule 3: the final risk score is calculated by deterministic code, never by a
// model. This module depends on :domain only — it cannot reach :ai or :threatintel, so that
// rule is enforced by the dependency graph rather than by convention.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

dependencies {
    implementation(project(":domain"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
