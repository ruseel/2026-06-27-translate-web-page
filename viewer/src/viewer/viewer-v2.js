(() => {
  const STORAGE_KEY = "translation-reader-v2";
  const $ = (id) => document.getElementById(id);
  const html = (value) => String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");

  const bootEl = $("boot-data");
  const boot = bootEl ? JSON.parse(bootEl.textContent) : { view: null };
  const stored = (() => {
    try {
      return JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
    } catch (_) {
      return {};
    }
  })();

  const state = {
    view: boot.view,
    layout: stored.layout || "side-by-side",
    theme: stored.theme || "sepia",
    textSize: stored.textSize || 20,
  };

  const persistReaderState = () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      layout: state.layout,
      theme: state.theme,
      textSize: state.textSize,
    }));
  };

  const selectedCandidate = (segment) => (segment.candidates || [])[0] || null;

  const sourceHtml = (segment) => `
    <p class="v2-text v2-source" lang="en">${html(segment.source)}</p>`;

  const translationHtml = (segment) => {
    const candidate = selectedCandidate(segment);
    if (!candidate) {
      return '<p class="v2-text v2-translation v2-empty">No translation matches this page.</p>';
    }
    return `<p class="v2-text v2-translation" lang="${html(candidate.language || "ko")}">${html(candidate.text)}</p>`;
  };

  const paragraphHtml = (segment) => {
    const parts = [];
    if (state.layout !== "ko-only") parts.push(sourceHtml(segment));
    if (state.layout !== "en-only") parts.push(translationHtml(segment));
    return `
      <section class="v2-paragraph" id="segment-${html(segment.position)}" data-layout="${html(state.layout)}">
        ${parts.join("")}
      </section>`;
  };

  const renderReader = () => {
    const el = $("segments");
    if (!el) return;
    const segments = state.view?.segments || [];
    el.innerHTML = segments.length
      ? segments.map(paragraphHtml).join("")
      : '<p class="v2-empty">No page segments found.</p>';
  };

  const renderFrame = () => {
    const page = state.view?.page || {};
    const title = page.title || "Translation Reader";
    document.title = `${title} - Translation Reader`;

    if ($("page-title")) $("page-title").textContent = title;
    if ($("page-url")) {
      $("page-url").textContent = page.url || "";
      $("page-url").setAttribute("href", page.url || "#");
      $("page-url").hidden = !page.url;
    }
    if ($("nav-meta")) $("nav-meta").textContent = page.url || title;
    if ($("reader-main")) $("reader-main").dataset.layout = state.layout;
  };

  const renderControls = () => {
    document.documentElement.dataset.theme = state.theme;
    document.documentElement.style.setProperty("--reader-size", `${state.textSize}px`);
    if ($("text-size")) $("text-size").textContent = String(state.textSize);

    document.querySelectorAll("[data-layout]").forEach((button) => {
      button.setAttribute("aria-pressed", String(button.dataset.layout === state.layout));
    });
    document.querySelectorAll("[data-theme]").forEach((button) => {
      button.setAttribute("aria-pressed", String(button.dataset.theme === state.theme));
    });
  };

  const updateProgress = () => {
    const totalHeight = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
    const progress = Math.max(0, Math.min(100, (window.scrollY / totalHeight) * 100));
    if ($("progress")) $("progress").style.width = `${progress}%`;
  };

  const render = () => {
    renderFrame();
    renderReader();
    renderControls();
    updateProgress();
  };

  const wireReaderControls = () => {
    document.querySelectorAll("[data-layout]").forEach((button) => {
      button.addEventListener("click", () => {
        state.layout = button.dataset.layout;
        persistReaderState();
        render();
      });
    });

    document.querySelectorAll("[data-theme]").forEach((button) => {
      button.addEventListener("click", () => {
        state.theme = button.dataset.theme;
        persistReaderState();
        renderControls();
      });
    });

    $("size-down")?.addEventListener("click", () => {
      state.textSize = Math.max(14, state.textSize - 2);
      persistReaderState();
      renderControls();
      updateProgress();
    });

    $("size-up")?.addEventListener("click", () => {
      state.textSize = Math.min(28, state.textSize + 2);
      persistReaderState();
      renderControls();
      updateProgress();
    });
  };

  const wire = () => {
    render();
    wireReaderControls();
    window.addEventListener("scroll", updateProgress, { passive: true });
    window.addEventListener("resize", updateProgress);
  };

  document.addEventListener("DOMContentLoaded", wire, { once: true });
})();
