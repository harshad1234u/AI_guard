package com.trustlens.di

import android.content.Context
import com.trustlens.ai.ImageTextRecogniser
import com.trustlens.ai.MlKitTextRecogniser
import com.trustlens.ai.OnnxScamClassifier
import com.trustlens.domain.AiClassifier
import com.trustlens.domain.IndicatorExtractor
import com.trustlens.domain.QrPayloadClassifier
import com.trustlens.domain.ReputationSource
import com.trustlens.domain.RiskAssessor
import com.trustlens.domain.UrlAnalyzer
import com.trustlens.threatintel.BuildConfigCredentials
import com.trustlens.threatintel.HttpTransport
import com.trustlens.threatintel.OkHttpTransport
import com.trustlens.threatintel.ProviderCredentials
import com.trustlens.threatintel.UrlhausSource
import com.trustlens.threatintel.VirusTotalSource
import com.trustlens.investigation.DeterministicQrPayloadClassifier
import com.trustlens.investigation.DeterministicUrlAnalyzer
import com.trustlens.investigation.RuleBasedIndicatorExtractor
import com.trustlens.riskengine.RiskEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The composition root: the single place that decides which implementation backs each
 * contract.
 *
 * Swapping the mock classifier for the real ONNX CPU one — and later an NPU one — is a change
 * to [ClassifierModule] alone. No screen, use case or engine moves.
 */
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideRiskAssessor(): RiskAssessor = RiskEngine()

    @Provides
    @Singleton
    fun provideIndicatorExtractor(): IndicatorExtractor = RuleBasedIndicatorExtractor()

    @Provides
    @Singleton
    fun provideUrlAnalyzer(): UrlAnalyzer = DeterministicUrlAnalyzer()

    @Provides
    @Singleton
    fun provideQrPayloadClassifier(): QrPayloadClassifier = DeterministicQrPayloadClassifier()
}

@Module
@InstallIn(SingletonComponent::class)
object ClassifierModule {

    /**
     * The trained model: TF-IDF features computed in Kotlin, logistic regression executed
     * by ONNX Runtime on the CPU.
     *
     * This binding is the whole reason [AiClassifier] is an interface. Replacing the mock
     * with a real model changed this one function; no screen, use case or engine moved, and
     * the same will be true when an NPU-backed implementation arrives.
     *
     * [MockScamClassifier] is deliberately kept in `:ai` — it is still the fixture that
     * makes the pure-JVM pipeline tests fast and deterministic — but it is no longer on any
     * production path.
     */
    @Provides
    @Singleton
    fun provideAiClassifier(@ApplicationContext context: Context): AiClassifier =
        OnnxScamClassifier(context)

    /** Bundled ML Kit OCR: on-device, no upload, no first-run model download. */
    @Provides
    @Singleton
    fun provideImageTextRecogniser(impl: MlKitTextRecogniser): ImageTextRecogniser = impl
}

/**
 * The external reputation providers.
 *
 * This is the only place networking enters the graph, and it is a `Set` on purpose: adding
 * or removing a provider is one line here, the orchestrator queries whatever it is given,
 * and no other code learns that the set changed.
 *
 * Providers are constructed unconditionally, even with no credentials configured. A keyless
 * provider is not a broken provider — it answers UNKNOWN, the report is marked degraded, and
 * the screen says external verification was unavailable. Omitting it instead would make the
 * app quietly stop mentioning that anything was missing.
 */
@Module
@InstallIn(SingletonComponent::class)
object ThreatIntelModule {

    @Provides
    @Singleton
    fun provideHttpTransport(impl: OkHttpTransport): HttpTransport = impl

    @Provides
    @Singleton
    fun provideProviderCredentials(impl: BuildConfigCredentials): ProviderCredentials = impl

    @Provides
    @Singleton
    fun provideReputationSources(
        transport: HttpTransport,
        credentials: ProviderCredentials,
    ): Set<ReputationSource> = setOf(
        VirusTotalSource(transport, credentials),
        UrlhausSource(transport, credentials),
    )
}
