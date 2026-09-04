package com.trustlens.threatintel

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider credentials, read from `BuildConfig` fields populated at build time from the
 * gitignored `secrets.properties`.
 *
 * The keys are never checked in, never written to source, and never logged — including in
 * the failure paths of the adapters, which report *that* a credential was rejected without
 * ever echoing it.
 *
 * A missing key is a first-class, fully supported state, not an error to work around. It
 * yields [com.trustlens.domain.ReputationVerdict.UNKNOWN] from the provider and a visible
 * "external verification unavailable" on screen. That is the whole point: without a key,
 * TrustLens says it does not know, and never pretends the domain is clean.
 */
@Singleton
class BuildConfigCredentials @Inject constructor() : ProviderCredentials {

    override val virusTotalApiKey: String?
        get() = BuildConfig.VIRUSTOTAL_API_KEY.takeIf { it.isNotBlank() }

    override val urlhausAuthKey: String?
        get() = BuildConfig.URLHAUS_AUTH_KEY.takeIf { it.isNotBlank() }
}
