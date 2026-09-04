// :ai — concrete implementations of the AiClassifier / VisionAdapter / VoiceAdapter
// interfaces declared in :domain.
//
// Backend policy: NPU -> GPU -> CPU with runtime capability detection. Until the iQOO 15 is
// available, only the CPU path (ONNX Runtime) and the mock adapter are real. The app must
// work correctly with NPU and GPU both unavailable, so nothing here may be a hard dependency
// of a P0 flow.
//
// ONNX Runtime and ML Kit dependencies are added in the phases that introduce them.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.trustlens.ai"
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
    implementation(project(":core"))
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.javax.inject)

    // Bundled on-device OCR. No image ever leaves the device and there is no
    // first-run model download to stall a scan.
    implementation(libs.mlkit.text.recognition)

    // Runs the trained logistic regression on the CPU. The same runtime is the swap
    // point for an NPU execution provider later — no caller changes when that happens.
    implementation(libs.onnxruntime.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
