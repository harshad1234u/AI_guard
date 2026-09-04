// :core — shared Android infrastructure (Keystore access, network plumbing, dispatchers).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.trustlens.core"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
