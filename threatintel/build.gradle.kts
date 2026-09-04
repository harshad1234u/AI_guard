import java.util.Properties

// :threatintel — ReputationSource implementations (VirusTotal, URLhaus).
//
// Every lookup resolves to one of CONFIRMED / SUSPICIOUS / NO_DETECTION / UNKNOWN / ERROR /
// CONFLICTING. Principle 2: a failed, rate-limited or empty lookup returns UNKNOWN and is
// never allowed to become SAFE.
//
// This is the only module permitted to make a network request.
plugins {
    alias(libs.plugins.android.library)
}

/**
 * Provider credentials come from a gitignored `secrets.properties` at the repository root:
 *
 *     VIRUSTOTAL_API_KEY=...
 *     URLHAUS_AUTH_KEY=...
 *
 * A missing file or a missing key is a supported state — the build succeeds and every
 * lookup reports UNKNOWN. That is deliberate: the app must be honest about not knowing
 * rather than refuse to build, and no contributor should need a key to compile TrustLens.
 */
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String): String = (secrets.getProperty(name) ?: "").trim()

android {
    namespace = "com.trustlens.threatintel"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        buildConfigField("String", "VIRUSTOTAL_API_KEY", "\"${secret("VIRUSTOTAL_API_KEY")}\"")
        buildConfigField("String", "URLHAUS_AUTH_KEY", "\"${secret("URLHAUS_AUTH_KEY")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // OkHttp's platform initialiser calls android.util.Log, which throws in a plain
            // JVM unit test. Returning defaults keeps that from printing a stack trace that
            // looks like a failure but is not one.
            isReturnDefaultValues = true
        }
    }
}

/**
 * Forwards the opt-in flag for [LiveProviderVerificationTest]. Without it, live tests are
 * skipped, so an ordinary `./gradlew test` never touches a provider's API or rate limit:
 *
 *     ./gradlew :threatintel:test -Dtrustlens.live=true
 */
tasks.withType<Test>().configureEach {
    systemProperty("trustlens.live", System.getProperty("trustlens.live") ?: "false")
    testLogging { showStandardStreams = true }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.javax.inject)

    implementation(libs.okhttp)
    // JsonElement parsing only — no @Serializable classes, so the serialization compiler
    // plugin is not needed and the build stays as it was.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
