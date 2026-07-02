CATEGORY=Pilot-and-Scale; USING=Pilot-and-Scale

**The whole:** A shared translation commons: anyone can submit a URL, run extraction + LLM translation with their own model/provider, publish the bilingual result into a Fluree ledger, and others can consume, verify, improve, and reuse translations. The first living thing is not “the full platform”; it is one trustworthy URL → extracted article → translated HTML → ledger record → readable output loop.

## Sequence

1. **Define the smallest shared record: one translated article in Fluree.**  
   Before scaling people or LLMs, decide what one successful contribution looks like: source URL, extracted text, original paragraphs, Korean translation, translator identity, model/provider used, timestamps, license/status, and rendered HTML reference.

2. **Make one local vertical slice work end-to-end.**  
   For a single URL: run Defuddle, extract the main article, translate with one LLM, assemble side-by-side HTML, and write/read the result from Fluree. This becomes the living seed.

3. **Separate the pipeline into Fluree-backed stable contracts.**  
   In this repo, the stage boundary is the Fluree ledger, not separate translation input/output files.  
   `fetch` inserts the source article into Fluree as a predefined `WebPage` plus ordered `Paragraph` records.  
   `translate` queries Fluree for that `WebPage` and its `Paragraph` records, runs translation internally, and does not expose or depend on a separate translation input/output file contract.  
   After translation completes, `translate` inserts the result back into Fluree in a predefined form: `TranslationRun` plus ordered `TranslatedParagraph` records.  
   Consumers such as `genhtml` then query Fluree and render from those records.

4. **Let contributors bring their own LLM.**  
   Add provider adapters/configuration: OpenAI, Anthropic, local Ollama, Gemini, etc. The shared system should not require one central API key. Each contributor runs translation with their own credentials, but publishes to the same schema.

5. **Add provenance and trust before adding many users.**  
   Every translation record should say: who/what produced it, source content hash, model name, prompt/version, time, and whether it is machine-only, human-reviewed, or corrected. This protects the commons from becoming anonymous uncheckable text.

6. **Create a small contribution CLI.**  
   Something like:

   ```bash
   translate-page submit https://example.com/article
   translate-page publish result.json
   translate-page render <article-id>
   ```

   The CLI is the first usable “front door” for collaborators.

7. **Run a pilot with 3–5 trusted contributors and 10–20 URLs.**  
   Do not open it broadly yet. Have a few people use different LLMs and publish to the same Fluree ledger. Watch where the schema breaks: duplicate URLs, bad extraction, mistranslation, failed HTML, conflicting translations.

8. **Add deduplication and versioning.**  
   Same URL may be translated many times. Use source URL + content hash as the article identity, and allow multiple translation versions. Consumers can choose latest, reviewed, specific model, or preferred contributor.

9. **Build the consumer view.**  
   A simple reader that queries Fluree and displays: English left, Korean right, paragraph-aligned, with metadata and alternate translations. This makes the ledger useful, not just archival.

10. **Add review/correction as a second contribution type.**  
   After machine translations exist, people should be able to submit corrections, paragraph edits, or reviewed versions. This turns the initiative from “many LLM outputs” into a growing translation knowledge base.

11. **Document the public contribution path.**  
   Write a short README:

   - install
   - configure your LLM
   - submit URL
   - inspect output
   - publish to Fluree
   - consume translations
   - contribution rules / license / provenance expectations

12. **Only then widen participation.**  
   Invite more people once the seed loop, schema, provenance, CLI, and reader are stable. Scaling comes after the small commons already works.
