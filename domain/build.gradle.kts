// :domain — pure Kotlin/JVM.
//
// Holds every contract in TrustLens: the InvestigationContext model, Evidence, RiskResult,
// and the adapter interfaces (AiClassifier, ReputationSource, ...). Implementations live in
// :ai and :threatintel. Keeping this module Android-free means :riskengine and
// :investigation stay pure JVM too, so the deterministic core tests run in milliseconds
// without an emulator.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
