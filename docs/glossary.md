# Glossary

## Translation commons

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Translation Commons** | A shared collection of Korean web-article translations, source text, provenance, and review status stored as ledger records. | Platform, archive, app |
| **Reader** | The first external user: a person who reads bilingual article pages produced from the ledger. | Customer, contributor, operator |
| **Trusted Contribution** | A machine-only Korean translation with provenance recorded in the ledger. | Reviewed translation, corrected translation, approved translation |
| **Korean Translation** | The fixed target-language translation for an extracted web article. | Target translation, localized text |

## Ledger and artifacts

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **Fluree Ledger** | The canonical record of source articles, paragraphs, translation runs, translated paragraphs, and provenance. | Database, output file, archive |
| **Hosted Ledger** | The project owner's Fluree ledger that contributors may publish to. | Server, database, production DB |
| **Contributor Ledger** | A contributor-owned Fluree ledger that can hold the same translation records as the hosted ledger. | Fork, local copy, private DB |
| **Public Viewer** | The reader-facing web UI that renders records from the ledger. | Canonical output, source of truth |
| **Rendered HTML** | A generated reading artifact derived from ledger records. | Canonical output, source of truth |

## Source and translation records

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **WebPage** | The extracted source article record in the ledger. | Article file, page JSON |
| **Paragraph** | An ordered unit of source text belonging to a WebPage. | Block, chunk, source row, segment |
| **TranslationRun** | One machine translation attempt for a WebPage with model, prompt, identity, license, and timestamp provenance. | Job, batch, invocation |
| **TranslatedParagraph** | The Korean translation of one Paragraph produced by one TranslationRun. | Translation row, candidate row |
| **Translation Candidate** | One available TranslatedParagraph option for a source Paragraph. | Final translation, chosen translation |

## Relationships

- A **WebPage** has one or more **Paragraphs**.
- A **WebPage** may have zero or more **TranslationRuns**.
- A **TranslationRun** produces one **TranslatedParagraph** for each source **Paragraph**.
- A **Paragraph** may have multiple **Translation Candidates** across different **TranslationRuns**.
- The **Public Viewer** and **Rendered HTML** read from the **Fluree Ledger**.
- A **Trusted Contribution** can be machine-only if it records provenance.

## Example dialogue

> **Dev:** "Is the HTML file the output of the pipeline?"
>
> **Domain expert:** "No. The **Fluree Ledger** is the canonical output. The **Rendered HTML** is just a derived artifact."
>
> **Dev:** "Can a **Reader** see multiple translations for the same paragraph?"
>
> **Domain expert:** "Yes. Each **TranslationRun** can produce a different **Translation Candidate** for the same **Paragraph**."
>
> **Dev:** "Does a translation need human review before it enters the commons?"
>
> **Domain expert:** "No. A **Trusted Contribution** can be machine-only as long as provenance is recorded."

## Flagged ambiguities

- "Output" should mean the **Fluree Ledger** unless explicitly qualified as **Rendered HTML** or **Public Viewer** output.
- "Translation" can mean a full **TranslationRun**, one **TranslatedParagraph**, or a visible **Translation Candidate**; use the precise term when discussing data model behavior.
- "Ledger" should be qualified as **Hosted Ledger** or **Contributor Ledger** when publishing topology matters.
