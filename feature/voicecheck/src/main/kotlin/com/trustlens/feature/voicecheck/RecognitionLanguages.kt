package com.trustlens.feature.voicecheck

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Which languages this device can actually recognise **on-device**, asked rather than assumed.
 *
 * Live Guard is aimed at Indian scam calls, which are rarely conducted in one language. The
 * platform can be told to listen for several and to switch between them mid-sentence, but
 * only for languages whose speech model is present: `EXTRA_ENABLE_LANGUAGE_SWITCH` documents
 * that "the corresponding language models must be downloaded to support the switch.
 * Otherwise, the recognizer will report an error on a switch failure."
 *
 * A missing Hindi model is the dangerous case, and it is dangerous in a quiet way. The
 * recogniser does not refuse Hindi speech — it renders it as English-sounding nonsense, which
 * then scores as harmless. So this is asked before listening starts and the answer is shown
 * to the user, rather than being discovered as a low score during a real scam call.
 *
 * All of it is API 33+. Below that, [Support.queried] is false and Live Guard says only that
 * it is listening in the phone's own language — which is the truth, and better than a claim.
 */
internal object RecognitionLanguages {

    /** BCP 47 tags Live Guard asks the recogniser to listen for, in preference order. */
    val REQUESTED: List<String> = listOf("hi-IN", "en-IN")

    /** What the platform reported. Empty lists mean "not asked", not "nothing available". */
    data class Support(
        val installedOnDevice: List<String> = emptyList(),
        val supportedOnDevice: List<String> = emptyList(),
        val pendingOnDevice: List<String> = emptyList(),
        /** False when the device predates the API, so absence is never read as a "no". */
        val queried: Boolean = false,
    ) {
        fun isInstalled(tag: String): Boolean = installedOnDevice.any { it.matchesLanguage(tag) }

        fun isDownloadable(tag: String): Boolean =
            !isInstalled(tag) && supportedOnDevice.any { it.matchesLanguage(tag) }

        fun isPending(tag: String): Boolean = pendingOnDevice.any { it.matchesLanguage(tag) }
    }

    /**
     * Asks the platform what it has, off the main thread, and reports back on it.
     *
     * Creating a recogniser to ask does not start listening and does not touch the
     * microphone; it is destroyed as soon as the answer arrives.
     */
    fun query(context: Context, onResult: (Support) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            onResult(Support())
            return
        }

        val recognizer = runCatching {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }.getOrElse {
            onResult(Support())
            return
        }

        var settled = false
        fun finish(support: Support) {
            if (settled) return
            settled = true
            runCatching { recognizer.destroy() }
            onResult(support)
        }

        runCatching {
            recognizer.checkRecognitionSupport(
                recognitionIntent(),
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        finish(
                            Support(
                                installedOnDevice = support.installedOnDeviceLanguages,
                                supportedOnDevice = support.supportedOnDeviceLanguages,
                                pendingOnDevice = support.pendingOnDeviceLanguages,
                                queried = true,
                            ),
                        )
                    }

                    override fun onError(error: Int) = finish(Support())
                },
            )
        }.onFailure { finish(Support()) }
    }

    /**
     * Asks the system to fetch a language pack.
     *
     * This downloads a *speech model*, not user data, and nothing about the user or their
     * calls is transmitted. The download is performed by the system recognition service; the
     * user is told what is happening before it is offered.
     */
    fun requestDownload(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val recognizer = runCatching {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }.getOrNull() ?: return

        runCatching {
            recognizer.triggerModelDownload(
                recognitionIntent().putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag),
                context.mainExecutor,
                object : ModelDownloadListener {
                    override fun onProgress(completedPercent: Int) = Unit
                    override fun onSuccess() {
                        runCatching { recognizer.destroy() }
                    }

                    override fun onScheduled() = Unit
                    override fun onError(error: Int) {
                        runCatching { recognizer.destroy() }
                    }
                },
            )
        }.onFailure { runCatching { recognizer.destroy() } }
    }

    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
        }

    /** A readable name for a BCP 47 tag, for display only. */
    fun displayName(tag: String): String =
        runCatching { Locale.forLanguageTag(tag).getDisplayName(Locale.ENGLISH) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: tag

    /**
     * Compares only the language subtag, so `hi-IN` reported as `hi_IN`, `hi` or `hi-Deva-IN`
     * all count as Hindi. Recognition services are not consistent about this and a string
     * comparison would silently report Hindi as missing when it is installed.
     */
    private fun String.matchesLanguage(other: String): Boolean =
        replace('_', '-').substringBefore('-').equals(
            other.replace('_', '-').substringBefore('-'),
            ignoreCase = true,
        )
}
