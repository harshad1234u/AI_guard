plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.trustlens"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.trustlens"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ONNX Runtime ships a ~30 MB native library per ABI, and ML Kit adds ~11 MB more.
        // Building all four ABIs produced a 204 MB APK, ~126 MB of which was ONNX Runtime
        // duplicated for architectures no target device uses. Every phone in scope — the
        // iQOO 15 and the POCO validation device included — is arm64-v8a.
        //
        // Add "x86_64" here if you need to run on an emulator; it costs about 54 MB.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    /*
     * Demo signing only. A release APK must be signed to install, and this build exists to be
     * side-loaded onto a validation handset at a hackathon — not published. It reuses the local
     * debug keystore, which every Android SDK install generates and which is not a secret.
     *
     * Before this app is ever distributed, replace this with a real upload key held outside the
     * repository. Do not commit a production keystore or its password.
     */
    signingConfigs {
        create("demo") {
            storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // R8 is deliberately off. It would shrink the APK and make string extraction slightly
            // more tedious, but it is not a secrecy mechanism, and an untested shrink of a build
            // using Hilt, ONNX Runtime and two ML Kit models is not something to discover on
            // stage. Turn it on only with time to run the full device regression afterwards.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("demo")
        }
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
    implementation(project(":domain"))
    implementation(project(":riskengine"))
    implementation(project(":investigation"))
    implementation(project(":core"))
    implementation(project(":ai"))
    implementation(project(":threatintel"))

    implementation(project(":feature:home"))
    implementation(project(":feature:lensscan"))
    implementation(project(":feature:qrscan"))
    implementation(project(":feature:urlcheck"))
    implementation(project(":feature:textcheck"))
    implementation(project(":feature:voicecheck"))
    implementation(project(":feature:result"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // The real bundled decoder, exercised on the device against a real QR image.
    androidTestImplementation(libs.mlkit.barcode.scanning)
    androidTestImplementation(libs.zxing.core)
}

tasks.withType<Test>().configureEach { testLogging { showStandardStreams = true } }
