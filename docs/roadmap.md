# Roadmap

Translate WebPage is a personal pipeline, a shared translation commons, and a public reader product. The long-term goal is a Korean translation commons where anyone can submit a URL, run extraction and LLM translation with their own model/provider, publish records into a Fluree ledger, and let readers consume the bilingual result.

The first living thing is one trustworthy loop:

```text
URL -> extracted article -> Korean machine translation -> Fluree ledger record -> reader page
```

## Sequence

1. **Stabilize the smallest shared record.**  
   One successful contribution is a source URL, extracted text, ordered source paragraphs, Korean translated paragraphs, translator identity, model/provider, prompt hash, timestamps, license/status, and enough metadata for a reader to inspect the result.

2. **Keep the local vertical slice healthy.**  
   For a single URL: run Defuddle, extract the main article, translate with one LLM, insert source and translation records into Fluree, and render a side-by-side reader page.

3. **Keep Fluree as the stage boundary.**  
   `fetch` inserts `WebPage` and `Paragraph` records. `translate` queries those records and inserts `TranslationRun` plus `TranslatedParagraph` records. `genhtml` and the viewer query those records and render from them. File artifacts are debug outputs, not contracts.

4. **Improve the reader experience.**  
   The first external user is the reader. The public viewer should make bilingual reading pleasant, expose provenance clearly, and handle multiple translation candidates without confusing the reading flow.

5. **Let contributors bring their own LLM.**  
   Add provider adapters/configuration for OpenAI, Anthropic, local Ollama, Gemini, and other Pi-supported providers. The commons should not depend on one central API key.

6. **Support both hosted and contributor ledgers.**  
   Contributors should eventually be able to publish either to the hosted ledger or to their own Fluree ledger. The schema should work in both topologies.

7. **Add deduplication and versioning.**  
   Same URL may be translated many times. Use source URL plus source content hash as the meaningful article identity and allow multiple translation runs. Consumers can choose latest, model-specific, contributor-specific, or eventually reviewed translations.

8. **Create a small contribution CLI.**  
   Something like:

   ```bash
   translate-page submit https://example.com/article
   translate-page publish --ledger hosted <slug>
   translate-page publish --ledger ./my-ledger <slug>
   translate-page render <slug>
   ```

9. **Run a pilot with 3–5 trusted contributors and 10–20 URLs.**  
   Use different LLMs and publish to compatible ledgers. Watch where the schema breaks: duplicate URLs, bad extraction, mistranslation, failed HTML, conflicting translation candidates, and unclear provenance.

10. **Add review/correction as a second contribution type.**  
    Machine-only translation is enough for initial trust, but later contributors should be able to submit corrections, paragraph edits, or human-reviewed versions.

11. **Document the public contribution path.**  
    Write concise contributor docs: install, configure an LLM, submit URL, inspect output, publish to a ledger, consume translations, and follow provenance/license expectations.

12. **Only then widen participation.**  
    Invite more people once the seed loop, schema, provenance, ledger publishing story, and reader are stable.
