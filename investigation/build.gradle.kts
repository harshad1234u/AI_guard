// :investigation — pure Kotlin/JVM.
//
// The InvestigationAgent orchestrates: it extracts indicators, decides which tools to call,
// correlates results and builds Evidence. It depends only on :domain interfaces, so it never
// sees a concrete model or threat-intelligence provider.
//
// Security invariant (Principle 3): this module exposes NO action capability. There is no
// openUrl, no pay, no send. The agent cannot execute what it cannot name.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
