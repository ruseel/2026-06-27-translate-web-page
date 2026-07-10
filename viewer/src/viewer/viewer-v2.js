(() => {
  const STORAGE_KEY = "translation-reader-v2";
  const $ = (id) => document.getElementById(id);
  const html = (value) => String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");

  const safeHref = (value) => {
    const href = String(value ?? "").trim();
    return /^(https?:|mailto:|#)/i.test(href) ? href : "#";
  };

  const inlineHtml = (value) => {
    const tokens = [];
    const token = (markup) => `\u0000${tokens.push(markup) - 1}\u0000`;
    let text = String(value ?? "");

    // Defuddle turns PG-style footnote links into Markdown text like
    // \[[2](#f2n)\]. Render those as compact note references instead of
    // exposing the Markdown escape characters in the reader.
    text = text.replace(/\\?\[\[(\d+)\]\((#[^)\s]+)\)\\?\]/g, (_, label, href) => (
      token(`<sup class="v2-note-ref"><a href="${html(safeHref(href))}">${html(label)}</a></sup>`)
    ));

    text = text.replace(/\[([^\]\n]{1,120})\]\((https?:\/\/[^)\s]+|mailto:[^)\s]+|#[^)\s]+)\)/g, (_, label, href) => (
      token(`<a class="v2-inline-link" href="${html(safeHref(href))}">${html(label)}</a>`)
    ));

    text = text.replace(/\\([\[\]])/g, "$1");

    let out = html(text)
      .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
      .replace(/\u0000(\d+)\u0000/g, (_, i) => tokens[Number(i)] || "");

    return out;
  };

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

  const selectedCandidate = (paragraph) => (paragraph.candidates || [])[0] || null;

  const tagFor = (kind) => ({
    h1: "h2",
    h2: "h3",
    h3: "h4",
    quote: "blockquote",
  })[kind] || "p";

  const noteNumber = (paragraph) => {
    const match = String(paragraph.source ?? "").match(/^\\?\[(\d+)\\?\]\s/);
    return match ? match[1] : null;
  };

  const textBlockHtml = ({ className, lang, kind, text }) => {
    const tag = tagFor(kind);
    const prefix = kind === "li" ? "• " : "";
    return `<${tag} class="v2-text ${className}" lang="${html(lang)}">${prefix}${inlineHtml(text)}</${tag}>`;
  };

  const sourceHtml = (paragraph) => textBlockHtml({
    className: "v2-source",
    lang: "en",
    kind: paragraph.kind,
    text: paragraph.source,
  });

  const translationHtml = (paragraph) => {
    const candidate = selectedCandidate(paragraph);
    if (!candidate) {
      return '<p class="v2-text v2-translation v2-empty">No translation matches this page.</p>';
    }
    return textBlockHtml({
      className: "v2-translation",
      lang: candidate.language || "ko",
      kind: paragraph.kind,
      text: candidate.text,
    });
  };

  const paragraphHtml = (paragraph) => {
    const parts = [];
    const note = noteNumber(paragraph);
    if (state.layout !== "ko-only") parts.push(sourceHtml(paragraph));
    if (state.layout !== "en-only") parts.push(translationHtml(paragraph));
    return `
      <section class="v2-paragraph" id="paragraph-${html(paragraph.position)}" data-layout="${html(state.layout)}">
        ${note ? `<span class="v2-anchor" id="f${html(note)}n"></span>` : ""}
        ${parts.join("")}
      </section>`;
  };

  const renderReader = () => {
    const el = $("paragraphs");
    if (!el) return;
    const paragraphs = state.view?.paragraphs || [];
    el.innerHTML = paragraphs.length
      ? paragraphs.map(paragraphHtml).join("")
      : '<p class="v2-empty">No page paragraphs found.</p>';
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
