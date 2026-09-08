// :core — the TrustLens design system, plus shared Android infrastructure.
//
// PRD v5.3 §3 lists this module as "shared UI/theme". Until Phase 17 it held no source at
// all and every screen styled itself, which is how five surfaces ended up with five private
// copies of the same `Notice` card. The design system lives here so that a Critical verdict,
// a disclosure and a safe action look identical wherever they are shown — the consistency is
// a trust property, not a cosmetic one.
//
// It contains no analysis, no scoring and no networking. It reads `RiskLevel` from :domain to
// resolve a band's colour and nothing else.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.trustlens.core"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // `api` rather than `implementation`: these types appear in this module's public
    // composable signatures, so every consumer needs them on its compile classpath.
    api(project(":domain"))

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)

    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
