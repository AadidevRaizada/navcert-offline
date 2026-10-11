# NavCert Offline: AI syllabus mapping, experiments, deck plan and viva prep

Course: Artificial Intelligence (702CO0C032), NMIMS MPSTME Mumbai, AY 2025-26
Student: Aadidev Raizada (N152), solo project
Repository: `github.com/AadidevRaizada/navcert-offline`, branch `main`
Written against the code at commit `e5d55a4` (12 Oct 2026). Every number below comes from `ml/README.md`, the Kotlin source, or `ml/ai_eval/results.json`, which was produced by scripts in this repository.

Status labels used throughout:

| Label | Meaning |
| --- | --- |
| **IN APP** | Runs in the Android app today. File named. |
| **IN PIPELINE** | Runs in the offline training pipeline in `ml/`. |
| **EXPERIMENT** | Implemented and measured in `ml/ai_eval/` on synthetic data. Not in the app. |
| **PROPOSED** | Designed here, not implemented. Say "proposed" out loud in the viva. |

---

## 0. The project in the course's words

NavCert Offline is a **rational agent** that perceives a maritime certificate through a phone camera, and acts by giving capture guidance, producing a rectified page, readable text, five structured fields and answers to questions. It works with no network. Inside it sit four syllabus ideas. First, a **rule-based expert system** extracts the fields. Second, **learned transformers** (a fine-tuned MiniLM and MobileBERT) act as a fallback for fields the rules miss. Third, **search** appears in OCR correction, CTC decoding and answer-span selection. Fourth, a **hand-built decision tree** judges capture quality. The offline `ml/` pipeline shows **supervised learning from weak labels** on a real 108-PDF corpus, with leakage-safe document-level splits.

Course outcomes covered:

| CO | Where it shows |
| --- | --- |
| CO1 Agents and environments | PEAS and environment analysis, section 1.1 |
| CO2 Heuristic and search strategies | OCR correction, CTC decoding, span search, A* experiment, section 1.2 |
| CO3 Knowledge base and expert systems | Field extractor as a production system, sections 1.3 and 1.6 |
| CO4 Supervised and unsupervised learning | MiniLM fine-tuning, decision tree, k-means proposal, section 1.5 |

---

## 1. Syllabus mapping

### 1.1 Unit 1: Introduction, intelligent agents, rationality, environments

**Modelling, inference and learning in one app.**
- *Modelling:* the field schema (five variables), the date grammar regex and the port and authority cues in `CertificateFieldExtractor.kt`.
- *Inference:* rule firing at runtime and transformer forward passes in `CertificateOnnxAnswerer.kt` and `OfflineDocumentAssistant.kt`.
- *Learning:* fine-tuning in `ml/train_certificate_qa.py`.

ML and DL as subsets of AI are both visible. A hand-written rule system (symbolic AI) sits in front of a deep transformer (deep learning). The quality check is a tiny decision procedure that could be learned (machine learning).

**PEAS** (IN APP)

| | NavCert Offline |
| --- | --- |
| **Performance measure** | Fields copied exactly from the certificate. Abstain rather than invent a value. Keep every byte on the phone. Low user effort (few retakes and edits). Answer latency acceptable on a mid-range phone. |
| **Environment** | A paper or printed certificate in the physical world: lighting, glare, skew, stamps, handwriting, mixed templates from many flag states and class societies. The phone's local document library. |
| **Actuators** | On-screen guidance ("Too dark", "Too bright", "Soft image", "Image looks good"). The rectified, enhanced image. Formatted text. Five field values. Answers with source excerpt. Saved documents and folders. |
| **Sensors** | Camera photo (`TakePicture`) or gallery image. User touch input: crop corners, enhancement mode, typed questions, field edits. |

**Environment properties** (task environment = "turn this certificate into trustworthy structured data")

| Property | Value | Why, in project terms |
| --- | --- | --- |
| Observability | **Partially observable** | The agent sees only pixels. Glare, folds, stamps over text and cropped edges hide the true field values. OCR adds a second veil. |
| Determinism | **Stochastic** | The same certificate photographed twice gives different OCR output. Lighting and hand shake are random. The model's output is deterministic for a fixed input, but the input is not. |
| Episodic vs sequential | **Episodic** | Each certificate is an independent episode. Each question to the assistant is answered independently (no dialogue memory). |
| Static vs dynamic | **Static** (during processing) | The certificate does not change while the agent thinks. The capture moment is semi-dynamic (hand moves), which is why quality is checked on the captured frame. |
| Discrete vs continuous | **Continuous** perception, **discrete** output | Pixel intensities and corner coordinates are continuous. Field values are discrete strings. |
| Single vs multi-agent | **Single-agent** | No opponent. Forgery is a real threat but the app does not model an adversary (see Unit 2, minimax). |
| Known vs unknown | **Known** rules of the world | The developer wrote the rules. Template variety is the unknown part, which is why a learned fallback exists. |

**Agent structure.**
- *Simple reflex agent* (IN APP): the capture-quality check, a set of condition-action rules on two percepts (`DocumentScanner.measureQuality`).
- *Model-based reflex* (IN APP): field extraction keeps an internal model of the document (ordered lines, derived facts such as "this page has expiry evidence") before acting.
- *Utility-based* (IN APP): answer selection maximises a score. The certificate model picks the span with the highest start + end logit. The document assistant picks the answer with the highest logit across retrieved chunks.
- *Learning agent* (IN PIPELINE): the learning element (`ml/`) is offline. The deployed agent does not learn from use. Honest depth: no online learning, no user-feedback loop yet.

Depth: **strong**. This is the cleanest fit in the syllabus.

### 1.2 Unit 2: Problem solving by searching

| Syllabus item | Where | Status | Honest depth |
| --- | --- | --- | --- |
| Problem formulation | OCR correction as a search problem. State = partial match of a noisy word against a dictionary trie. Actions = match, substitute, insert, delete, confusion merges (rn→m). Goal = a whole dictionary word. Path cost = weighted edits. | EXPERIMENT (`ml/ai_eval/ocr_search.py`) | Full formulation, measured. |
| Generate-and-test / exhaustive search | `OcrTextCorrector.correctWord` scores all 206 dictionary words with Levenshtein distance (dynamic programming), keeps those within budget (1 edit for words under 7 letters, 2 otherwise), and accepts the best only if it beats the runner-up by at least 1. | IN APP | Simple. A flat scan, not a tree search. |
| Uniform-cost search | Same problem solved with UCS over a 772-node trie. Priority g(n). | EXPERIMENT | Measured: 206.4 / 237.2 nodes per word. |
| A* search | h(n) = 0.4 × \|remaining noisy characters − nearest completion length\|. Admissible: every unit of length mismatch needs at least one length-changing operation, the cheapest costs 0.4. Consistent (checked by hand, see code docstring). | EXPERIMENT | Measured: 68.1 / 74.0 nodes per word, about 3× fewer than UCS for identical answers. |
| Greedy best-first | Three places in the app commit to the locally best choice. (1) CTC **best-path decoding** in `PaddleHandwritingRecognizer.decodeCtc`: argmax per time step, collapse repeats, drop blank. (2) **WordPiece** tokenisation in `CertificateWordPieceTokenizer`: greedy longest-match-first. (3) Word-by-word correction with no sentence context. | IN APP | Real. Beam search for CTC is the textbook alternative (PROPOSED). |
| Pruned (beam-like) search | Span decoding in `CertificateOnnxAnswerer.answer`: top 12 start tokens × top 12 end tokens, span ≤ 32 tokens, ≤ 200 characters. That is 144 candidates instead of up to 384² pairs. | IN APP | Real heuristic pruning. |
| Hill climbing | Fine-tuning is gradient descent on the loss surface, the continuous cousin of hill climbing. Early stopping (patience 5) and best-checkpoint selection guard against the plateau and noise problems. Local maxima map to local minima of the loss. | IN PIPELINE | Conceptual link. Explain, do not overclaim. |
| Genetic algorithm | Tune the capture and OpenCV thresholds (Canny 55/170, dark < 55, bright > 220, blur < 65, quad ≥ 15% area) as a chromosome. Fitness = field accuracy on a labelled photo set. Tournament selection, one-point crossover, Gaussian mutation. | PROPOSED | Needs a labelled photographed set first. |
| Minimax / alpha-beta | Not applicable. The task environment is single-agent. A forger-vs-verifier game is a fair discussion point, but the app extracts data and does not verify authenticity. | N/A | Say so plainly. |

**Measured** (EXPERIMENT, synthetic noise generated from the dictionary, seed 152):

| | App greedy scan | UCS | A* |
| --- | --- | --- | --- |
| Digit confusions (144 words) recovered | 0% | 100% | 100% |
| Letter / split-glyph confusions (179 words) recovered | 72.1% | 100% | 100% |
| Miscorrections | 0% | 0% | 0% |
| Mean work per word | 206 Levenshtein evaluations | 206.4 / 237.2 nodes | 68.1 / 74.0 nodes |
| 34 maritime out-of-vocabulary words changed | 1 (`manning`→`scanning`) | 1 | 1 |

Two real findings about the app came out of this:
- **The app's confusion map is unreachable.** `correctWord` returns early for any word containing a digit (`lower.any { it.isDigit() }`). That means `0→o`, `1→l`, `5→s`, `6→g` and `8→b` are never applied. The guard protects certificate numbers, but it also blocks the feature it was meant to enable.
- **The dictionary has no maritime words.** Correction runs before field extraction (`MainActivity.kt`), so a mixed-case "Minimum Safe Manning Certificate" would become "…Scanning…". All-caps titles are protected by the uppercase guard.

Caveat to state: the search methods share their confusion list with the noise generator, so 100% shows the mechanism works, not real-world accuracy.

Depth: **medium**. The app uses simple search. The course-level search (UCS, A*) is in a measured experiment.

### 1.3 Unit 3: Knowledge representation and inference

**Knowledge base** (IN APP, `CertificateFieldExtractor.kt`). The knowledge is held as regex predicates:
- `certificateCue` (certificate, attestation, licence, insurance, registry, document of compliance)
- `issueEvidence`, `expiryEvidence`, `authorityEvidence`
- `organizationName` (government, register, society, bureau, maritime, shipping, limited …)
- `equipmentDate` (battery, cells, equipment): a negative rule
- a date grammar covering numeric, day-month-name and month-name-day forms

**The same rules in first-order logic** (write these on the slide or board):

```
∀l ∀d  Line(l) ∧ HasLabel(l, ExpiryLabel) ∧ ¬Mentions(l, Equipment) ∧ DateIn(l, d)            ⇒ Expiry(d)
∀l ∀d  Line(l) ∧ HasLabel(l, ExpiryLabel) ∧ ¬Mentions(l, Equipment) ∧ ¬∃x DateIn(l,x)
                ∧ Next(l, m) ∧ DateIn(m, d)                                                    ⇒ Expiry(d)
∀l ∀p ∀d  Line(l) ∧ Matches(l, "Issued at p on d")                                            ⇒ IssuePort(p) ∧ IssueDate(d)
∀t ∀h  Title(t) ∧ Above(h, t) ∧ WithinFirst(h, 8) ∧ OrgCue(h) ∧ ¬CertCue(h)                    ⇒ Authority(h)
∀doc  ¬CertCue(doc)                                                                           ⇒ ∀f ¬Extract(doc, f)
```

**Forward chaining** (IN APP, data-driven). The OCR lines are the initial facts. Rules fire and assert new facts. One true chain exists in the code: the certificate-name rule asserts `Title(t)`. Then `issuerFromHeader` fires only because that fact now exists, and asserts `Authority(h)`. The "Issued at … on …" rule asserts two facts from one firing. A derived fact `hasExpiryEvidence` gates the learned fallback.

**Backward chaining view.** Each field is a goal. The Kotlin elvis chain (`a ?: b ?: c`) tries sub-goals in order until one succeeds: "is there an issued-at line? else a labelled date? else, if issue evidence exists, ask the model". That is goal-directed, depth-first proof search with ordered clauses, like Prolog.

**Resolution** (paper exercise, PROPOSED as a slide build). Prove `Expiry(19/04/2027)` from the clauses above:
1. Convert the rule to CNF: ¬Line(l) ∨ ¬HasLabel(l,E) ∨ Mentions(l,Equip) ∨ ¬DateIn(l,d) ∨ Expiry(d).
2. Add facts: Line(L3), HasLabel(L3,E), ¬Mentions(L3,Equip), DateIn(L3, 19/04/2027).
3. Negate the goal: ¬Expiry(19/04/2027).
4. Resolve step by step with unifier {l/L3, d/19/04/2027} until you reach the empty clause.

The battery-line case (`Mentions(L2, Equip)`) cannot resolve away the `Mentions` literal, so no expiry is derived from it. That matches the unit test `missingBottomDoesNotTurnEquipmentDatesIntoCertificateFields`.

Depth: **medium-strong**. The knowledge base is real and tested by 6 unit tests. The logic formalisation is our reading of the code, not a logic engine in the app.

### 1.4 Unit 4: Constraint satisfaction problems

**Formulation** (EXPERIMENT, `ml/ai_eval/csp_consistency.py`, 9 unit tests):
- **Variables:** certificate_name, issue_date, issue_port, issuing_authority, expiry_date.
- **Domains:** the candidate spans a rule extractor proposes, in priority order, plus `None` (abstain).
- **Unary constraints:**
  - dates parse
  - port ∈ port gazetteer (20 entries in the demo)
  - authority matches an organisation cue
  - name carries a certificate cue
- **Binary constraints:**
  - expiry > issue
  - expiry − issue ≤ 10 years (a loose plausibility bound chosen for the demo)
  - name ≠ authority
- **Algorithm:**
  - node consistency (unary pruning)
  - backtracking search with **MRV** variable ordering and **forward checking**
  - branch-and-bound preference: fill the most fields, then prefer higher-priority candidates

**What exists in the app today:** only fragments, applied locally and not as a CSP. The extractor rejects equipment dates, rejects a model answer with no valid date (`qaDate`) and limits answer length to 2–160 characters. No cross-field check exists. The app would accept an expiry before the issue date. Moving this checker into the app is PROPOSED.

**Measured** (2,000 synthetic candidate sets, seven distractor types, seed 152):

| | Greedy first candidate | CSP solver |
| --- | --- | --- |
| Field accuracy | 80.3% | 97.2% |
| Whole-record exact | 16.4% | 86.2% |
| Records satisfying all constraints | 30.2% | 100% |
| "Wrong but consistent" date cases fixed | 0 / 276 | 0 / 276 |

Mean search nodes per case:
- 10.4 with MRV + forward checking
- 11.0 with static order + forward checking
- 11.6 with static order and no forward checking (0.58 backtracks per case)

The problem is tiny, so MRV saves little. Say that.

Course vocabulary to use: node consistency, forward checking, MRV, least-constraining value (our value order is rule priority instead), intelligent backtracking (conflict-directed backjumping would jump straight from expiry back to issue date; PROPOSED), local search for CSP (min-conflicts would start from the greedy assignment and repair the variable in most violations; PROPOSED).

Depth: **medium**. Correct and tested, but synthetic and outside the app.

### 1.5 Unit 5: Learning

**Inductive learning from weak supervision** (IN PIPELINE). Labels for the field model were not typed by hand. They were produced by the rule extractor (`prepare_certificate_qa.extract`) and by PP-OCR pseudo-labels (confidence ≥ 0.85). This is weak supervision. The learner induces a general span-finding function from examples, but it also inherits the teacher's errors. That is the paper's central lesson.

**Corpus and leakage control** (IN PIPELINE):
- 108 PDFs, 351 pages, 295 unique, 56 exact duplicates (SHA-256 of page images); 130 text-layer and 221 image-only pages.
- Split by **source document**, never by page. `split_for` hashes the PDF name into 20 buckets: 2 test, 3 val, 15 train. Result: 206 / 56 / 33 unique pages.
- In the augmented set:
  - six human-checked gold documents are held out
  - their template group is excluded from training
  - duplicate page text across splits is removed by fingerprint
  - train and val are split by hashed source group

Why: pages of one certificate share the same names, dates and layout. A page-level split would let the model memorise the answer and inflate the score.

**Supervised fine-tuning** (IN PIPELINE, numbers from `ml/README.md`):

| Model | Data | Result |
| --- | --- | --- |
| BERT-tiny (≈4.37M params) | 123 / 34 / 17 aligned train/val/test | 35.3% val EM, 23.5% test EM |
| BERT-tiny, same model | 6-doc human-checked gold set | 4/26 (15.4%) |
| MiniLM (deepset/minilm-uncased-squad2), fine-tuned | 169 weak train / 49 weak val rows | 35/49 (71.4%) weak-val EM, best epoch 6 |
| MiniLM, same | gold set | **15/26 (57.7%)** |
| MiniLM int8 ONNX (32.3 MiB, in app) | weak val / gold | 33/49 (67.3%) / 15/26; three individual gold predictions changed vs the 126.9 MiB full-precision export |

Per-field gold: certificate name 2/6, issue date 3/6, issue port 4/4, issuing authority 2/4, expiry date 4/6.

Training setup:
- AdamW, learning rate 3e-5, batch 8, seed 42
- 384-token windows, stride 96, gradient clipping at 1.0
- checkpoint chosen by **macro** (per-field-averaged) exact match on validation, so one easy field cannot dominate
- early stop after 5 epochs with no improvement
- optional field-balanced sampling

**Overfitting.** Signals and guards:
- tiny data (160 aligned training rows)
- weak val (71.4%) far above gold (57.7%): the model fits the teacher's label style
- guards: early stopping, best-epoch checkpointing, document-level splits, a small pretrained model instead of a large one
- 26 gold fields give wide uncertainty. Each field is worth 3.8 points.

**Why exact match.** A certificate field must be copied, not paraphrased. "19/04/2027" vs "19/04/202" is a wrong expiry. Token-F1 would give that partial credit. The app needs the strict metric, and the evaluator normalises only whitespace, case and edge punctuation.

**Decision tree** (IN APP as a hand-built tree, EXPERIMENT as a learned tree). `DocumentScanner.measureQuality` is a depth-3 tree: `mean < 55 → dark; mean > 220 → bright; Laplacian variance < 65 → soft; else good`. The experiment rendered 240 synthetic pages with known degradations, computed the same features at the 1,600 px analysis size, and learned a CART tree (Gini, depth ≤ 3) on 144 pages:

| Test split, 96 pages | Hand tree | Learned tree |
| --- | --- | --- |
| Accuracy | 78.1% | 92.7% |
| Blurry caught | 5/22 | 20/22 |
| Good accepted | 22/25 | 20/25 |

Learned splits: `mean < 97.0 → dark; mean ≥ 218.7 → bright; Laplacian var < 267.7 → blurry; else good`. Lesson: sensor noise inflates Laplacian variance, so a fixed threshold of 65 misses noisy blur. The learned thresholds belong to the generator and are not a drop-in replacement.

**Unsupervised: k-means** (PROPOSED). Cluster certificate *layouts* to discover templates without labels:
1. **Features per page:** from ML Kit geometry, normalised y-positions of the first 20 lines; positions of label keywords (issue / expiry / place / authority), each a 0–1 coordinate or −1 if absent; line count; text density per page third; ratio of uppercase lines.
2. **Steps:** standardise, run k-means for k = 2…12, pick k by elbow and silhouette, then inspect each cluster's medoid.
3. **Uses:**
   - template-aware rules (per-cluster label positions)
   - stratified, template-disjoint splits for honest evaluation
   - spotting outliers (non-certificates) before extraction
4. **Data needed:** only geometry, which the app already computes in `DocumentTextFormatter`.

**Transformers for extractive QA** (IN APP). MiniLM and MobileBERT are BERT-family encoders with self-attention. The model reads `[CLS] question [SEP] context [SEP]` and outputs two logit vectors, one for the start token and one for the end token. The answer is the span maximising start + end. It cannot invent text that is not in the context. That is the safety argument for extractive over generative QA.

**Retrieval for document Q&A** (IN APP, `DocumentKnowledgeIndex`):
- **Chunking:** 1,200 characters with 180 overlap. A chunk boundary snaps back to the last sentence end if that end lies past the half-way point.
- **Scoring** is TF-IDF-like: occurrences × (ln((N+1)/(df+1)) + 1), with title hits weighted ×3 and stop-words removed.
- **Answering:** take the top 4 chunks, run MobileBERT on each, keep the answer with the highest logit.

The overlap exists so that an answer cut by a chunk boundary still appears whole in the neighbouring chunk.

Depth: **strong**. Real training, real held-out gold set, honest negative results.

### 1.6 Unit 6: Expert systems

The field extractor is a small **rule-based expert system** with a learned fallback.

| Expert-system part | NavCert Offline | Status |
| --- | --- | --- |
| Knowledge base | Label patterns, date grammar, organisation cues, negative rules (equipment dates), certificate gate | IN APP |
| Working memory | OCR lines (trimmed, non-blank), derived facts: `name`, `issuedAt` match, `hasExpiryEvidence` | IN APP |
| Inference engine | `CertificateFieldExtractor.extract`: gate → per-field ordered rule chains → evidence-gated fallback | IN APP |
| Conflict resolution | **Rule order** (priority). The first rule that proves a field wins. Recency: `issuedAt` takes the **last** "Issued at … on …" line (test `prefersCertificatesOwnIssuanceLine…`). Specificity: an exact "by Indian Register of Shipping" header outranks a generic "issued by" label. | IN APP |
| Fallback to learned model | Only when the rules fail **and** evidence exists. MiniLM int8 ONNX first, MobileBERT TFLite if ONNX fails. The port field has no model fallback at all. Model output is validated: 2–160 chars, dates must parse. | IN APP |
| User interface | Result screen shows the five fields as editable text. The user is the final authority. | IN APP |
| Explanation facility | "Why this value?": show the rule that fired and the source line. | PROPOSED |
| Knowledge acquisition | The developer read the 108-PDF corpus and wrote rules. Unit tests freeze each lesson as a regression case. | Manual |

**Architecture diagram spec** (for `docs/figures/ai_expert_system.png`, 1920×1080, white, deck palette):
- **Left column, top to bottom:** "Camera + OCR" (ink outline) → arrow "lines" → **Working memory** (mist fill, yellow left bar). Inside it, three facts in mono: `Line[1..n]`, `Title(t)`, `ExpiryEvidence`.
- **Centre:** **Inference engine** (blue fill, white text). Three stacked lanes labelled `1 Gate`, `2 Rule chain per field`, `3 Fallback`. A curved arrow from engine back to working memory is labelled "asserts facts".
- **Right of the engine:** **Knowledge base** (paper fill, ink outline), listing `CERT_CUE`, `ISSUE_LABEL`, `EXPIRY_LABEL`, `ORG_CUE`, `¬EQUIPMENT`, `DATE_GRAMMAR`. A double arrow to the engine is labelled "match".
- **Below the engine:** dashed box **Learned fallback**: `MiniLM int8 ONNX → MobileBERT`, with arrow label "only if rule fails ∧ evidence".
- **Far right:** **User interface**: "5 editable fields".
- **Dashed red-outline box** above the UI: **Explanation facility (proposed)**.
- **Bottom strip:** **Knowledge acquisition**: "108 PDFs read by developer → rules → 6 unit tests".

**Development process followed:**
1. Identify the problem.
2. Acquire knowledge (corpus inspection).
3. Represent it (regex).
4. Build the inference engine.
5. Test (unit tests from real failure patterns, such as the battery date and the truncated model date).
6. Refine.

**Characteristics shown:**
- expertise in a narrow domain
- symbolic reasoning
- the ability to abstain
- transparency: every rule is readable

**Limitations shown:**
- brittle on unseen templates
- no certainty factors
- English only
- no explanation screen yet
- the knowledge engineer is also the developer

Depth: **strong**. This is the best single slide for CO3.

### 1.7 Gaps a strict examiner may raise

| Gap | Answer |
| --- | --- |
| No minimax/alpha-beta | Single-agent task. Not forced into the project. |
| No GA or hill climbing in the app | GA is proposed for threshold tuning. Gradient-based fine-tuning is the continuous analogue of hill climbing. |
| CSP not in app | Implemented and tested as an experiment. Moving it into the app is the next step. |
| k-means not implemented | Designed, with features available from existing geometry. |
| No end-to-end accuracy | Correct. The 57.7% is text-only QA on 26 gold fields. The full photographed pipeline has not been scored. |

---

## 2. Experiments (done)

Location: `ml/ai_eval/` (README, three scripts, two test files, `results.json`). Commit `e5d55a4`.
Run: `ml\.venv\Scripts\python.exe ml\ai_eval\run_all.py` (about 20 s) and `ml\.venv\Scripts\python.exe -m unittest discover -s ml\ai_eval` (14 tests, all pass).
All data is synthetic and generated by the scripts. No certificate text or image is read.

| Experiment | Headline number | What it really shows |
| --- | --- | --- |
| (a) CSP consistency | Whole-record exact 16.4% → 86.2% on 2,000 synthetic candidate sets | Hard constraints repair constraint-violating distractors. They cannot fix a wrong value that satisfies all constraints (0/276). |
| (b) OCR correction search | A* expands ~3× fewer nodes than UCS (68.1 vs 206.4; 74.0 vs 237.2) with the same answers. App recovers 0% of digit confusions. | A heuristic cuts search effort. The app's confusion map is unreachable. The dictionary corrupts `manning`. |
| (c) Quality decision tree | Hand tree 78.1%, learned CART 92.7% on 96 synthetic test pages. Blurry caught: 5/22 vs 20/22. | Learned thresholds adapt to the feature distribution. Laplacian variance is noise-sensitive. Real calibration is needed. |

---

## 3. AI deck plan (about 10 minutes, 15 slides)

Look: shared deck look from the brief:
- 1920×1080, paper #F7F4EE, mist #E9E3D6, ink #0D1B2A, muted #5B6472
- accents: blue #1F4BD8, yellow #FFC72C, red #D62828 (sparingly)
- Bricolage Grotesque 800 headlines, Instrument Sans body, DM Mono kickers
- Footer `AADIDEV RAIZADA · N152 · ARTIFICIAL INTELLIGENCE · NMIMS MPSTME` + slide number

Screenshots from `docs/screenshots/` (synthetic certificate only). Every x-embed is a self-contained mini page ≤ 16 KB: inline CSS/JS, system font stack, no network, loops or replays on click, uses only synthetic strings shown below.

Title forms used for ★ headlines, in order: name · number-led · paradox · equation · metaphor · quote · strikethrough · question · fraction · versus · imperative · confession · maxim · what-if · rule of three. No form repeats on adjacent slides.

Timing: 20 + 35 + 40 + 50 + 40 + 50 + 50 + 50 + 50 + 40 + 45 + 45 + 35 + 30 + 20 = **600 s**.

---

### S01 · Title (20 s)

- **Kicker:** `ARTIFICIAL INTELLIGENCE · PROJECT`
- **★ Headline (name):** NavCert Offline
- Alt 1 (subtitle-promise): An AI that reads ship certificates with zero bars of signal
- Alt 2 (question): Can a phone be a certificate expert without the cloud?
- **On-slide text:**
  - NavCert Offline
  - A rational agent for maritime certificates, fully on-device
  - Aadidev Raizada · N152 · NMIMS MPSTME Mumbai · Artificial Intelligence · 2025-26
- **Visual:** right half: `01_home.png` in a phone frame, tilted 4°. Left: title block. A thin yellow rule under the title.
- **Build:** title → subtitle → byline → phone slides in from the right.
- **Speaker notes (20 s):** "Good morning. I'm Aadidev, N152. My project is NavCert Offline, an Android app that reads maritime certificates and turns them into structured data, with no internet at all. Today I'll show it through the lens of this course: agents, search, knowledge, constraints, learning and expert systems."

### S02 · Hook (35 s)

- **Kicker:** `THE HOOK`
- **★ Headline (number-led):** 108 PDFs. 351 pages. Zero bars of signal.
- Alt 1 (scene): A seafarer, a stack of certificates, and no network
- Alt 2 (question): Who reads the paperwork when the ship is out of range?
- **On-slide text:**
  - 5 fields checked first: certificate name · issue date · port of issue · issuing authority · expiry date
  - Real corpus: 108 PDFs · 351 pages · 295 unique · 221 image-only
- **Visual:** five yellow field chips flying out of a grey synthetic certificate silhouette into a clean card. x-embed `hook_fields.html` (≤ 6 KB): a CSS animation where five labelled chips detach from a blurred page and stack into a card, 3 s loop.
- **Build:** headline → certificate silhouette → chips fly out one per beat → corpus line.
- **Speaker notes (35 s):** "Ships and seafarers carry many certificates, and the first things anyone checks are five facts: what is it, when was it issued, where, by whom, and when does it expire. I was given a real corpus: 108 PDFs, 351 pages, and 221 of those pages are images with no text layer at all. And ships are often out of signal range. So the question for this project was: can a phone do this job alone, offline, and be honest about what it does not know?"

### S03 · Why it matters (40 s)

- **Kicker:** `WHY IT MATTERS`
- **★ Headline (paradox):** The most private papers are the ones we upload first
- Alt 1 (contrast): Cloud OCR is convenient. Identity documents are not.
- Alt 2 (number-led): 0 network permissions, by design
- **On-slide text:**
  - Certificates carry names, ranks, ID numbers, vessel details
  - Cloud document AI = network dependence + data leaves the device
  - NavCert: INTERNET and ACCESS_NETWORK_STATE removed in the manifest
- **Visual:** split screen. Left (muted): phone → cloud arrow with a red dashed line. Right (blue): phone with a closed loop arrow labelled "on-device".
- **Build:** left side → right side → manifest line appears last in DM Mono.
- **Speaker notes (40 s):** "Here's the paradox. The documents we most want to protect, identity and competency certificates, are exactly the ones we usually push to a cloud OCR service. That creates two problems: it fails when there is no signal, and it moves personal data off the device. So the design constraint was strict: the manifest removes the internet permission entirely. Every AI component you'll see today, OCR, rules, transformers, runs on the phone. That constraint shapes every algorithm choice that follows."

### S04 · The agent (50 s)

- **Kicker:** `UNIT 1 · INTELLIGENT AGENT`
- **★ Headline (equation):** Agent = camera + rules + model + the right to say "I don't know"
- Alt 1 (question): What would a rational certificate clerk do?
- Alt 2 (metaphor): A clerk in your pocket, with a rulebook and a hunch
- **On-slide text:** PEAS grid:
  - **P** exact fields · abstain over invent · stays on-device · few retakes
  - **E** paper certificate, light, glare, stamps, many templates
  - **A** guidance · rectified page · text · 5 fields · answers
  - **S** camera · gallery · touch (crop, questions, edits)
  - Strip below: Partially observable · Stochastic · Episodic · Static · Continuous → discrete · Single-agent
- **Visual:** 2×2 PEAS grid on mist cards. Below, six environment-property pills; four in blue, "Partially observable" and "Stochastic" in yellow (the hard ones). x-embed `agent_loop.html` (≤ 8 KB): percept → agent box → action loop; a dot travels the loop; the agent box flips through "reflex: quality check" → "model-based: field rules" → "utility: best answer span".
- **Build:** P → E → A → S → property pills one by one → loop animation.
- **Speaker notes (50 s):** "In course terms, NavCert is a rational agent.
  - **Performance:** copy fields exactly, and abstain rather than invent. A blank is better than a wrong expiry date.
  - **Environment:** a real paper certificate under real light.
  - **Actuators:** guidance, a flattened page, text, fields, answers.
  - **Sensors:** the camera and my touches.

  The environment is partially observable, because glare and stamps hide text. It's stochastic, because two photos of the same page give different OCR. It's episodic: each certificate stands alone. It's static while processing, continuous in pixels but discrete in its output, and single-agent. The agent itself is layered: a simple reflex rule for capture quality, a model-based rule system for fields, and a utility-maximiser that picks the highest-scoring answer span."

### S05 · Perception pipeline (40 s)

- **Kicker:** `PERCEPTION`
- **★ Headline (metaphor):** Every agent is only as smart as its eyes
- Alt 1 (number-led): 1,600 px to think, 3,200 px to read
- Alt 2 (imperative): Fix the photo before you trust the model
- **On-slide text:**
  - Detect page (Canny 55/170 → largest quad ≥ 15%) → warp flat
  - Quality reflex: dark < 55 · bright > 220 · blur < 65
  - Enhance: CLAHE 2.2, 8×8 → OCR: ML Kit (print) · PP-OCRv5 ONNX (handwriting, falls back below 0.20 confidence)
  - Layout: blocks top→bottom, left→right, indentation from median character width
- **Visual:** left to right strip of four real screenshots: `02_preview.png` → `03_crop.png` → `04_result_text.png`, with arrows labelled "rectify", "enhance + OCR". If the Pipeline Lab screenshots exist, use `11_lab_edges.png` and `13_lab_warp.png` as inset thumbnails.
- **Build:** screenshots appear in sequence with arrow labels → parameter line last.
- **Speaker notes (40 s):** "Before any reasoning, the agent has to perceive. OpenCV finds the page with Canny edges and the largest four-sided contour covering at least 15% of the frame, then warps it flat. A reflex rule checks brightness and blur and tells the user to retake. CLAHE boosts contrast. Then two recognisers: ML Kit for print, and a PP-OCRv5 model in ONNX Runtime for handwriting, which falls back to ML Kit when its confidence is below 0.20. Finally, layout is rebuilt from box geometry. The output of all this is the agent's percept: ordered lines of noisy text."

### S06 · Knowledge and rules (50 s)

- **Kicker:** `UNIT 3 + 6 · EXPERT SYSTEM`
- **★ Headline (quote):** "If it says *Valid until*, believe the date after it — unless it's the battery."
- Alt 1 (number-led): 1 gate, 5 rule chains, 1 fallback
- Alt 2 (metaphor): A rulebook first, a neural hunch second
- **On-slide text:**
  - Knowledge base: label patterns · date grammar · organisation cues · ¬equipment
  - Inference: gate → rule chain per field → model only if rule fails ∧ evidence exists
  - Conflict resolution: rule priority · last "Issued at" line wins · specific beats generic
  - `Line(l) ∧ ExpiryLabel(l) ∧ ¬Equipment(l) ∧ DateIn(l,d) ⇒ Expiry(d)`
- **Visual:** expert-system architecture diagram (spec in section 1.6), plus x-embed `rule_chain.html` (≤ 12 KB). A synthetic certificate of 8 mono lines on the left (`CARGO SHIP CONSTRUCTION CERTIFICATE`, `Issued by: Government of India`, `Expiry Date of Battery: 30/10/2026`, `Valid until 19/04/2027`, `Issued at Mumbai on 22/01/2026` …). Rules light up in order: CERT_CUE gate (green tick) → name rule highlights line 1 → "Issued at" rule highlights the last issued line and emits two chips (date, port) → expiry rule skips the battery line (red strike) and fires on "Valid until" → authority rule fires. Each fired rule drops a chip into a 5-slot card. One cycle 8 s.
- **Build:** knowledge base box → inference engine box → animation plays → FOL line fades in at the bottom.
- **Speaker notes (50 s):** "The field extractor is a classic rule-based expert system.
  - **Knowledge base:** patterns I acquired by reading the corpus: label phrases, a date grammar, organisation words, and one important negative rule. A battery or equipment date is never the certificate's expiry.
  - **Inference engine:** a gate first. If the page has no certificate cue, nothing fires, so meeting notes never get fake fields. Then each field has an ordered rule chain; the first rule that proves it wins. That's conflict resolution by priority, plus recency: the last 'Issued at' line beats an attachment's.
  - **Chaining:** the name rule asserts a title, and the authority rule then fires on the header above that title. That's forward chaining.
  - **Fallback:** only when rules fail and there's evidence, it asks a learned model, and validates the answer. The port field never asks the model at all."

### S07 · Search in correction (50 s)

- **Kicker:** `UNIT 2 · SEARCH`
- **★ Headline (strikethrough):** ~~Spell-check~~ Shortest path
- Alt 1 (number-led): 206 comparisons vs 68 nodes
- Alt 2 (question): Is "narne" one edit from "name", or 0.4?
- **On-slide text:**
  - App today: scan 206 words, Levenshtein ≤ 1–2, accept only a clear winner
  - As search: state = (chars read, trie node) · OCR confusions cost 0.4 · other edits 1
  - h(n) = 0.4 × |remaining − nearest word length| → admissible
  - Synthetic test: A* 68.1 nodes vs UCS 206.4, same answers · App recovers 0% of digit confusions (guard blocks them)
- **Visual:** x-embed `search_tree.html` (≤ 14 KB): input `narne`. A trie fragment grows level by level (n → na → nam → name; branches nar, nat …). Two side-by-side panels "UCS" and "A*", each with a live "nodes expanded" counter. UCS fans out wide; A* goes nearly straight down. The `rn→m` edge glows yellow with "0.4". Final frame: both reach `name`, counters freeze. Replay on click.
- **Build:** strikethrough animates on "Spell-check" → app line → search formulation → animation → findings line in red-accent mono.
- **Speaker notes (50 s):** "OCR post-correction looks like spell-check, but in AI terms it's a search problem. The app today does generate-and-test: it compares the word with all 206 dictionary words and accepts the best only if it clearly wins. I reformulated it properly. The state is how much of the noisy word I've read and where I am in a dictionary trie. A known OCR confusion like 'rn' read for 'm' costs 0.4; any other edit costs 1. Uniform-cost search finds the cheapest word. A* adds an admissible heuristic from the length mismatch, and on my synthetic test it expands about three times fewer nodes for the same answers. The experiment also caught a real bug: the app skips any word with a digit, so its own zero-to-'o' confusion map never runs."

### S08 · CSP consistency (50 s)

- **Kicker:** `UNIT 4 · CSP`
- **★ Headline (question):** Can a certificate expire before it was issued?
- Alt 1 (equation): 5 variables + 3 constraints = 0 impossible certificates
- Alt 2 (imperative): Let the fields check each other
- **On-slide text:**
  - Variables: name · issue · port · authority · expiry
  - Domains: rule candidates + ∅ (abstain)
  - Constraints: expiry > issue · port ∈ gazetteer · authority looks like an organisation
  - Backtracking + MRV + forward checking
  - Synthetic test (2,000 cases): record exact 16.4% → 86.2% · consistent-but-wrong fixed: 0/276
  - Status: experiment, not yet in the app
- **Visual:** x-embed `csp_pruning.html` (≤ 12 KB). Five columns (variables), each a stack of candidate chips from a synthetic case: issue {`19/04/2027`, `22/01/2026`, ∅}, expiry {`22/01/2026`, `19/04/2027`, ∅}, port {`A. Kumar`, `Mumbai`, ∅}. Step 1: unary pruning: `A. Kumar` greys out ("not a port"). Step 2: MRV picks the smallest domain (highlighted). Step 3: assign issue = `19/04/2027` → forward check strikes every expiry ≤ it → only ∅ left → backtrack (red flash). Step 4: issue = `22/01/2026` → expiry `19/04/2027` survives → green. Final: a 5-field card. 10 s loop.
- **Build:** question → variables/domains → constraints → animation → numbers → status tag in mono.
- **Speaker notes (50 s):** "Today the app checks each field alone. Nothing stops it reporting an expiry date before the issue date, for example when the two labels are read in the wrong order. That's a constraint satisfaction problem:
  - **Variables:** the five fields.
  - **Domains:** the candidates the rules found, plus 'abstain'.
  - **Constraints:** expiry after issue, port in a gazetteer, authority that looks like an organisation.

  I wrote a backtracking solver with MRV ordering and forward checking, with nine unit tests. On 2,000 synthetic cases, whole-record accuracy rose from 16 to 86 percent. But look at the last number: when a wrong date still satisfies every constraint, the CSP fixes none of them. Constraints catch contradictions, not plausible mistakes. This is an experiment; adding it to the app is the next step."

### S09 · Learning: fine-tuning (50 s)

- **Kicker:** `UNIT 5 · SUPERVISED LEARNING`
- **★ Headline (fraction):** 15 / 26
- Alt 1 (versus): BERT-tiny vs MiniLM: 4 right vs 15 right
- Alt 2 (confession): The model learned my rules' mistakes too
- **On-slide text:**
  - Weak labels: rules + OCR pseudo-labels (≥ 0.85) → no hand labelling
  - Split by document, not page: 206 / 56 / 33 unique pages; gold docs and their template held out
  - BERT-tiny: 35.3% val · 23.5% test · 4/26 gold
  - MiniLM fine-tuned: 71.4% weak val · **15/26 = 57.7% gold** · int8 ONNX 32.3 MiB, still 15/26
  - Per field (gold): name 2/6 · issue date 3/6 · port 4/4 · authority 2/4 · expiry 4/6
- **Visual:** large "15/26" in Bricolage 800. Right: horizontal bars for gold EM, BERT-tiny 4/26 (muted) vs MiniLM 15/26 (blue), with a bracket "6 documents · wide uncertainty". Below: five small per-field bars. Built as a native chart in the deck, data exactly as above.
- **Build:** big fraction → tiny vs MiniLM bars → per-field bars → split line.
- **Speaker notes (50 s):** "Learning. I had no hand labels, so I used weak supervision: my own rules and OCR pseudo-labels became the teacher. To avoid leakage I split by document, never by page, because pages of one certificate share names and dates. The first model, BERT-tiny, reached 23.5 percent test exact match. Too weak. A fine-tuned MiniLM reached 71 percent on weak validation but 15 of 26, 57.7 percent, on six human-checked gold documents. That gap is the lesson: on weak labels the model also learns the teacher's mistakes, so the human-checked number is the one to trust. Quantised to int8 it shrinks to 32 MiB and keeps 15 of 26. I use exact match because a date with one wrong digit is a wrong date."

### S10 · Learning: trees and clusters (40 s)

- **Kicker:** `UNIT 5 · TREES + CLUSTERS`
- **★ Headline (versus):** Thresholds I wrote vs thresholds it learned
- Alt 1 (number-led): 5 of 22 blurry photos caught, then 20
- Alt 2 (question): Should a camera rule be hand-written or learned?
- **On-slide text:**
  - App: hand-built tree → dark < 55 · bright > 220 · blur < 65
  - Learned CART (Gini, depth 3), 240 synthetic pages: 78.1% → 92.7% · blurry caught 5/22 → 20/22
  - Why: sensor noise inflates Laplacian variance
  - Next (proposed): k-means on layout geometry → discover certificate templates
- **Visual:** two small trees drawn side by side (ink lines, blue leaves), the blur node highlighted yellow with "65" vs "267.7". Bottom-right inset: a scatter of 2-D layout points in 4 coloured clusters labelled "proposed: k-means templates" (schematic, no data values).
- **Build:** hand tree → learned tree → numbers → k-means inset.
- **Speaker notes (40 s):** "The capture-quality check is a decision tree I wrote by hand. I tested it against a tree learned with Gini impurity on 240 synthetic pages with known damage. The learned tree scored 93 percent against 78, mainly because it caught 20 of 22 blurry pages where my rule caught 5. The reason is that camera noise inflates the sharpness measure. The learned thresholds belong to synthetic data, so the real fix is to calibrate on real phone photos. For unsupervised learning, the proposal is k-means on layout geometry, to discover certificate templates without labels."

### S11 · Transformer QA (45 s)

- **Kicker:** `TRANSFORMERS · EXTRACTIVE QA`
- **★ Headline (imperative):** Point to the answer. Never write it.
- Alt 1 (metaphor): A highlighter, not a pen
- Alt 2 (number-led): 384 tokens, 2 logits, 1 span
- **On-slide text:**
  - Input: [CLS] question [SEP] certificate text [SEP] · 384-token windows, stride 96
  - Output: P(start), P(end) per token → best span (top 12 × top 12, ≤ 32 tokens)
  - Fields: MiniLM int8 ONNX → MobileBERT if unavailable
  - Ask-a-document: 1,200-char chunks, 180 overlap → TF-IDF-style top 4 → MobileBERT
- **Visual:** x-embed `span_probs.html` (≤ 10 KB): tokens of a synthetic context `Date of expiry : 19 / 04 / 2027 . Issued at Mumbai` laid out in a row. Two bar rows animate above them: start probability peaks on `19`, end probability peaks on `2027`. A highlighter sweep fills the span. Question shown above: "What is the expiry date?". Right side: `06_ask_document.png` screenshot.
- **Build:** question → token row → start bars → end bars → highlight → screenshot.
- **Speaker notes (45 s):** "The learned part is a transformer doing extractive question answering. It reads the question and the certificate text together, and for every token predicts two probabilities: is this where the answer starts, and is this where it ends. The best span wins. That's a search too: the top 12 starts times the top 12 ends, capped at 32 tokens. The key property is that it can only highlight text that exists. It cannot invent a date. For asking questions across saved documents, text is cut into 1,200-character chunks with 180 characters of overlap, so an answer on a boundary is never cut in half. The best four chunks go to MobileBERT."

### S12 · Evaluation and limits (45 s)

- **Kicker:** `EVALUATION`
- **★ Headline (confession):** What I have not measured yet
- Alt 1 (strikethrough): ~~57.7% accurate~~ 57.7% on 26 text fields
- Alt 2 (paradox): My most honest number is my smallest dataset
- **On-slide text:**
  - Measured:
    - QA on text: 15/26 gold
    - unit tests: extractor 6, CSP 9, search 5
    - synthetic search/CSP/tree experiments
  - Not measured:
    - photographed end-to-end accuracy
    - OCR CER/WER
    - latency on phone
    - abstention when a field is absent
  - Limits: weak labels · 6 gold documents · English only · correction dictionary has no maritime words
  - Next: fresh, vessel-disjoint photo set · per-field precision/recall · p95 latency
- **Visual:** two columns, "Measured" (blue ticks) and "Not yet" (yellow open circles). No chart.
- **Build:** measured column → not-yet column → limits → next line.
- **Speaker notes (45 s):** "Honest evaluation.
  - **Measured:** 15 of 26 gold fields for the text-only model, plus unit tests and synthetic experiments.
  - **Not measured:** the full pipeline from a photograph to five fields. The 57.7 percent excludes camera, cropping and OCR errors, so it's not an app accuracy. I also haven't measured character error rate, phone latency, or how well it abstains when a field is genuinely missing.
  - **Limits:** weak labels, a six-document gold set, English only, and a correction dictionary with no maritime words.
  - **Next:** a fresh set of photographed certificates from vessels the model has never seen, with per-field precision and recall."

### S13 · Ethics and privacy (35 s)

- **Kicker:** `RESPONSIBLE AI`
- **★ Headline (maxim):** The safest server is no server
- Alt 1 (number-led): 0 bytes leave the phone
- Alt 2 (question): Who else sees your seafarer's papers?
- **On-slide text:**
  - Network permissions removed · models bundled · documents in app-private storage
  - Private corpus never in the repo, decks or paper; demos use a synthetic certificate
  - Abstain over hallucinate: gate, evidence rules, extractive QA, answer validation
  - Human in the loop: every field editable · extraction ≠ authenticity verification
- **Visual:** four icon rows (lock, database-crossed, hand-stop, pencil) in ink line style.
- **Build:** one row per beat.
- **Speaker notes (35 s):** "On-device AI changes the ethics. Nothing can leave the phone because the app has no network permission at all. The client's real certificates never appear in my repository, slides or paper; every demo uses a synthetic certificate. The system is built to abstain rather than hallucinate. The user can edit every field. And I'm careful about one claim: this app extracts data. It does not verify that a certificate is genuine, and it should never be sold as if it did."

### S14 · Future (30 s)

- **Kicker:** `FUTURE WORK`
- **★ Headline (what-if):** What if the model read the page, not the text?
- Alt 1 (metaphor): From reading glasses to eyes
- Alt 2 (imperative): Teach the layout, not just the words
- **On-slide text:**
  - Layout-aware: LayoutLMv3 (text + image + 2-D position, 133M params)
  - OCR-free: Donut (image → structured output)
  - Faster OCR challenger: PP-OCRv6 small (7.7M params)
  - In-app next: CSP checker · maritime dictionary + fix digit guard · k-means templates · explanation screen
- **Visual:** three cards left to right with increasing size: "text → fields (today)", "text + layout → fields", "pixels → fields". Small mono source line under each research card.
- **Build:** cards left to right → in-app next line.
- **Speaker notes (30 s):** "Where next. Today's model reads only text, so it can't tell an issue date from an expiry date by position. Layout-aware models like LayoutLMv3 add page coordinates; Donut skips OCR entirely. Both are much larger than my 32 MiB model and need word-box or image labels I don't have yet. The cheaper next steps are in this deck: move the CSP checker into the app, fix the digit guard, add maritime words, and cluster templates."

### S15 · Close (20 s)

- **Kicker:** `CLOSE`
- **★ Headline (rule of three):** Zero bars. Five fields. One honest answer.
- Alt 1 (callback-question): So, can a phone be a certificate expert?
- Alt 2 (equation): Rules + search + learning = an agent you can audit
- **On-slide text:**
  - Agent · expert system · search · CSP · learning: one app
  - github.com/AadidevRaizada/navcert-offline
  - Thank you · Questions
- **Visual:** `05_certificate_fields.png` centred in a phone frame, five filled field chips around it.
- **Build:** headline → phone → repo link.
- **Speaker notes (20 s):** "To close: zero bars of signal, five fields, and an answer that is honest about its confidence. One app that uses an agent design, an expert system, search, constraints and learning, each where it fits. Thank you. I'm happy to take questions."

---

## 4. Viva prep: 15 likely questions

1. **What kind of agent is this?**
   A layered rational agent. The capture check is a simple reflex agent (condition-action rules on brightness and Laplacian variance). Field extraction is model-based: it keeps derived facts such as the title and whether expiry evidence exists. Answer selection is utility-based: it maximises the start + end logit. Learning happens offline, so it is not an online learning agent.

2. **Why is the environment partially observable and stochastic?**
   The agent sees only pixels. Glare, stamps and folds hide true values, and OCR adds noise. Two photos of the same certificate produce different text, so the outcome of "read this page" is not deterministic.

3. **Where exactly is search in the app?**
   Three places:
   - Dictionary correction: generate-and-test over 206 words with Levenshtein dynamic programming.
   - Greedy best-path CTC decoding for handwriting.
   - Pruned span search in QA: top 12 starts × top 12 ends.

   UCS and A* are in the `ml/ai_eval` experiment, where A* expanded about 3× fewer nodes than UCS for the same answers.

4. **Is your A* heuristic admissible?**
   Yes. h = 0.4 × the smallest gap between the remaining noisy length and any reachable word length. Each unit of length gap needs at least one length-changing operation, and the cheapest one costs 0.4. It is also consistent, because one step changes h by at most that step's cost.

5. **Why is the field extractor an expert system and not "just regex"?**
   Regex is the knowledge representation. The system also has:
   - working memory (lines and derived facts)
   - an inference engine with a gate, ordered rule chains and evidence-gated fallback
   - conflict resolution (priority, recency, specificity)
   - a knowledge-acquisition process (corpus reading → rules → regression tests)

   It lacks an explanation facility, which is proposed.

6. **Forward or backward chaining?**
   Both views fit:
   - Data-driven forward chaining: OCR lines → the name rule asserts a title → the header rule fires on that title to assert the authority.
   - Backward, per field: the goal "expiry" tries ordered sub-goals until one succeeds, like Prolog clause order.

7. **How does the CSP improve on the rules?**
   The rules judge each field alone. The CSP adds cross-field constraints (expiry after issue, port in a gazetteer, organisation-like authority) and searches with MRV and forward checking. On 2,000 synthetic cases, record exact match went from 16.4% to 86.2%. It cannot fix a wrong value that satisfies all constraints (0/276). It is not in the app yet.

8. **What is MRV and did it help?**
   Minimum remaining values: assign the variable with the fewest legal values first, so failures appear early. On five variables it saved little: 10.4 vs 11.0 nodes per case. Its value grows with problem size.

9. **What is weak supervision and what is its risk?**
   Labels come from an automatic teacher (my rules and OCR pseudo-labels at confidence ≥ 0.85), not from humans. The risk is learning the teacher's errors. That is why weak validation (71.4%) is higher than the human-checked gold set (57.7%), and why only the gold number is quoted as evidence.

10. **How did you prevent data leakage?**
    - Split by source PDF, never by page (hashed name into buckets).
    - Remove exact duplicate pages by image hash.
    - Remove duplicate page text across splits by fingerprint.
    - Hold out the six gold documents and their whole template group.

    Pages of one certificate share names and dates, so a page split would let the model memorise answers.

11. **Why exact match instead of F1?**
    A field is copied, not summarised. A date with one missing digit is wrong, but token F1 would give it partial credit. The evaluator normalises only case, whitespace and edge punctuation.

12. **Did the model overfit? How do you know?**
    The data is tiny (160 aligned training rows), and weak-val beats gold by about 14 points, so it fits the label style. Guards:
    - early stopping (patience 5)
    - best-epoch checkpoint by per-field macro exact match
    - document-level splits
    - a small pretrained model

    With 26 gold fields, each one is 3.8 points, so the estimate is wide.

13. **How does extractive QA work, and why not a generative model?**
    The transformer encodes `[CLS] question [SEP] context [SEP]` and predicts a start and an end score per token. The answer is the best-scoring span. It can only highlight existing text, so it cannot hallucinate a date. A generative model could produce a plausible value that is not on the page.

14. **Where would k-means and a decision tree fit?**
    The capture check already is a hand-built depth-3 decision tree. A learned CART tree beat it on synthetic pages (92.7% vs 78.1%), mostly on blur, but needs real calibration. k-means (proposed) would cluster layout geometry (line positions, label positions, density) to discover templates. Uses: template-specific rules and template-disjoint test splits. Choose k by elbow or silhouette.

15. **What is the biggest limitation, and what would you do next?**
    No end-to-end measurement from photograph to five fields. The 57.7% is text-only on 26 fields. Next: a fresh, vessel-disjoint set of photographed certificates with verified fields, including absent fields. Report per-field precision/recall, OCR CER/WER, and median and p95 latency on the phone. In code: move the CSP checker into the app, fix the digit guard and add maritime vocabulary.
