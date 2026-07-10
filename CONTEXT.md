# Translate WebPage

Translate WebPage is a Korean translation commons for web articles. It supports a personal translation pipeline, a shared translation ledger, and a public reader product, with the Fluree ledger as the canonical record.

When those identities conflict, the translation commons wins first, the public reader second, and the personal pipeline third.

## Language

**Translation Commons**:
A shared collection of Korean web-article translations, source text, provenance, and review status stored as ledger records.
_Avoid_: Platform, archive, app

**Reader**:
The first external user: a person who reads bilingual article pages produced from the ledger.
_Avoid_: Customer, contributor, operator

**Fluree Ledger**:
The canonical record of source articles, paragraphs, translation runs, translated paragraphs, and provenance.
_Avoid_: Database, output file, archive

**Hosted Ledger**:
The project owner's Fluree ledger that contributors may publish to.
_Avoid_: Server, database, production DB

**Contributor Ledger**:
A contributor-owned Fluree ledger that can hold the same translation records as the hosted ledger.
_Avoid_: Fork, local copy, private DB

**Trusted Contribution**:
A machine-only Korean translation with provenance recorded in the ledger.
_Avoid_: Reviewed translation, corrected translation, approved translation

**Korean Translation**:
The fixed target-language translation for an extracted web article.
_Avoid_: Target translation, localized text

**Public Viewer**:
The reader-facing web UI that renders records from the ledger.
_Avoid_: Canonical output, source of truth

**Rendered HTML**:
A generated reading artifact derived from ledger records.
_Avoid_: Canonical output, source of truth

**WebPage**:
The extracted source article record in the ledger.
_Avoid_: Article file, page JSON

**Paragraph**:
An ordered unit of source text belonging to a WebPage.
_Avoid_: Block, chunk, source row, segment

**TranslationRun**:
One machine translation attempt for a WebPage with model, prompt, identity, license, and timestamp provenance.
_Avoid_: Job, batch, invocation

**TranslatedParagraph**:
The Korean translation of one Paragraph produced by one TranslationRun.
_Avoid_: Translation row, candidate row

**Translation Candidate**:
One available TranslatedParagraph option for a source Paragraph.
_Avoid_: Final translation, chosen translation
