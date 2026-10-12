# Plagiarism and originality check — IVP paper

Paper: *NavCert Offline: Classical Image Processing for Robust On-Device Digitization of Maritime Certificates* (`paper/NavCert_Offline_IEEE.pdf`, 8 pages, 41 references).
Method: `scholar-ethics` MODE 2 (open-scholar-skill 5.22.2) checklist, plus two measured checks.
Date: 12 Oct 2026.

## Measured checks

| Check | Method | Result |
|---|---|---|
| Text recycling vs. first draft | Word n-gram overlap of the paper body (5,398 words) against the September draft (3,203 words), references excluded | 0.2% of 6-grams shared, 0.0% of 8-grams; the only shared run is the list of the five field names |
| Verbatim web matches | Exact-phrase web searches for six distinctive sentences from the Introduction, Related Work and Discussion | No source contains any of the six sentences |

This is a spot check, not a similarity report. Turnitin or iThenticate compares the full text against subscription databases that a web search cannot reach.

## Checklist (MODE 2, Step 2.2)

| Section | Finding | Status |
|---|---|---|
| Introduction | Every statistic (BIMCO/ICS seafarer count, Tokyo MoU deficiencies, IMO forgery study, IMO e-certificate guidance) carries a citation | Pass |
| Related Work | Short paraphrased summaries, each followed by a citation; no long passage tracks one source | Pass |
| Methods | Equations are standard textbook forms (Canny, Otsu, CLAHE, DCT, SAD) with citations to the original papers | Pass |
| Results | All tables and figures were generated for this paper from `ml/ivp_eval` | Pass |
| Figures | No figure is reproduced from another paper; app screenshots are the author's own | Pass |
| References | 41 entries checked against DOIs, Crossref or the publisher page during drafting | Pass |

## Items that need action

1. **AI-assistance disclosure (required).** The manuscript text, figures and experiments were produced with substantial help from AI tools (Claude Code). Under COPE and ICMJE guidance and NMIMS academic-integrity rules, this must be declared, and AI-text detectors used by Turnitin may flag the prose. Suggested statement for the Acknowledgment section:
   > The author used Claude (Anthropic) to assist with drafting the manuscript text, writing evaluation and figure scripts, and checking references. All experiments were run by the author, all numbers were checked against the generated result files, and the author takes full responsibility for the content.
2. **Data acknowledgement.** The fine-tuned field model (Section VI) was trained on a certificate corpus supplied by a third party. Name or thank the provider in general terms, with their permission, and state that the documents are not redistributed.

## Originality statement (paste-ready)

> *NavCert Offline: Classical Image Processing for Robust On-Device Digitization of Maritime Certificates* is original work by the author and has not been published or submitted elsewhere. An earlier internal draft by the same author is superseded by this version. AI assistance used in preparing the manuscript is disclosed in the Acknowledgment. The author confirms that all cited sources exist and are represented accurately.
