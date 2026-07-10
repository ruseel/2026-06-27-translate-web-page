# Translate WebPage

Translate WebPage turns a web article into a Fluree-backed Korean bilingual reading record: fetched source article, ordered source paragraphs, machine translation candidates, provenance, and a reader UI.

The project is three things at once:

- a personal URL → Korean translation pipeline,
- a shared translation commons backed by Fluree,
- a public reader product for bilingual article pages.

The canonical output is the **Fluree ledger**. HTML files and the public viewer are derived artifacts.

## Quickstart

```bash
bb page https://paulgraham.com/do.html
bb viewer 8000
```

Then open:

```text
http://localhost:8000
```

Small fixture:

```bash
bb page examples/small.defuddle.json translate-small
bb status translate-small example-org-small-translation-test
```

## Commands

```bash
bb fetch <url-or-defuddle-json> [ledger]
bb translate <slug>                  # default ledger: translate
bb translate <ledger> <slug>
bb genhtml <slug>                    # default ledger: translate
bb genhtml <ledger> <slug>
bb viewer [port]
bb status <slug>
bb status <ledger> <slug>
```

## Translation model

Translation uses Pi with `openai-codex/gpt-5.5` and `xhigh` thinking by default. Override with:

```bash
PI_TRANSLATION_MODEL='provider/model' \
PI_TRANSLATION_THINKING='high' \
bb translate <ledger> <slug>
```

Optional provenance fields:

```bash
TWP_TRANSLATOR_IDENTITY='your-name-or-agent' \
TWP_TRANSLATION_LICENSE='license-name' \
bb translate <ledger> <slug>
```

## Docs

- [Architecture](docs/architecture.md)
- [Data model](docs/data-model.md)
- [Pipeline](docs/pipeline.md)
- [Glossary](docs/glossary.md)
- [Roadmap](docs/roadmap.md)
- [Operations](docs/operations.md)
- [ADRs](docs/adr/)
