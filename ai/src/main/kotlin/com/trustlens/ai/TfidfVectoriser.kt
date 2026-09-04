package com.trustlens.ai

import kotlin.math.sqrt

/**
 * The trained vocabulary: one entry per feature, in the model's feature-index order.
 *
 * [terms] maps a term to its column index; [idf] holds the inverse-document-frequency
 * weight for that column, both taken verbatim from the fitted scikit-learn vectoriser.
 */
class TfidfVocabulary(
    val terms: Map<String, Int>,
    val idf: FloatArray,
) {
    val featureCount: Int get() = idf.size

    companion object {
        /**
         * Parses the exported `term<TAB>idf` asset. Line number is the feature index, so
         * order is load-bearing and the file must never be sorted or deduplicated.
         */
        fun parse(lines: Sequence<String>): TfidfVocabulary {
            val terms = HashMap<String, Int>()
            val idf = ArrayList<Float>()

            lines.forEach { line ->
                if (line.isEmpty()) return@forEach
                val tab = line.lastIndexOf('\t')
                if (tab <= 0) return@forEach
                val weight = line.substring(tab + 1).toFloatOrNull() ?: return@forEach
                terms[line.substring(0, tab)] = idf.size
                idf.add(weight)
            }

            return TfidfVocabulary(terms, idf.toFloatArray())
        }
    }
}

/**
 * A reimplementation of scikit-learn's `TfidfVectorizer` for the exact configuration this
 * model was trained with: `lowercase=True`, `ngram_range=(1, 2)`, `sublinear_tf=False`,
 * `norm='l2'`, and the default token pattern.
 *
 * This exists because the vectoriser could not be exported into the ONNX graph faithfully.
 * skl2onnx lowers it onto an ONNX `Tokenizer` that forms n-grams from *every* token, while
 * scikit-learn first discards tokens shorter than two characters and forms n-grams from
 * what survives — so a message like "Can u get pic msgs" yields the bigram `can get` in
 * scikit-learn and `can u` in ONNX. Measured over the held-out set, that diverged on 281 of
 * 1,115 messages and flipped one label, which is why this code exists rather than a
 * convenient one-line export.
 *
 * Correctness here is therefore not a matter of opinion: `TfidfVectoriserParityTest` pins
 * the output against vectors produced by scikit-learn itself.
 */
class TfidfVectoriser(private val vocabulary: TfidfVocabulary) {

    /**
     * Produces the dense feature vector for [text].
     *
     * Dense rather than sparse because that is what the ONNX model's input expects, and at
     * roughly twelve thousand floats the allocation is trivial next to the inference.
     */
    fun transform(text: String): FloatArray {
        val vector = FloatArray(vocabulary.featureCount)
        val tokens = tokenise(text)
        if (tokens.isEmpty()) return vector

        // Raw term counts: unigrams, then the bigrams formed from the surviving tokens.
        for (index in tokens.indices) {
            vocabulary.terms[tokens[index]]?.let { vector[it] += 1f }
            if (index + 1 < tokens.size) {
                val bigram = tokens[index] + " " + tokens[index + 1]
                vocabulary.terms[bigram]?.let { vector[it] += 1f }
            }
        }

        // count * idf, then L2 normalisation — the order scikit-learn applies them in.
        var sumOfSquares = 0.0
        for (index in vector.indices) {
            if (vector[index] != 0f) {
                val weighted = vector[index] * vocabulary.idf[index]
                vector[index] = weighted
                sumOfSquares += weighted.toDouble() * weighted.toDouble()
            }
        }
        if (sumOfSquares > 0.0) {
            val norm = sqrt(sumOfSquares).toFloat()
            for (index in vector.indices) {
                if (vector[index] != 0f) vector[index] = vector[index] / norm
            }
        }

        return vector
    }

    /**
     * scikit-learn's default analyzer: lowercase, then every match of `(?u)\b\w\w+\b`.
     *
     * The two-character minimum is the detail that made the ONNX export unfaithful, so it is
     * spelled out here rather than left implicit in a regex someone might "simplify".
     */
    private fun tokenise(text: String): List<String> =
        TOKEN_PATTERN.findAll(text.lowercase())
            .map { it.value }
            .filter { it.length >= MIN_TOKEN_LENGTH }
            .toList()

    private companion object {
        /**
         * Maximal runs of word characters, Unicode-aware on every platform.
         *
         * scikit-learn's default pattern is `(?u)\b\w\w+\b`. The obvious transliteration —
         * `Pattern.compile("\\b\\w\\w+\\b", UNICODE_CHARACTER_CLASS)` — compiles on the
         * desktop JVM and throws `IllegalArgumentException: UNICODE_CHARACTER_CLASS flag
         * not supported` on Android, whose regex engine is ICU-backed. That failure was
         * found on a physical device, not by the JVM tests, which is precisely why this
         * comment exists.
         *
         * `[\p{L}\p{N}_]+` plus a length filter is equivalent and portable: greedy matching
         * already yields maximal runs, so keeping those of length two or more reproduces
         * exactly what `\b\w\w+\b` selects, without relying on a word-boundary escape whose
         * Unicode semantics differ between platforms.
         */
        val TOKEN_PATTERN = Regex("[\\p{L}\\p{N}_]+")

        const val MIN_TOKEN_LENGTH = 2
    }
}
