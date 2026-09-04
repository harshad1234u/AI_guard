"""
Trains the TrustLens on-device scam classifier and exports it to ONNX.

This runs offline on a developer machine. Nothing here ships in the APK except the
exported model file.

WHAT THIS MODEL IS
------------------
A TF-IDF + logistic regression classifier trained on the UCI SMS Spam Collection:
5,574 real, human-labelled SMS messages (ham / spam), collected in the UK around 2011.

WHAT THIS MODEL IS NOT
----------------------
It is *not* trained on Indian banking scams. There are no UPI IDs, no KYC fraud and no
OTP-harvesting patterns in the training data. Held-out accuracy reported here is
"accuracy on the UCI SMS Spam Collection" and nothing more — it must never be quoted as
"scam detection accuracy".

Indian scam-specific signals are the deterministic layer's job (RuleBasedIndicatorExtractor
and DeterministicUrlAnalyzer), and the risk engine caps this model's contribution at
RiskWeights.ML_SIGNAL_MAX = 30 of 100 precisely so one model cannot dominate a verdict.

HYPERPARAMETERS ARE FIXED BEFORE TRAINING
-----------------------------------------
They were chosen from standard practice for this dataset and are deliberately not tuned
against any TrustLens demo message. Tuning a model until a chosen example classifies
correctly, then reporting held-out metrics, would make those metrics meaningless.

WHY THE VECTORISER IS NOT IN THE ONNX GRAPH
-------------------------------------------
Exporting the whole sklearn Pipeline was tried first and rejected on measurement, not on
suspicion. skl2onnx lowers TfidfVectorizer onto an ONNX `Tokenizer` op that splits text
and then forms n-grams from *every* token, whereas sklearn extracts tokens matching
`(?u)\\b\\w\\w+\\b` and forms n-grams from the surviving sequence. Single-character words
therefore change every bigram that spans them:

    "Can u get pic msgs"   sklearn -> ... 'can get', 'get pic' ...   ('u' dropped)
                           ONNX    -> ... 'can u',   'u get'   ...

Measured on the held-out set, that diverged on 281 of 1,115 messages (25%), up to 0.082
in probability and one flipped label. So the vectoriser is reimplemented in Kotlin against
this exported vocabulary, and only the logistic regression — pure linear algebra, which
converts exactly — goes into ONNX. `parity-vectors.json` pins the Kotlin implementation
to sklearn's output.
"""

from __future__ import annotations

import json
import sys
import zipfile
from pathlib import Path

import numpy as np
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline

SEED = 42
TEST_SIZE = 0.2

VECTORISER = dict(
    lowercase=True,
    ngram_range=(1, 2),
    min_df=2,
    max_features=20_000,
    sublinear_tf=False,  # skl2onnx does not convert the sublinear variant
    norm="l2",
)
CLASSIFIER = dict(
    C=4.0,
    class_weight="balanced",  # spam is ~13% of the corpus
    max_iter=1_000,
    solver="liblinear",
    random_state=SEED,
)


def load_corpus(archive: Path) -> tuple[list[str], np.ndarray]:
    """Reads the UCI collection: one 'label\\ttext' record per line."""
    with zipfile.ZipFile(archive) as bundle:
        raw = bundle.read("SMSSpamCollection").decode("utf-8", errors="replace")

    texts: list[str] = []
    labels: list[int] = []
    for line in raw.splitlines():
        if "\t" not in line:
            continue
        label, text = line.split("\t", 1)
        label = label.strip().lower()
        if label not in ("ham", "spam"):
            continue
        texts.append(text)
        labels.append(1 if label == "spam" else 0)

    return texts, np.array(labels)


def main() -> int:
    archive = Path(sys.argv[1])
    out_dir = Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)

    texts, labels = load_corpus(archive)
    print(f"corpus: {len(texts)} messages, {int(labels.sum())} spam, "
          f"{int((labels == 0).sum())} ham")

    x_train, x_test, y_train, y_test = train_test_split(
        texts, labels, test_size=TEST_SIZE, stratify=labels, random_state=SEED,
    )
    print(f"split:  {len(x_train)} train / {len(x_test)} held-out test")

    pipeline = Pipeline([
        ("tfidf", TfidfVectorizer(**VECTORISER)),
        ("clf", LogisticRegression(**CLASSIFIER)),
    ])
    pipeline.fit(x_train, y_train)

    # ---- held-out evaluation. Measured once, on data never seen during training. ----
    probabilities = pipeline.predict_proba(x_test)[:, 1]
    predictions = (probabilities >= 0.5).astype(int)

    tn, fp, fn, tp = confusion_matrix(y_test, predictions).ravel()
    metrics = {
        "dataset": "UCI SMS Spam Collection (5,574 real labelled SMS, UK English, ~2011)",
        "task": "SMS spam vs ham — NOT Indian banking-scam detection",
        "test_messages": int(len(y_test)),
        "accuracy": round(float(accuracy_score(y_test, predictions)), 4),
        "precision_spam": round(float(precision_score(y_test, predictions)), 4),
        "recall_spam": round(float(recall_score(y_test, predictions)), 4),
        "f1_spam": round(float(f1_score(y_test, predictions)), 4),
        "roc_auc": round(float(roc_auc_score(y_test, probabilities)), 4),
        "confusion_matrix": {
            "true_negative": int(tn), "false_positive": int(fp),
            "false_negative": int(fn), "true_positive": int(tp),
        },
        "vocabulary_size": int(len(pipeline.named_steps["tfidf"].vocabulary_)),
        "seed": SEED,
        "hyperparameters": {"vectoriser": {**VECTORISER, "ngram_range": list(VECTORISER["ngram_range"])},
                            "classifier": {k: v for k, v in CLASSIFIER.items()}},
    }
    print(json.dumps(metrics, indent=2))

    vectoriser = pipeline.named_steps["tfidf"]
    classifier = pipeline.named_steps["clf"]
    n_features = len(vectoriser.vocabulary_)

    # ---- ONNX export: the logistic regression only ----
    # Linear algebra converts exactly; see the module docstring for why the vectoriser
    # is deliberately left out of the graph.
    onnx_model = convert_sklearn(
        classifier,
        initial_types=[("input", FloatTensorType([None, n_features]))],
        target_opset=18,
        options={id(classifier): {"zipmap": False}},
    )
    model_path = out_dir / "scam-clf.onnx"
    model_path.write_bytes(onnx_model.SerializeToString())
    print(f"exported {model_path} ({model_path.stat().st_size:,} bytes)")

    # ---- vocabulary asset: "term<TAB>idf" in feature-index order ----
    terms = [None] * n_features
    for term, index in vectoriser.vocabulary_.items():
        terms[index] = term
    idf = vectoriser.idf_

    vocab_path = out_dir / "scam-clf-vocab.tsv"
    with vocab_path.open("w", encoding="utf-8", newline="\n") as handle:
        for term, weight in zip(terms, idf):
            handle.write(f"{term}\t{float(weight)!r}\n")
    print(f"exported {vocab_path} ({vocab_path.stat().st_size:,} bytes, "
          f"{n_features} features)")

    # ---- sklearn -> ONNX parity on the entire held-out set ----
    import onnxruntime as ort

    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    dense = vectoriser.transform(x_test).toarray().astype(np.float32)
    ort_probabilities = session.run(None, {"input": dense})[1][:, 1]

    max_delta = float(np.max(np.abs(ort_probabilities - probabilities)))
    disagreements = int(np.sum((ort_probabilities >= 0.5) != (predictions == 1)))
    metrics["onnx_parity"] = {
        "scope": "logistic regression only; the vectoriser is reimplemented in Kotlin",
        "max_probability_delta": max_delta,
        "label_disagreements": disagreements,
        "checked_messages": int(len(x_test)),
    }
    print(f"parity: max delta {max_delta:.3e}, {disagreements} label disagreements "
          f"over {len(x_test)} messages")

    # ---- golden vectors so the Kotlin vectoriser can be pinned to sklearn on the JVM ----
    samples = [
        "URGENT! Your SBI account will be blocked today. Complete KYC and share your OTP.",
        "Can u get pic msgs to your phone?",
        "Hey, are we still meeting for lunch at 1pm tomorrow?",
        "WINNER!! You have won a 1 week FREE membership. Call 09061701461 now!",
        "Pay Rs. 4999 to merchant@example immediately or your parcel will be returned",
        "",
        "!!! ??? ...",
        "Ok lar... Joking wif u oni...",
    ]
    # Line format, chosen so the Kotlin test needs no JSON parser:
    #   text <TAB> scam_probability <TAB> index:value,index:value,...
    sample_matrix = vectoriser.transform(samples)
    sample_probabilities = pipeline.predict_proba(samples)[:, 1]
    parity_path = out_dir / "parity-vectors.tsv"
    with parity_path.open("w", encoding="utf-8", newline="\n") as handle:
        for row, (text, probability) in enumerate(zip(samples, sample_probabilities)):
            start, end = sample_matrix.indptr[row], sample_matrix.indptr[row + 1]
            entries = ",".join(
                f"{int(i)}:{float(v)!r}"
                for i, v in zip(sample_matrix.indices[start:end], sample_matrix.data[start:end])
            )
            assert "\t" not in text and "\n" not in text
            handle.write(f"{text}\t{float(probability)!r}\t{entries}\n")
    print(f"exported {len(samples)} golden vectors for the Kotlin vectoriser")

    (out_dir / "metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
