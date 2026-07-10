# Pipeline

The pipeline turns one source article into ledger records and reader artifacts.

## One-command path

```bash
bb page <url-or-defuddle-json> [ledger]
```

Default ledger:

```text
translate
```

`bb page` runs:

```text
fetch -> translate -> genhtml
```

## Fetch

```bash
bb fetch <url-or-defuddle-json> [ledger]
```

Fetch accepts either:

- a URL, parsed with Defuddle, or
- a local `.json` Defuddle snapshot.

Outputs:

- `out/<slug>.defuddle.json`
- `out/<slug>.jsonld`
- Fluree `WebPage` and `Paragraph` records

## Translate

```bash
bb translate <slug>
bb translate <ledger> <slug>
```

Translate queries paragraphs from Fluree, calls Pi, validates the model output, and inserts translation records.

Default model settings:

```text
PI_TRANSLATION_MODEL=openai-codex/gpt-5.5
PI_TRANSLATION_THINKING=xhigh
```

Override:

```bash
PI_TRANSLATION_MODEL='provider/model' \
PI_TRANSLATION_THINKING='high' \
bb translate <ledger> <slug>
```

Development-only mock mode:

```bash
MOCK_TRANSLATION=1 bb translate <ledger> <slug>
```

Outputs:

- `out/<slug>.translations.jsonld`
- Fluree `TranslationRun` and `TranslatedParagraph` records

## Generate HTML

```bash
bb genhtml <slug>
bb genhtml <ledger> <slug>
```

`genhtml` queries source paragraphs and Korean translations from Fluree and writes a side-by-side HTML artifact.

Output:

- `out/<slug>.translation-pairs.html`

The HTML file is derived from the ledger and can be regenerated.

## Viewer

```bash
bb viewer [port]
```

Default local port is `8123`; deployment uses port `8000`.

Useful URLs:

```text
/                         reader list
/page/<slug>?ledger=...   reader detail page
/v1?ledger=...&slug=...   legacy queryable UI
/api/pages                page JSON
/api/page                 page detail JSON
```

## Status

```bash
bb status <slug>
bb status <ledger> <slug>
```

Reports article metadata, paragraph counts, translation counts, missing translations, and latest run information.
