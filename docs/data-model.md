# Data model

The Fluree ledger is the canonical output of Translate WebPage. Records are JSON-LD using schema.org terms plus project-specific terms under the Translate WebPage namespace.

## Identity

### Article identity

A source article is represented by a `WebPage`.

Current identity is based on the slug derived from the source URL or local Defuddle JSON metadata:

```text
urn:twp:page:<slug>
```

The content identity is recorded separately as `sourceContentHash` using the cleaned paragraph text:

```text
sha256:<hash>
```

Long-term deduplication should treat source URL plus source content hash as the meaningful article identity.

### Paragraph identity

Each source paragraph has a stable per-page position:

```text
urn:twp:page:<slug>/paragraph/<position>
```

Positions are one-based and preserve article order.

### Translation run identity

A translation run groups all translated paragraphs produced by one model invocation:

```text
urn:twp:page:<slug>/translation-run/<timestamp-or-generated-id>
```

A page may have many translation runs.

## Records

## WebPage

A `WebPage` is the extracted source article.

Important fields:

| Field | Meaning |
| --- | --- |
| `@id` | page ID |
| `@type` | `WebPage` |
| `url` / `sourceUrl` | original article URL |
| `slug` | local page slug |
| `sourceContentHash` | SHA-256 hash of normalized source content |
| `defuddleVersion` | Defuddle version used for extraction |
| `name` / `headline` | article title |
| `creationDate` | article date when available |
| `hasPart` | references to ordered source paragraphs |

## Paragraph

A `Paragraph` is one ordered source-text unit extracted from the article.

Important fields:

| Field | Meaning |
| --- | --- |
| `@id` | paragraph ID |
| `@type` | `Paragraph` |
| `position` | one-based order within the page |
| `blockKind` | `p`, `h1`, `h2`, `h3`, `quote`, `li`, `date`, or `thanks` |
| `text` | cleaned English source text |
| `inLanguage` | `en` |
| `isPartOf` | parent `WebPage` |

## TranslationRun

A `TranslationRun` is one machine translation attempt for a page.

Important fields:

| Field | Meaning |
| --- | --- |
| `@id` | run ID |
| `@type` | `TranslationRun` |
| `isPartOf` | translated `WebPage` |
| `llmProvider` | provider name, usually `pi` |
| `llmModel` | model identifier |
| `thinkingEffort` | Pi thinking effort |
| `promptName` | prompt identifier, currently `translate-web-page/v1` |
| `promptHash` | SHA-256 hash of the prompt template |
| `translationStatus` | currently `machine` |
| `translatorIdentity` | contributor or local agent identity |
| `license` | declared translation license |
| `generatedAt` / `dateCreated` | generation timestamp |

## TranslatedParagraph

A `TranslatedParagraph` is the Korean translation of one source paragraph in one translation run.

Important fields:

| Field | Meaning |
| --- | --- |
| `@id` | translated paragraph ID |
| `@type` | `TranslatedParagraph` |
| `position` | source paragraph position |
| `text` | Korean translation |
| `inLanguage` | `ko` |
| `isPartOf` | translated `WebPage` |
| `translationOfWork` | source `Paragraph` |
| `translationRun` | producing `TranslationRun` |
| `translationStatus` | currently `machine` |

## Versioning

The ledger may contain multiple translation candidates for the same source paragraph. Candidates differ by `translationRun`, model, prompt hash, translator identity, timestamp, or license.

Consumers should not assume there is exactly one translation. The viewer should expose alternates; generated HTML may choose a default candidate such as latest run.

## Contribution baseline

The minimum trusted contribution is a machine-only Korean translation with provenance. Human review and correction can become later contribution types, but they are not required for a translation to enter the commons.
