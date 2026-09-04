package com.trustlens.ai

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.math.abs
import org.junit.Test

/**
 * Pins the Kotlin vectoriser to scikit-learn's output.
 *
 * The vectoriser had to be reimplemented by hand because the ONNX export of
 * `TfidfVectorizer` was measurably unfaithful (see [TfidfVectoriser]'s documentation). A
 * hand reimplementation of someone else's numerical code is only trustworthy if it is
 * checked against that code, so these tests compare against vectors produced by
 * scikit-learn itself and exported by `tools/train_classifier.py`.
 *
 * If a future retrain changes the vocabulary, regenerate `parity-vectors.tsv` in the same
 * run — the two files are a matched pair.
 */
class TfidfVectoriserParityTest {

    private data class Golden(
        val text: String,
        val scamProbability: Double,
        val expected: Map<Int, Double>,
    )

    private val vocabulary: TfidfVocabulary by lazy {
        File("src/main/assets/scam-clf-vocab.tsv").useLines { TfidfVocabulary.parse(it) }
    }

    private val vectoriser by lazy { TfidfVectoriser(vocabulary) }

    private val golden: List<Golden> by lazy {
        File("src/test/resources/parity-vectors.tsv").readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val (text, probability, entries) = line.split('\t').let {
                    Triple(it[0], it[1], it.getOrElse(2) { "" })
                }
                Golden(
                    text = text,
                    scamProbability = probability.toDouble(),
                    expected = entries.split(',')
                        .filter { it.isNotBlank() }
                        .associate { entry ->
                            val (index, value) = entry.split(':')
                            index.toInt() to value.toDouble()
                        },
                )
            }
    }

    @Test
    fun `the exported vocabulary loads with the expected shape`() {
        assertThat(vocabulary.featureCount).isEqualTo(12_007)
        assertThat(vocabulary.terms).hasSize(12_007)
        // Every term maps to a distinct, in-range column.
        assertThat(vocabulary.terms.values.toSet()).hasSize(12_007)
        assertThat(vocabulary.terms.values.max()).isEqualTo(12_006)
    }

    @Test
    fun `bigrams are present in the vocabulary`() {
        assertThat(vocabulary.terms.keys.any { ' ' in it }).isTrue()
    }

    @Test
    fun `there are golden vectors to check against`() {
        assertThat(golden).hasSize(8)
    }

    @Test
    fun `every non-zero feature matches scikit-learn`() {
        golden.forEach { sample ->
            val actual = vectoriser.transform(sample.text)

            sample.expected.forEach { (index, expectedValue) ->
                assertThat(abs(actual[index] - expectedValue))
                    .isLessThan(TOLERANCE)
            }
        }
    }

    @Test
    fun `no feature is set that scikit-learn left at zero`() {
        golden.forEach { sample ->
            val actual = vectoriser.transform(sample.text)
            val unexpected = actual.indices.filter {
                actual[it] != 0f && it !in sample.expected
            }

            assertThat(unexpected).isEmpty()
        }
    }

    @Test
    fun `the number of active features matches scikit-learn exactly`() {
        golden.forEach { sample ->
            val actual = vectoriser.transform(sample.text).count { it != 0f }

            assertThat(actual).isEqualTo(sample.expected.size)
        }
    }

    @Test
    fun `the single-character token case that broke the onnx export is handled`() {
        // "Can u get pic msgs" must yield the bigram "can get", not "can u"/"u get".
        val sample = golden.first { it.text.startsWith("Can u get") }
        val actual = vectoriser.transform(sample.text)

        vocabulary.terms["can get"]?.let { assertThat(actual[it]).isGreaterThan(0f) }
        vocabulary.terms["can u"]?.let { assertThat(actual[it]).isEqualTo(0f) }
        assertThat(actual.count { it != 0f }).isEqualTo(sample.expected.size)
    }

    @Test
    fun `vectors are l2 normalised`() {
        golden.filter { it.expected.isNotEmpty() }.forEach { sample ->
            val norm = vectoriser.transform(sample.text)
                .sumOf { it.toDouble() * it.toDouble() }

            assertThat(abs(norm - 1.0)).isLessThan(1e-5)
        }
    }

    @Test
    fun `empty and tokenless input produce an all-zero vector rather than failing`() {
        listOf("", "   ", "!!! ??? ...", "a b c").forEach { text ->
            val vector = vectoriser.transform(text)

            assertThat(vector).hasLength(12_007)
            assertThat(vector.all { it == 0f }).isTrue()
        }
    }

    @Test
    fun `transformation is deterministic`() {
        val text = golden.first().text

        val vectors = (1..10).map { vectoriser.transform(text).toList() }

        assertThat(vectors.distinct()).hasSize(1)
    }

    private companion object {
        /** Float32 storage of double-precision scikit-learn values. */
        const val TOLERANCE = 1e-6
    }
}
