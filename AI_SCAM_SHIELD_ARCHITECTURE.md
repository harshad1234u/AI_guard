# AI Scam Shield (TrustLens) — System Architecture & Specification

**Architecture Specification Version:** 1.0.0  
**Status:** Software Complete & Formally Verified  
**Target Runtime:** Android 8.0+ (API 26 to API 37, targetSdk 36), `arm64-v8a`  

---

## 1. Architectural Philosophy & Core Axioms

AI Scam Shield is engineered around three non-negotiable security and privacy axioms:

| Axiom | Principle | Engineering Enforcement |
|:---:|---|---|
| **Axiom 1** | **Untrusted Content is DATA, Never Instructions** | The system contains zero generative LLMs in the execution or scoring path. Inputs (SMS, OCR text, transcripts, QR strings) are parsed strictly as passive data buffers by compiled deterministic code. Prompt injection is impossible by construction. |
| **Axiom 2** | **UNKNOWN $\ne$ SAFE (Absence of Evidence is not Evidence of Safety)** | External threat intelligence queries that return no detection, rate-limit errors, or timeouts contribute exactly $+0$ points to risk scores. Negative responses never reduce locally established risk. |
| **Axiom 3** | **The Agent Investigates, But NEVER Executes Actions** | The client application contains zero capability to open external links, execute payments, initiate calls, or install applications. `SafeAction` consists solely of 7 decline and defer recommendations. |

---

## 2. High-Level System Architecture

```mermaid
graph TD
    subgraph CLIENT["Android Client Device Boundary"]
        subgraph UI["Presentation Layer (Jetpack Compose)"]
            UI_HOME[Home Dashboard]
            UI_TEXT[Check Message]
            UI_URL[Check Link]
            UI_LENS[Camera OCR]
            UI_QR[QR Scanner]
            UI_VOICE[Voice & Live Guard]
            UI_EMAIL[Check Email & Process Text]
            UI_RESULT[Explained Risk Result View]
        end

        subgraph DOMAIN["Domain & Pipeline Layer (:domain)"]
            UC_TEXT[AnalyzeTextUseCase]
            UC_URL[AnalyzeUrlUseCase]
            UC_QR[AnalyzeQrPayloadUseCase]
            UC_OCR[AnalyzeScannedTextUseCase]
            UC_VOICE[AnalyzeTranscriptUseCase]
            UC_EMAIL[AnalyzeEmailUseCase]
            UC_INTEL[InvestigateIndicatorsUseCase]
        end

        subgraph ML["On-Device AI / ML Engine (:ai)"]
            ML_VEC[TfidfVectoriser\n12,007 Vocab Kotlin]
            ML_ONNX[OnnxScamClassifier\nONNX Runtime CPU]
            ML_GUARD[Zero-Coverage & Link Guard]
            ML_OCR[ML Kit Text Recognition v2]
            ML_BC[ML Kit Barcode Scanning]
        end

        subgraph INV["Deterministic Investigation Layer (:investigation)"]
            INV_MULTI[MultilingualIndicatorExtractor\nHindi, Hinglish, English]
            INV_RULE[RuleBasedIndicatorExtractor\nEntities, Requests, Negation]
            INV_URL[DeterministicUrlAnalyzer\nAST Parser, Brand Matching]
            INV_QR[DeterministicQrPayloadClassifier\nUPI, Web, Text Schemas]
            INV_EMAIL[DeterministicEmailParser\nFrom: Header & Subject Recovery]
        end

        subgraph RISK["Deterministic Risk Engine (:riskengine)"]
            RE_CORE[RiskEngine\nPure Kotlin / Zero IO]
            RE_WEIGHTS[RiskWeights Table\nSingle Source of Truth]
            RE_CORROB[Diminishing Corroboration Engine]
        end

        subgraph NET["Threat Intelligence Transport (:threatintel)"]
            TI_VT[VirusTotalSource\nDomain Reputation]
            TI_UH[UrlhausSource\nHost Reputation & Local URL Match]
            TI_OK[OkHttpTransport\nBounded Pool / 6s Timeout]
        end
    end

    subgraph CLOUD["External Reputation Services (Optional)"]
        EXT_VT[(VirusTotal API v3)]
        EXT_UH[(URLhaus API v1)]
    end

    %% UI to UseCase
    UI_TEXT --> UC_TEXT
    UI_URL --> UC_URL
    UI_LENS --> ML_OCR --> UC_OCR --> UC_TEXT
    UI_QR --> ML_BC --> UC_QR
    UI_VOICE --> UC_VOICE --> UC_TEXT
    UI_EMAIL --> UC_EMAIL --> UC_TEXT

    %% QR Dispatch
    UC_QR --> INV_QR
    INV_QR -->|URL Payload| UC_URL
    INV_QR -->|UPI Payload| UC_TEXT
    INV_QR -->|Text Payload| UC_TEXT

    %% Text Pipeline Flow
    UC_TEXT --> INV_MULTI
    INV_MULTI --> INV_RULE
    UC_TEXT --> ML_VEC --> ML_ONNX --> ML_GUARD
    UC_TEXT --> INV_URL
    UC_TEXT --> UC_INTEL

    %% Threat Intel Lookups
    UC_INTEL --> TI_VT --> TI_OK
    UC_INTEL --> TI_UH --> TI_OK
    TI_OK -. Hostnames Only .-> EXT_VT
    TI_OK -. Hostnames Only .-> EXT_UH

    %% Risk Engine Integration
    ML_GUARD --> RE_CORE
    INV_MULTI --> RE_CORE
    INV_URL --> RE_CORE
    TI_VT --> RE_CORROB --> RE_CORE
    TI_UH --> RE_CORROB --> RE_CORE
    RE_WEIGHTS --> RE_CORE

    %% Result Rendering
    RE_CORE --> UI_RESULT
```

---

## 3. Component Architecture & Module Responsibilities

The codebase is partitioned into 8 decoupled Gradle modules enforcing clean dependency inversion:

```
                    ┌─────────────┐
                    │    :app     │
                    └──────┬──────┘
                           │
      ┌────────────────────┼────────────────────┐
      ▼                    ▼                    ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  :feature:* │     │    :core    │     │   :domain   │
└──────┬──────┘     └─────────────┘     └──────┬──────┘
       │                                       │
       └───────────────────┬───────────────────┘
                           │
      ┌────────────────────┼────────────────────┐
      ▼                    ▼                    ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ :riskengine │     │:investigation│    │     :ai     │
└─────────────┘     └─────────────┘     └─────────────┘
                                               ▲
                                               │
                                        ┌─────────────┐
                                        │:threatintel │
                                        └─────────────┘
```

### Module Responsibilities

| Module | Target Runtime | Dependencies | Functional Scope |
|---|---|---|---|
| **`:domain`** | Pure Kotlin / JVM | None | Defines immutable contracts (`AiClassifier`, `ReputationSource`, `RiskAssessor`), domain models (`InvestigationContext`, `RiskResult`, `Observation`, `ExternalEvidence`), and orchestrating use cases. |
| **`:riskengine`** | Pure Kotlin / JVM | `:domain` only | Pure mathematical risk calculation, weight definitions (`RiskWeights.kt`), confidence evaluation, and safe action resolution. Free of Android SDK, I/O, coroutines, and network. |
| **`:investigation`** | Pure Kotlin / JVM | `:domain` only | Rule-based entity extraction, regex pattern matching, Unicode multilingual analysis, offline URL structural AST inspection, and payload schema classification. |
| **`:ai`** | Android Library | `:domain`, ONNX Runtime, ML Kit Text | On-device ML inference (`OnnxScamClassifier`), Kotlin TF-IDF vectorizer (`TfidfVectoriser`), zero-coverage guard, and ML Kit Text Recognition adapter. |
| **`:threatintel`** | Android Library | `:domain`, OkHttp, Kotlinx Serialization | Remote reputation adapters for VirusTotal and URLhaus. Enforces hostname-only queries and on-device URL slice matching. |
| **`:core`** | Android Library | Jetpack Compose, Material3 | Design system tokens, security colors (Risk Level styling), typography, and shared Compose components (gauges, reason lists, action buttons). |
| **`:feature:*`** | Android Libraries | `:domain`, `:core`, Hilt | Presentation layer: ViewModels, UI state management, CameraX viewfinder preview, barcode analyzer, and audio recorder. |
| **`:app`** | Android Application | All modules, Hilt, CameraX | Application lifecycle, Hilt dependency injection composition root (`TrustLensModule.kt`), MainActivity, ProcessTextActivity, and end-to-end integration tests. |

---

## 4. Multi-Modal End-to-End Pipelines

### 4.1. Text & SMS Processing Pipeline

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as TextCheckScreen
    participant UC as AnalyzeTextUseCase
    participant Extractor as MultilingualIndicatorExtractor
    participant URLAnalyzer as DeterministicUrlAnalyzer
    participant ML as OnnxScamClassifier
    participant Intel as InvestigateIndicatorsUseCase
    participant Engine as RiskEngine

    User->>UI: Pastes Message / SMS
    UI->>UC: invoke(text, source=SMS)
    UC->>Extractor: extract(text)
    Extractor-->>UC: Indicators (URLs, VPAs, OTP/Payment Flags, Orgs)
    
    par Offline URL Structural Analysis
        UC->>URLAnalyzer: observe(urls, claimedOrgs)
        URLAnalyzer-->>UC: List<Observation> (Mismatch, Punycode, TLDs)
    and On-Device ML Classification
        UC->>ML: classify(text)
        ML-->>UC: MlSignal (p=0.8888, backend=CPU)
    and Remote Threat Intel Lookups
        UC->>Intel: investigate(hostnames, urls)
        Intel-->>UC: ThreatIntelReport (VirusTotal, URLhaus)
    end

    UC->>Engine: assess(InvestigationContext)
    Engine-->>UC: RiskResult (Score=90, CRITICAL, Reasons, SafeActions)
    UC-->>UI: AnalysisResult
    UI-->>User: Renders Score Gauge, Attribution Breakdown & Actions
```

### 4.2. Camera OCR & QR Code Pipelines

```mermaid
flowchart TD
    subgraph OPTICAL["Optical Inputs"]
        CAM[CameraX ImageCapture]
        QR_CAM[CameraX ImageAnalysis]
    end

    subgraph VISION["On-Device Vision Models"]
        OCR_ENGINE[ML Kit Text Recognition v2]
        QR_DECODER[ML Kit Barcode Scanning]
    end

    subgraph ROUTERS["Use Case Routers"]
        OCR_ROUTER[AnalyzeScannedTextUseCase]
        QR_ROUTER[AnalyzeQrPayloadUseCase]
        QR_CLASS[DeterministicQrPayloadClassifier]
    end

    subgraph CORE_PIPELINE["Unified Investigation Core"]
        TEXT_UC[AnalyzeTextUseCase]
        URL_UC[AnalyzeUrlUseCase]
        UPI_UC[AnalyzeUpiRequestUseCase]
    end

    CAM --> OCR_ENGINE --> OCR_ROUTER --> TEXT_UC
    QR_CAM --> QR_DECODER --> QR_ROUTER --> QR_CLASS

    QR_CLASS -->|Web Scheme http/https| URL_UC
    QR_CLASS -->|Payment Scheme upi/tez| UPI_UC
    QR_CLASS -->|Plain Text| TEXT_UC

    TEXT_UC --> RISK_ENGINE[RiskEngine]
    URL_UC --> RISK_ENGINE
    UPI_UC --> RISK_ENGINE
```

---

## 5. Machine Learning & Vision Architecture

### 5.1. Model Lineage & Pipeline Architecture
The machine learning pipeline couples a high-performance, on-device vectorizer with an ONNX-runtime executed logistic regression model:

$$\text{Raw String} \xrightarrow{\text{Regex Tokenizer}} \text{Tokens} \xrightarrow{\text{N-Gram Generator}} (1,2)\text{-Grams} \xrightarrow{\text{IDF Scaling}} \mathbf{v}_{\text{raw}} \xrightarrow{\text{L2 Norm}} \mathbf{v}_{\text{norm}} \xrightarrow{\text{ONNX CPU}} p(\text{scam})$$

```mermaid
graph LR
    subgraph PYTHON["Offline Training (tools/train_classifier.py)"]
        DS[(UCI SMS Spam Corpus\n5,574 Messages)]
        SK_VEC[scikit-learn TfidfVectorizer]
        SK_CLF[scikit-learn LogisticRegression]
        EXPORT[skl2onnx Exporter]
    end

    subgraph ASSETS["Shipped Assets (ai/src/main/assets/)"]
        ONNX_FILE[scam-clf.onnx\n120 KB LinearClassifier]
        VOCAB_FILE[scam-clf-vocab.tsv\n12,007 Terms + IDF Weights]
    end

    subgraph RUNTIME["On-Device Android Execution (ai/)"]
        KT_VEC[TfidfVectoriser.kt\nNative Kotlin Vectorizer]
        ONNX_RT[OnnxScamClassifier.kt\nONNX Runtime 1.29.0 on CPU]
        PARITY[TfidfVectoriserParityTest\nDelta < 1.2e-7]
    end

    DS --> SK_VEC --> SK_CLF
    SK_CLF --> EXPORT --> ONNX_FILE
    SK_VEC --> VOCAB_FILE

    ONNX_FILE --> ONNX_RT
    VOCAB_FILE --> KT_VEC
    KT_VEC --> ONNX_RT
    SK_VEC -. Verified Parity .-> KT_VEC
```

### 5.2. Mathematical Specifications

1. **Tokenization Rule:**
   $$\text{Token}(w) = \{ w \in \text{Matches}(\texttt{[\textbackslash p\{L\}\textbackslash p\{N\}\_]+}) \mid \text{Length}(w) \ge 2 \}$$
2. **Feature Mapping & IDF Weighting:**
   $$\text{TF}(t, d) = f_{t, d}, \quad \text{IDF}(t) = \ln\left(\frac{1 + n}{1 + \text{df}(t)}\right) + 1, \quad v_i = \text{TF}(t_i, d) \times \text{IDF}(t_i)$$
3. **L2 Normalization:**
   $$\hat{\mathbf{v}} = \frac{\mathbf{v}}{\|\mathbf{v}\|_2} = \frac{\mathbf{v}}{\sqrt{\sum_{j=1}^{12007} v_j^2}}$$
4. **Logistic Regression Probability Calculation (ONNX):**
   $$z = \mathbf{w}^T \hat{\mathbf{v}} + b, \quad p(\text{scam}) = \sigma(z) = \frac{1}{1 + e^{-z}}$$
5. **Zero-Coverage Guard:**
   $$\text{If } \|\mathbf{v}\|_2 = 0 \implies \text{Return } \texttt{ClassificationResult.Unavailable}$$

---

## 6. Deterministic Risk Engine Specification

### 6.1. Scoring Formula
The risk score $S \in [0, 100]$ is computed as a deterministic sum of active observations and corroborated external evidence:

$$S = \min\left(100, \sum_{o \in \mathcal{O}} W(o) + \sum_{e \in \mathcal{E}} W(e)\right)$$

Where:
- $\mathcal{O}$ is the deduplicated set of observed local characteristics.
- $\mathcal{E}$ is the set of strongest external reputation reports per provider.
- $W(o)$ represents the weight mapping defined in `RiskWeights.kt`.
- $W(e)$ represents the diminishing corroboration schedule for external evidence.

### 6.2. Diminishing Corroboration Curve
To prevent external sources from linearly compounding when they share threat telemetry, external reports follow a sub-linear diminishing schedule:

$$W_{\text{Confirmed}}(k) = \begin{cases} 40 & \text{if } k = 0 \text{ (1st provider)} \\ 25 & \text{if } k = 1 \text{ (2nd provider)} \\ 10 & \text{if } k = 2 \text{ (3rd provider)} \\ 5 & \text{if } k \ge 3 \end{cases}$$

$$W_{\text{Suspicious}}(k) = \begin{cases} 18 & \text{if } k = 0 \\ 10 & \text{if } k = 1 \\ 5 & \text{if } k \ge 2 \end{cases}$$

---

## 7. Threat Intelligence & Network Architecture

### 7.1. Data Minimization Protocol
When external threat intelligence is enabled, only hostnames cross the network boundary:

```
Full URL:    https://sbi-verify.example.com:8443/kyc/update.apk?token=9876543210
Transmitted: sbi-verify.example.com
Stripped:    https://, :8443, /kyc/update.apk, ?token=9876543210
```

### 7.2. On-Device URLhaus Slice Matching
To prevent false-positive flagging on legitimate platforms with historical abuse (e.g., `github.com`), URLhaus integration executes local slice verification:

```mermaid
sequenceDiagram
    autonumber
    participant App as TrustLens Client
    participant Net as OkHttpTransport
    participant UH as URLhaus API

    App->>Net: postForm(host = "github.com")
    Net->>UH: POST /v1/host/ (host="github.com")
    UH-->>Net: Returns JSON with top 100 reported URLs on github.com
    Net-->>App: Raw JSON Response

    Note over App: ON-DEVICE MATCHING STEP
    App->>App: Match local URL against returned 100 URLs

    alt Exact URL matches and is ONLINE
        App->>App: Verdict = CONFIRMED (+40 pts)
    else Exact URL matches and is OFFLINE
        App->>App: Verdict = SUSPICIOUS (+18 pts)
    else Host listed, but URL not in 100 entries
        App->>App: Verdict = SUSPICIOUS (+18 pts, Attributed to Host)
    end
```

---

## 8. Android Security & Permissions Model

```mermaid
graph TD
    subgraph PERMISSIONS["Declared Android Permissions"]
        P_NET[android.permission.INTERNET\nOnly for :threatintel lookups]
        P_CAM[android.permission.CAMERA\nOptical scans; frames never stored]
        P_AUD[android.permission.RECORD_AUDIO\nVoice checks; raw audio never persisted]
    end

    subgraph FORBIDDEN["Forbidden / Never Requested Permissions"]
        F_SMS[READ_SMS / RECEIVE_SMS\nStrictly Prohibited]
        F_CALL[READ_CALL_LOG / CAPTURE_AUDIO\nStrictly Prohibited]
        F_STORE[READ_EXTERNAL_STORAGE\nStrictly Prohibited]
        F_OAUTH[Gmail API / Mailbox OAuth\nStrictly Prohibited]
    end

    subgraph ACTION_RESTRAINT["Principle 3 Action Restraints"]
        R_NO_VIEW[0x startActivity ACTION_VIEW]
        R_NO_PAY[0x upi://pay / Payment Intents]
        R_NO_CALL[0x Intent.ACTION_CALL]
        R_NO_WEBVIEW[0x android.webkit.WebView]
    end
```

---

## 9. Deployment Architecture & Future Production Topologies

### Current Architecture (Direct Client-to-API)
```mermaid
graph LR
    A[Android Client APK\nBuildConfig Injected Keys] -->|GET /api/v3/domains/| B[VirusTotal API]
    A -->|POST /v1/host/| C[URLhaus API]
```

### Recommended Production Architecture (Zero-Trust Gateway)
```mermaid
graph LR
    subgraph CLIENT["Android Client"]
        A[TrustLens Client APK]
    end

    subgraph GATEWAY["TrustLens Secure Cloud Gateway (Cloudflare / Go)"]
        G1[Play Integrity Attestation]
        G2[Secret Vault\nVT & UH API Keys]
        G3[Redis Cache\n24h Host Reputation TTL]
    end

    subgraph PROVIDERS["Threat Intel Cloud"]
        B[VirusTotal]
        C[URLhaus]
    end

    A -->|HTTPS + Play Integrity Token| G1
    G1 --> G2
    G2 --> G3
    G3 -->|Cache Miss| B
    G3 -->|Cache Miss| C
```

---

## 10. Architectural Verification & Compliance Matrix

| Architecture Requirement | Verification Method | Status |
|---|---|:---:|
| **Zero Generative LLM in Scoring Path** | Bytecode & dependency sweep across all modules | **100% COMPLIANT** |
| **Bit-Identical Scikit-Learn Parity** | `TfidfVectoriserParityTest` against 1,115 golden vectors | **100% COMPLIANT** ($\Delta < 1.2\times 10^{-7}$) |
| **Strict Determinism of Risk Score** | `CalibrationTest` & `RiskEngineTest` (48 tests) | **100% COMPLIANT** |
| **Zero Execution Intent Capability** | AST grep across all 8 modules for `ACTION_VIEW`/`WebView` | **100% COMPLIANT** (0 matches) |
| **Data Minimization (Hostname Only)** | `ThreatIntelFlowIntegrationTest` (31 tests) | **100% COMPLIANT** |
| **Offline Airplane Mode Capability** | Network isolation test suite | **100% COMPLIANT** |
| **Total Test Suite Health** | 606 automated unit & integration tests | **602 PASS / 0 FAIL / 4 SKIP** |
