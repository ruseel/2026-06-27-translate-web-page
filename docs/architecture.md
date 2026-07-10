# Architecture

Translate WebPage is a Fluree-centered Korean translation system for web articles. It supports a personal pipeline, a shared translation commons, and a public reader, but all three are subordinate to one rule: the Fluree ledger is the canonical output.

## Priorities

When product identities conflict, optimize in this order:

1. **Translation commons** — preserve interoperable ledger records, provenance, and schema stability.
2. **Public reader** — make the ledger useful to readers through a clear bilingual UI.
3. **Personal pipeline** — keep local workflows convenient without compromising the shared record.

## System shape

```text
URL or Defuddle JSON
  -> fetch
  -> Fluree WebPage + Paragraph records
  -> translate
  -> Fluree TranslationRun + TranslatedParagraph records
  -> viewer / genhtml
  -> reader-facing pages and generated HTML artifacts
```

The stage boundary is the ledger. Intermediate JSON files in `out/` are debug artifacts, not contracts.

## Components

### Fetch

`src/fetch.clj` receives a URL or local Defuddle JSON snapshot, extracts the article, normalizes paragraphs, computes a source content hash, and inserts `WebPage` plus ordered `Paragraph` records into Fluree.

### Translate

`src/translate.clj` queries source paragraphs from Fluree, renders `prompts/translate-web-page-v1.md`, calls Pi, validates the returned JSON array, and inserts a `TranslationRun` plus ordered `TranslatedParagraph` records.

Machine-only Korean translation is a valid trusted contribution as long as provenance is recorded.

### Render

`src/genhtml.clj` queries the ledger and writes deterministic side-by-side HTML into `out/`. This HTML is derived from the ledger and is not canonical.

### Viewer

`viewer/src/viewer/server.clj`, `viewer/src/viewer/data.clj`, `viewer/src/viewer/v2.clj`, and browser assets serve the reader list and page view. The viewer reads Fluree records and can display multiple translation candidates with provenance.

## Code map

```text
src/
  common.clj      shared JSON-LD context, Fluree shell calls, slug/hash helpers
  fetch.clj       URL/Defuddle JSON -> WebPage + Paragraph records
  translate.clj   Paragraph records -> TranslationRun + TranslatedParagraph records
  genhtml.clj     ledger records -> generated side-by-side HTML
  status.clj      ledger inspection command
  pipeline.clj    fetch -> translate -> genhtml orchestration
viewer/src/viewer/
  data.clj        Fluree queries and view models
  server.clj      HTTP server and API routes
  v2.clj          current reader/list HTML shell
  client.cljs     legacy v1 CLJS source
prompts/
  translate-web-page-v1.md canonical translation prompt
examples/
  small fixture input
out/
  generated snapshots, JSON-LD exports, debug files, and HTML artifacts
```

## Deployment shape

The public deployment routes `good-writes.rsl.kr` to the viewer and `fluree.rsl.kr` to the Fluree HTTP API through the exe.dev VM proxy and nginx. Operational details live in [operations.md](operations.md).
