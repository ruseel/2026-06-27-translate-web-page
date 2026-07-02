# Translate Web Page for Pi

This project is a workspace for building a **Pi skill** and a matching **Pi prompt template** that translate web articles into a side-by-side bilingual reading page.

The skill and the prompt template should share the same underlying prompt and workflow so the behavior stays consistent whether it is invoked as a reusable skill or as a direct Pi prompt.

## Goal

Given a URL, the system should:

1. Extract the readable article body with Defuddle.
2. Convert the extracted Defuddle JSON into JSON-LD, using schema.org concepts where possible.
3. Store the source web page, original paragraphs, and translated paragraphs in Fluree.
4. Use Pi/LLM to create Korean translations for the original paragraphs.
5. Record provenance: source content hash, Defuddle version, prompt hash, model/provider, translator identity, license, status, and timestamps.
6. Query the populated original/translated paragraph pairs from Fluree.
7. Assemble the final result into HTML with the original English on the left and Korean translation on the right.

The final HTML should be produced by deterministic Clojure program code, not by asking the LLM to generate a whole HTML document.

## Directory Structure

```text
src/
  common.clj
  fetch.clj
  translate.clj
  genhtml.clj
  status.clj
  pipeline.clj
prompts/
examples/
out/        # generated, ignored
```

### `src/fetch.clj`

Clojure program for fetching and normalizing a web page.

Responsibilities:

- receive a URL,
- run Defuddle and get JSON output,
- convert Defuddle JSON into JSON-LD,
- model the data with schema.org in mind,
- use `WebPage` and `Paragraph` entities,
- insert the resulting JSON-LD into Fluree.

Functions should be implemented as a clear logical progression: fetch, parse, normalize, transform to JSON-LD, validate, and insert.

### `prompts/`

Canonical prompt templates used by both code and Pi-facing workflows. The current translator prompt is `prompts/translate-web-page-v1.md`; its SHA-256 hash is stored on every `TranslationRun`.

### `src/translate.clj`

Clojure program for translation orchestration through Pi.

Responsibilities:

- fetch the `WebPage` and ordered `Paragraph` records from Fluree,
- call Pi with the shared translation prompt,
- ask for the full translation in one go,
- parse/validate the returned translations,
- insert translated paragraph records back into Fluree,
- record provenance and machine/human status metadata.

The translation prompt used here should be the same prompt used by the Pi skill/prompt template.

### `src/genhtml.clj`

Clojure program for HTML generation.

Responsibilities:

- query original English paragraphs and Korean translations from Fluree,
- preserve paragraph order,
- assemble side-by-side HTML,
- place original English sentence/paragraph on the left,
- place translated Korean sentence/paragraph on the right.

Visual design and aesthetics will be worked on separately. `src/genhtml.clj` should focus on correct structure, safe escaping, and deterministic assembly.

### `src/status.clj`

Small inspection command for the living seed. It reports article metadata, source hash, paragraph counts, translation version counts, missing translations, and latest run info.

### `out/`

Generated artifact directory for Defuddle JSON snapshots, JSON-LD exports, translation debug files, and final HTML. It is ignored by git and can be safely regenerated.

## Why Fluree

Fluree is used as the database for the translation workflow. Based on the Fluree documentation index at <https://fluree.github.io/db/llms.txt>, it provides a semantic graph database with JSON-LD/RDF data modeling, query support, time travel, branching, CLI access, and library integration.

The project should model at least these entities:

- `WebPage` — the fetched source URL and page metadata.
- `Paragraph` — ordered paragraph/block records extracted from the page.
- translated paragraph records — Korean translations linked back to the original paragraph.
- `TranslationRun` — model/provider, prompt hash, translator identity, license, machine/review status, and timestamp.

This makes the workflow inspectable, replayable, and easier to improve than a single one-shot LLM output.

## Architecture

```text
URL
  -> src/fetch.clj
  -> Defuddle JSON
  -> schema.org-minded JSON-LD
  -> WebPage + Paragraph records in Fluree
  -> src/translate.clj
  -> Pi shared prompt / skill prompt
  -> translated paragraph records in Fluree
  -> src/genhtml.clj
  -> side-by-side HTML
```

## Rendering Strategy

The LLM should only be responsible for translation and interpretation. It should not be responsible for hand-writing the final page markup.

The HTML assembler will be implemented in Clojure and will:

- read ordered paragraph pairs from Fluree,
- preserve paragraph order and block kinds,
- escape text safely,
- render English and Korean columns side by side,
- leave aesthetic design/styling as a separate concern.

## Runnable Pipeline

Run the full Clojure pipeline with a URL or local Defuddle JSON file:

```bash
bb page <url-or-defuddle-json> [ledger]
```

The default ledger is `translate`, so the normal command is:

```bash
bb page https://paulgraham.com/do.html
```

This executes:

1. `src/fetch.clj` — run Defuddle, convert the result to schema.org-minded JSON-LD, and insert `WebPage`/`Paragraph` records into Fluree.
2. `src/translate.clj` — query paragraphs from Fluree, call Pi once for the full translation, validate the returned JSON, and insert `TranslatedParagraph` records plus a `TranslationRun` record containing the LLM model and thinking effort used.
3. `src/genhtml.clj` — query original/translated paragraph pairs from Fluree and write side-by-side HTML.

Individual stages are also runnable. They use the `translate` ledger by default:

```bash
bb fetch <url-or-defuddle-json> [ledger]
bb translate <slug>
bb genhtml <slug>
```

To override the ledger for translate/genhtml, pass it explicitly before the slug:

```bash
bb translate <ledger> <slug>
bb genhtml <ledger> <slug>
```

Inspect the living state:

```bash
bb status <slug>
bb status <ledger> <slug>
```

Small real-use test fixture:

```bash
bb page examples/small.defuddle.json translate-small
bb status translate-small example-org-small-translation-test
```

For plumbing tests only, without calling Pi:

```bash
MOCK_TRANSLATION=1 bb page examples/small.defuddle.json translate-dev
```

Pi options can be recorded explicitly with environment variables:

```bash
PI_TRANSLATION_MODEL='google/gemini-2.5-pro' PI_TRANSLATION_THINKING='high' bb translate translate paulgraham-com-do-html
```

## Current Living Seed

The repository now contains the Fluree-backed Clojure vertical slice:

- `src/fetch.clj` — Defuddle/local JSON to `WebPage` + ordered `Paragraph` JSON-LD.
- `src/translate.clj` — paragraph query, prompt rendering, Pi/mock translation, provenance-rich `TranslationRun` and `TranslatedParagraph` insertion.
- `src/genhtml.clj` — deterministic side-by-side reader HTML.
- `src/status.clj` — article/run inspection.
- `src/common.clj` — shared JSON-LD, Fluree, shell, slug/hash, and HTML helpers.
- `src/pipeline.clj` — orchestration for `bb page`.
- `prompts/translate-web-page-v1.md` — canonical translator prompt.

The old ad-hoc Python HTML assembly path has been removed/replaced.

## Non-goals

- Do not generate the final HTML entirely with the LLM.
- Do not bake visual/aesthetic decisions into the translation prompt.
- Do not collapse source text and translations into an opaque blob; keep paragraph records structured and queryable.
