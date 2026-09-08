package com.trustlens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.trustlens.feature.textcheck.TextCheckScreen
import com.trustlens.feature.textcheck.TextCheckViewModel
import com.trustlens.core.design.TrustLensTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Check selected text, launched from another app's text-selection toolbar.
 *
 * ## Why this is a separate activity
 *
 * `ACTION_PROCESS_TEXT` was first declared on [MainActivity], and on the POCO X6 that
 * registered correctly — `cmd package query-activities` listed it among the nine handlers on
 * the device — yet it never appeared in Gmail's or Chrome's selection menu. The other eight
 * handlers on that device all had one thing in common:
 *
 * ```
 * ProcessTextGatewayActivity   TrampolineActivity      SelectedTextSearchActivity
 * SelectToSpeakPopupActivity   DeepLinkActivity        TapToTranslateActivity
 * ProcessTextSearch            TextProcessorActivity
 * ```
 *
 * Every one is a dedicated activity. TrustLens was the only handler declaring the filter on
 * its `LAUNCHER` activity, and the only one the toolbars skipped. So the filter moved here.
 * That is a device-observed reason, not a stylistic one.
 *
 * ## What it is
 *
 * A thin host for the Check Message screen. It owns no analysis: the selected text goes to the
 * same [TextCheckViewModel] and the same `AnalyzeTextUseCase` that a pasted message uses, so a
 * highlighted fragment and pasted text reach an identical verdict.
 *
 * ## What it does not do
 *
 * **It never returns a result.** `ACTION_PROCESS_TEXT` lets a handler write modified text back
 * into the source app by calling `setResult`; TrustLens is strictly read-only and deliberately
 * never does, so nothing it produces can alter the user's email, message or web page.
 *
 * **It starts nothing.** There is no `startActivity`, no `ACTION_VIEW`, no `WebView` here — the
 * selected text is untrusted content (Principle 1), analysed and displayed, never obeyed.
 */
@AndroidEntryPoint
class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val selection = IncomingTextReader.read(
            action = intent?.action,
            processText = intent?.getCharSequenceExtra(android.content.Intent.EXTRA_PROCESS_TEXT),
        ) as? IncomingText.Selection

        setContent {
            TrustLensTheme {
                val viewModel: TextCheckViewModel = hiltViewModel()

                LaunchedEffect(selection) {
                    selection?.let { viewModel.onSelectedText(it.text, it.originalLength) }
                }

                // Back closes this window and returns the user to where they selected the
                // text, which is the only sensible destination: they never left that app.
                TextCheckScreen(onBack = { finish() }, viewModel = viewModel)
            }
        }
    }
}
