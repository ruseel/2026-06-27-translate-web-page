# Translate WebPage

## How to run

```bash
bb page <url-or-defuddle-json> [ledger]
```

The default ledger is `translate`, so the normal command is:

```bash
bb page https://paulgraham.com/do.html
```

```bash
bb fetch <url-or-defuddle-json> [ledger]
bb translate <slug>
bb genhtml <slug>
bb viewer [port]
```

Open the queryable multiple-LLM viewer:

```bash
bb viewer 8123
# then visit http://localhost:8123/?ledger=translate&slug=paulgraham-com-foundermode-html
```

Translation uses Pi with `openai-codex/gpt-5.5` and `xhigh` thinking by default. Contributors can bring their own configured Pi model/provider by overriding:

```bash
PI_TRANSLATION_MODEL='provider/model' PI_TRANSLATION_THINKING='high' bb translate <ledger> <slug>
```

Small real-use test fixture:

```bash
bb page examples/small.defuddle.json translate-small
bb status translate-small example-org-small-translation-test
```

## Current Living Seed

The repository now contains the Fluree-backed Clojure vertical slice:

- `src/fetch.clj` — Defuddle/local JSON to `WebPage` + ordered `Paragraph` JSON-LD.
- `src/translate.clj` — paragraph query, prompt rendering, Pi/mock translation, provenance-rich `TranslationRun` and `TranslatedParagraph` insertion.
- `src/genhtml.clj` — deterministic side-by-side reader HTML.
- `src/status.clj` — article/run inspection.
- `src/common.clj` — shared JSON-LD, Fluree, shell, slug/hash, and HTML helpers.
- `src/pipeline.clj` — orchestration for `bb page`.
- `viewer/src/viewer/data.clj` — Fluree queries for pages, segments, and multiple translation candidates.
- `viewer/src/viewer/server.clj` — Datastar-ready HTTP viewer/API for `bb viewer`.
- `viewer/src/viewer/client.cljs` — CLJS browser source for the viewer interactions.
- `prompts/translate-web-page-v1.md` — canonical translator prompt.
