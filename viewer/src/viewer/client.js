(() => {
  const $ = (id) => document.getElementById(id);
  const html = (value) => String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");

  const bootEl = $("boot-data");
  const boot = bootEl ? JSON.parse(bootEl.textContent) : { ledger: "translate", pages: [], view: null };
  const state = {
    ledger: boot.ledger || "translate",
    pages: boot.pages || [],
    slug: boot.selectedSlug || boot.view?.page?.slug || "",
    language: boot.view?.filters?.selected?.language || "",
    model: boot.view?.filters?.selected?.model || "",
    status: boot.view?.filters?.selected?.status || "",
    view: boot.view,
  };

  const setError = (message) => {
    const el = $("error");
    if (!el) return;
    el.innerHTML = message ? `<div class="error">${html(message)}</div>` : "";
  };

  const option = (value, label, selected) =>
    `<option value="${html(value)}"${value === selected ? " selected" : ""}>${html(label)}</option>`;

  const renderSelectOptions = (el, values, selected, allLabel) => {
    if (!el) return;
    el.innerHTML = option("", allLabel, selected) + (values || []).map((v) => option(v, v, selected)).join("");
  };

  const renderPages = () => {
    const el = $("slug");
    if (!el) return;
    el.innerHTML = state.pages.map((p) => option(p.slug, `${p.title} — ${p.slug}`, state.slug)).join("");
  };

  const renderSummary = (summary = {}) => {
    const el = $("summary");
    if (!el) return;
    el.innerHTML = [
      `${summary.paragraphCount || 0} paragraphs`,
      `${summary.filteredCandidateCount || 0}/${summary.candidateCount || 0} candidates`,
      `${summary.modelCount || 0} models`,
    ].map((x) => `<span class="badge">${html(x)}</span>`).join("");
  };

  const shortId = (s) => s && s.length > 18 ? `…${s.slice(-18)}` : (s || "");

  const candidateHtml = (c) => `
    <article class="candidate" data-candidate-id="${html(c.id)}">
      <div class="candidate-head">
        <strong>${html(c.model)}</strong>
        <span>${html(c.provider)}</span>
        <span>${html(c.language)}</span>
        <span>${html(c.status)}</span>
      </div>
      <p lang="ko">${html(c.text)}</p>
      <details>
        <summary>provenance</summary>
        <dl>
          <dt>generated</dt><dd>${html(c.generatedAt)}</dd>
          <dt>translator</dt><dd>${html(c.translatorIdentity)}</dd>
          <dt>prompt</dt><dd>${html(c.promptName)}</dd>
          <dt>run</dt><dd title="${html(c.run)}">${html(shortId(c.run))}</dd>
        </dl>
      </details>
    </article>`;

  const paragraphHtml = (paragraph) => `
    <section class="paragraph" id="paragraph-${paragraph.position}" data-paragraph="${paragraph.position}">
      <div class="source">
        <div class="eyebrow">#${paragraph.position} · ${html(paragraph.kind)}</div>
        <p lang="en">${html(paragraph.source)}</p>
      </div>
      <div class="translations">
        ${(paragraph.candidates || []).length
          ? paragraph.candidates.map(candidateHtml).join("")
          : '<div class="empty">No translations match these filters.</div>'}
      </div>
    </section>`;

  const renderView = () => {
    const view = state.view;
    if (!view) return;
    const page = view.page || {};
    document.title = `${page.title || "Fluree Translation Reader"} · Translation Reader`;
    if ($("page-title")) $("page-title").textContent = page.title || "Fluree Translation Reader";
    if ($("page-url")) $("page-url").textContent = page.url || "";
    renderSummary(view.summary);
    const filters = view.filters || {};
    renderSelectOptions($("language"), filters.languages || [], state.language, "All languages");
    renderSelectOptions($("model"), filters.models || [], state.model, "All models");
    renderSelectOptions($("status"), filters.statuses || [], state.status, "All statuses");
    const paragraphs = $("paragraphs");
    if (paragraphs) paragraphs.innerHTML = (view.paragraphs || []).map(paragraphHtml).join("");
  };

  const queryString = (params) => {
    const q = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== "") q.set(k, v);
    });
    return q.toString();
  };

  const fetchJson = async (url) => {
    const res = await fetch(url, { headers: { Accept: "application/json" } });
    const data = await res.json();
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
  };

  const loadPages = async () => {
    setError("");
    const data = await fetchJson(`/api/pages?${queryString({ ledger: state.ledger })}`);
    state.pages = data.pages || [];
    if (!state.pages.some((p) => p.slug === state.slug)) state.slug = state.pages[0]?.slug || "";
    renderPages();
  };

  const loadView = async () => {
    if (!state.slug) return;
    setError("");
    const data = await fetchJson(`/api/page?${queryString({
      ledger: state.ledger,
      slug: state.slug,
      language: state.language,
      model: state.model,
      status: state.status,
    })}`);
    state.view = data;
    history.replaceState(null, "", `/?${queryString({ ledger: state.ledger, slug: state.slug, language: state.language, model: state.model, status: state.status })}`);
    renderView();
  };

  const syncFromControls = () => {
    state.ledger = $("ledger")?.value || state.ledger;
    state.slug = $("slug")?.value || state.slug;
    state.language = $("language")?.value || "";
    state.model = $("model")?.value || "";
    state.status = $("status")?.value || "";
  };

  const wire = () => {
    renderPages();
    renderView();
    $("ledger")?.addEventListener("change", async () => {
      try {
        syncFromControls();
        await loadPages();
        await loadView();
      } catch (e) { setError(e.message); }
    });
    $("slug")?.addEventListener("change", async () => {
      try {
        syncFromControls();
        state.language = ""; state.model = ""; state.status = "";
        await loadView();
      } catch (e) { setError(e.message); }
    });
    ["language", "model", "status"].forEach((id) => {
      $(id)?.addEventListener("change", async () => {
        try { syncFromControls(); await loadView(); }
        catch (e) { setError(e.message); }
      });
    });
    $("refresh")?.addEventListener("click", async () => {
      try { syncFromControls(); await loadView(); }
      catch (e) { setError(e.message); }
    });
  };

  document.addEventListener("DOMContentLoaded", wire, { once: true });
})();
