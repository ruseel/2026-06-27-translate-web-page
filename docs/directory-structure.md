## Directory Structure

```text
src/
  common.clj
  fetch.clj
  translate.clj
  genhtml.clj
  status.clj
  pipeline.clj
viewer/
  src/viewer/
    data.clj
    server.clj
    client.cljs
    client.js
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

### `viewer/src/viewer/`

Small queryable viewer grown from the vertical slice:

- `data.clj` queries Fluree for pages, source segments, and all matching `TranslatedParagraph` candidates with `TranslationRun` provenance.
- `server.clj` serves the Datastar-ready HTML shell plus `/api/pages` and `/api/page` JSON endpoints via `bb viewer`.
- `client.cljs` is the CLJS source for browser interactions; `client.js` is the currently served browser artifact.

### `out/`

Generated artifact directory for Defuddle JSON snapshots, JSON-LD exports, translation debug files, and final HTML. It is ignored by git and can be safely regenerated.
