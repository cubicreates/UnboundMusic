"use client";

import { useMemo, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  Check,
  ChevronDown,
  Copy,
  Menu,
  Search,
  X,
} from "lucide-react";
import { API_CATEGORIES, ApiEndpoint, HttpMethod } from "@/data/apiSpec";

const METHOD_BADGE: Record<HttpMethod, string> = {
  GET: "bg-unbound-primary/15 text-unbound-primary border-unbound-primary/30",
  POST: "bg-unbound-secondary/15 text-unbound-secondary border-unbound-secondary/30",
  DELETE: "bg-unbound-rose/15 text-unbound-rose border-unbound-rose/30",
  SSE: "bg-unbound-violet/15 text-unbound-violet border-unbound-violet/30",
};

export function ApiExplorer() {
  const [activeCategoryId, setActiveCategoryId] = useState(API_CATEGORIES[0].id);
  const [searchQuery, setSearchQuery] = useState("");
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const activeIndex = API_CATEGORIES.findIndex((c) => c.id === activeCategoryId);
  const activeCategory = API_CATEGORIES[activeIndex] ?? API_CATEGORIES[0];
  const prevCategory = activeIndex > 0 ? API_CATEGORIES[activeIndex - 1] : null;
  const nextCategory =
    activeIndex >= 0 && activeIndex < API_CATEGORIES.length - 1
      ? API_CATEGORIES[activeIndex + 1]
      : null;

  const groupedSidebar = useMemo(() => {
    const groups: { name: string; items: typeof API_CATEGORIES }[] = [];
    for (const cat of API_CATEGORIES) {
      let existing = groups.find((g) => g.name === cat.group);
      if (!existing) {
        existing = { name: cat.group, items: [] };
        groups.push(existing);
      }
      existing.items.push(cat);
    }
    return groups;
  }, []);

  const searchResults = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return null;
    const matches: { categoryTitle: string; categoryId: string; endpoint: ApiEndpoint }[] = [];
    for (const cat of API_CATEGORIES) {
      for (const ep of cat.endpoints) {
        if (
          ep.path.toLowerCase().includes(q) ||
          ep.summary.toLowerCase().includes(q) ||
          ep.details.toLowerCase().includes(q) ||
          cat.title.toLowerCase().includes(q)
        ) {
          matches.push({ categoryTitle: cat.title, categoryId: cat.id, endpoint: ep });
        }
      }
    }
    return matches;
  }, [searchQuery]);

  function selectCategory(id: string) {
    setActiveCategoryId(id);
    setSearchQuery("");
    setMobileMenuOpen(false);
    if (typeof window !== "undefined") {
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
  }

  async function copyText(key: string, text: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedKey(key);
      setTimeout(() => setCopiedKey(null), 1500);
    } catch {
      // Clipboard unavailable
    }
  }

  function buildCurl(ep: ApiEndpoint) {
    const url = `http://127.0.0.1:45731${ep.path}`;
    if (ep.method === "SSE") {
      return `curl -N -H "Accept: text/event-stream" "${url}"`;
    }
    if (ep.method === "POST") {
      const payload =
        ep.requestJson && ep.requestJson.startsWith("{")
          ? ` -d '${ep.requestJson.replace(/\n\s*/g, " ")}'`
          : "";
      return `curl -X POST -H "Content-Type: application/json"${payload} "${url}"`;
    }
    return `curl "${url}"`;
  }

  const displayedEndpoints: { categoryTitle?: string; endpoint: ApiEndpoint }[] = searchResults
    ? searchResults.map((r) => ({ categoryTitle: r.categoryTitle, endpoint: r.endpoint }))
    : activeCategory.endpoints.map((ep) => ({ endpoint: ep }));

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8 lg:py-10">
      {/* ==================== MOBILE STICKY DOCS BAR ==================== */}
      <div className="mb-6 flex flex-col gap-3 lg:hidden">
        <button
          type="button"
          onClick={() => setMobileMenuOpen(true)}
          className="flex min-h-[46px] w-full items-center justify-between rounded-xl border border-unbound-outline bg-unbound-surfaceLow px-4 py-2.5 text-left text-sm font-semibold text-unbound-onSurface"
        >
          <span className="flex items-center gap-2.5">
            <Menu className="h-4 w-4 text-unbound-primary" />
            <span>{activeCategory.title}</span>
          </span>
          <ChevronDown className="h-4 w-4 text-unbound-onSurfaceVariant" />
        </button>

        {/* Mobile Quick Search */}
        <div className="relative">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-unbound-onSurfaceVariant" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Quick search endpoints (e.g. /lyrics, /search)..."
            className="min-h-[44px] w-full rounded-xl border border-unbound-outline bg-unbound-surfaceLowest py-2 pl-10 pr-9 text-sm text-unbound-onSurface placeholder:text-unbound-onSurfaceVariant focus:border-unbound-primary focus:outline-none"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => setSearchQuery("")}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-unbound-onSurfaceVariant hover:text-unbound-onSurface"
              aria-label="Clear search"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
      </div>

      {/* Mobile Section Drawer */}
      {mobileMenuOpen && (
        <div
          className="fixed inset-0 z-50 flex items-end bg-black/75 backdrop-blur-sm lg:hidden"
          onClick={() => setMobileMenuOpen(false)}
        >
          <div
            className="max-h-[80vh] w-full overflow-y-auto rounded-t-3xl border-t border-unbound-outline bg-unbound-surfaceLow p-5"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-4 flex items-center justify-between">
              <span className="text-sm font-bold text-unbound-onSurface">API Sections</span>
              <button
                type="button"
                onClick={() => setMobileMenuOpen(false)}
                className="rounded-full bg-unbound-surfaceHigh p-2 text-unbound-onSurface"
                aria-label="Close menu"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="space-y-5">
              {groupedSidebar.map((group) => (
                <div key={group.name}>
                  <p className="mb-1.5 px-2 text-xs font-semibold uppercase tracking-wider text-unbound-onSurfaceVariant">
                    {group.name}
                  </p>
                  <div className="space-y-1">
                    {group.items.map((item) => {
                      const isActive = item.id === activeCategory.id && !searchQuery;
                      return (
                        <button
                          key={item.id}
                          type="button"
                          onClick={() => selectCategory(item.id)}
                          className={`block w-full rounded-xl px-3 py-2.5 text-left text-sm font-medium transition-colors ${
                            isActive
                              ? "bg-unbound-primary/15 font-semibold text-unbound-primary"
                              : "text-unbound-onSurfaceVariant hover:bg-unbound-surfaceHigh hover:text-unbound-onSurface"
                          }`}
                        >
                          {item.title}
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* ==================== DESKTOP TAILWIND DOCS SHELL ==================== */}
      <div className="lg:grid lg:grid-cols-[260px_1fr] lg:gap-12">
        {/* Left Sticky Docs Sidebar */}
        <aside className="hidden lg:block">
          <div className="sticky top-24 max-h-[calc(100vh-7rem)] overflow-y-auto pr-2">
            {/* Sidebar Search */}
            <div className="relative mb-6">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-unbound-onSurfaceVariant" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Quick search..."
                className="w-full rounded-lg border border-unbound-outline bg-unbound-surfaceLowest py-2 pl-8 pr-7 text-xs text-unbound-onSurface placeholder:text-unbound-onSurfaceVariant focus:border-unbound-primary focus:outline-none"
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => setSearchQuery("")}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-unbound-onSurfaceVariant hover:text-unbound-onSurface"
                  aria-label="Clear search"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              )}
            </div>

            <nav className="space-y-6">
              {groupedSidebar.map((group) => (
                <div key={group.name}>
                  <h3 className="mb-2 text-xs font-semibold uppercase tracking-wider text-unbound-onSurface">
                    {group.name}
                  </h3>
                  <ul className="space-y-1 border-l border-unbound-outline">
                    {group.items.map((item) => {
                      const isActive = item.id === activeCategory.id && !searchQuery;
                      return (
                        <li key={item.id}>
                          <button
                            type="button"
                            onClick={() => selectCategory(item.id)}
                            className={`-ml-px block w-full border-l-2 py-1.5 pl-4 text-left text-sm transition-colors ${
                              isActive
                                ? "border-unbound-primary font-semibold text-unbound-primary"
                                : "border-transparent text-unbound-onSurfaceVariant hover:border-unbound-onSurfaceVariant/50 hover:text-unbound-onSurface"
                            }`}
                          >
                            {item.title}
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                </div>
              ))}
            </nav>
          </div>
        </aside>

        {/* Main Docs Content */}
        <div className="min-w-0">
          {searchResults ? (
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-unbound-primary">
                Search Results
              </p>
              <h1 className="mt-1 text-2xl font-extrabold text-unbound-onSurface sm:text-3xl">
                {searchResults.length} {searchResults.length === 1 ? "endpoint" : "endpoints"} matching
                &ldquo;{searchQuery}&rdquo;
              </h1>
            </div>
          ) : (
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-unbound-primary">
                {activeCategory.group} • API Reference
              </p>
              <h1 className="mt-1 text-3xl font-extrabold tracking-tight text-unbound-onSurface sm:text-4xl">
                {activeCategory.title}
              </h1>
              <p className="mt-3 max-w-2xl text-sm leading-relaxed text-unbound-onSurfaceVariant sm:text-base">
                {activeCategory.description}
              </p>

              {/* Simple Base URL Info Box on Getting Started */}
              {activeCategory.id === "getting-started" && (
                <div className="mt-6 grid gap-3 rounded-2xl border border-unbound-outline bg-unbound-surfaceLow p-4 sm:grid-cols-3">
                  <div>
                    <span className="text-[11px] font-medium text-unbound-onSurfaceVariant">
                      Base URL
                    </span>
                    <p className="mt-0.5 font-mono text-xs font-semibold text-unbound-primary">
                      http://127.0.0.1:45731
                    </p>
                  </div>
                  <div>
                    <span className="text-[11px] font-medium text-unbound-onSurfaceVariant">
                      Response Format
                    </span>
                    <p className="mt-0.5 text-xs font-semibold text-unbound-onSurface">
                      Standard JSON (UTF-8)
                    </p>
                  </div>
                  <div>
                    <span className="text-[11px] font-medium text-unbound-onSurfaceVariant">
                      Authentication
                    </span>
                    <p className="mt-0.5 text-xs font-semibold text-unbound-secondary">
                      Local only • No API key needed
                    </p>
                  </div>
                </div>
              )}

              {/* Tailwind-Docs Style Quick Reference Table */}
              <div className="mt-8 overflow-hidden rounded-2xl border border-unbound-outline bg-unbound-surfaceLow">
                <div className="border-b border-unbound-outlineVariant bg-unbound-surface px-4 py-3">
                  <h2 className="text-xs font-bold uppercase tracking-wider text-unbound-onSurface">
                    Quick Reference
                  </h2>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs sm:text-sm">
                    <thead className="border-b border-unbound-outlineVariant text-[11px] uppercase text-unbound-onSurfaceVariant">
                      <tr>
                        <th className="px-4 py-2.5 font-semibold">Method</th>
                        <th className="px-4 py-2.5 font-semibold">Endpoint</th>
                        <th className="hidden px-4 py-2.5 font-semibold sm:table-cell">What it does</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-unbound-outlineVariant/60">
                      {activeCategory.endpoints.map((ep) => (
                        <tr key={ep.id} className="hover:bg-unbound-surfaceHigh/40">
                          <td className="px-4 py-3">
                            <span
                              className={`inline-block rounded border px-2 py-0.5 font-mono text-[11px] font-bold ${METHOD_BADGE[ep.method]}`}
                            >
                              {ep.method}
                            </span>
                          </td>
                          <td className="px-4 py-3 font-mono text-xs font-semibold text-unbound-primary">
                            <a href={`#${ep.id}`} className="hover:underline">
                              {ep.path}
                            </a>
                          </td>
                          <td className="hidden px-4 py-3 text-xs text-unbound-onSurfaceVariant sm:table-cell">
                            {ep.summary}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* ==================== ENDPOINT DETAIL SECTIONS ==================== */}
          <div className="mt-10 space-y-8">
            {displayedEndpoints.length === 0 ? (
              <div className="rounded-2xl border border-unbound-outline bg-unbound-surfaceLow p-8 text-center">
                <p className="text-sm font-semibold text-unbound-onSurface">No matching endpoints</p>
                <button
                  type="button"
                  onClick={() => setSearchQuery("")}
                  className="mt-3 rounded-lg bg-unbound-primary px-4 py-2 text-xs font-bold text-unbound-onPrimary"
                >
                  Reset Search
                </button>
              </div>
            ) : (
              displayedEndpoints.map(({ categoryTitle, endpoint: ep }) => {
                const curlCmd = buildCurl(ep);
                const curlKey = `curl-${ep.id}`;
                const resKey = `res-${ep.id}`;

                return (
                  <article
                    key={ep.id}
                    id={ep.id}
                    className="scroll-mt-24 rounded-2xl border border-unbound-outline bg-unbound-surfaceLow p-5 sm:p-6"
                  >
                    {categoryTitle && (
                      <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-unbound-tertiary">
                        {categoryTitle}
                      </p>
                    )}
                    <div className="flex flex-wrap items-center gap-2.5">
                      <span
                        className={`rounded-md border px-2 py-0.5 font-mono text-xs font-bold ${METHOD_BADGE[ep.method]}`}
                      >
                        {ep.method}
                      </span>
                      <code className="break-all font-mono text-sm font-bold text-unbound-onSurface sm:text-base">
                        {ep.path}
                      </code>
                    </div>

                    <h3 className="mt-2.5 text-base font-bold text-unbound-onSurface">{ep.summary}</h3>
                    <p className="mt-1 text-sm leading-relaxed text-unbound-onSurfaceVariant">
                      {ep.details}
                    </p>

                    {/* Parameters (compact badges/list) */}
                    {ep.params && ep.params.length > 0 && (
                      <div className="mt-4 space-y-2 rounded-xl border border-unbound-outlineVariant bg-unbound-surfaceLowest p-3.5">
                        <p className="text-[11px] font-semibold uppercase tracking-wider text-unbound-onSurfaceVariant">
                          Parameters
                        </p>
                        <div className="space-y-1.5">
                          {ep.params.map((p) => (
                            <div
                              key={p.name}
                              className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5 text-xs"
                            >
                              <code className="font-mono font-semibold text-unbound-primary">
                                {p.name}
                              </code>
                              <span className="font-mono text-[11px] text-unbound-onSurfaceVariant">
                                ({p.location}, {p.type})
                              </span>
                              {p.required && (
                                <span className="text-[10px] font-semibold uppercase text-unbound-amber">
                                  required
                                </span>
                              )}
                              <span className="text-unbound-onSurfaceVariant">— {p.description}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Request Example */}
                    <div className="mt-4">
                      <div className="mb-1.5 flex items-center justify-between">
                        <span className="text-[11px] font-semibold uppercase tracking-wider text-unbound-onSurfaceVariant">
                          Example Request
                        </span>
                        <button
                          type="button"
                          onClick={() => copyText(curlKey, curlCmd)}
                          className="inline-flex items-center gap-1 rounded px-2 py-1 text-xs font-medium text-unbound-primary hover:bg-unbound-surfaceHigh"
                        >
                          {copiedKey === curlKey ? (
                            <Check className="h-3.5 w-3.5" />
                          ) : (
                            <Copy className="h-3.5 w-3.5" />
                          )}
                          {copiedKey === curlKey ? "Copied" : "Copy"}
                        </button>
                      </div>
                      <pre className="overflow-x-auto rounded-xl border border-unbound-outlineVariant bg-unbound-oled p-3.5 font-mono text-xs text-unbound-onPrimaryContainer">
                        {curlCmd}
                      </pre>
                    </div>

                    {/* Response Example */}
                    <div className="mt-4">
                      <div className="mb-1.5 flex items-center justify-between">
                        <span className="text-[11px] font-semibold uppercase tracking-wider text-unbound-onSurfaceVariant">
                          Example Response
                        </span>
                        <button
                          type="button"
                          onClick={() => copyText(resKey, ep.responseJson)}
                          className="inline-flex items-center gap-1 rounded px-2 py-1 text-xs font-medium text-unbound-primary hover:bg-unbound-surfaceHigh"
                        >
                          {copiedKey === resKey ? (
                            <Check className="h-3.5 w-3.5" />
                          ) : (
                            <Copy className="h-3.5 w-3.5" />
                          )}
                          {copiedKey === resKey ? "Copied" : "Copy"}
                        </button>
                      </div>
                      <pre className="overflow-x-auto rounded-xl border border-unbound-outlineVariant bg-unbound-surfaceLowest p-3.5 font-mono text-xs text-unbound-secondary">
                        {ep.responseJson}
                      </pre>
                    </div>
                  </article>
                );
              })
            )}
          </div>

          {/* ==================== PREVIOUS / NEXT DOCS PAGINATION ==================== */}
          {!searchResults && (
            <div className="mt-12 grid gap-4 border-t border-unbound-outline pt-8 sm:grid-cols-2">
              {prevCategory ? (
                <button
                  type="button"
                  onClick={() => selectCategory(prevCategory.id)}
                  className="flex items-center gap-3 rounded-2xl border border-unbound-outline bg-unbound-surfaceLow p-4 text-left transition-colors hover:border-unbound-primary/60"
                >
                  <ArrowLeft className="h-4 w-4 shrink-0 text-unbound-primary" />
                  <div>
                    <p className="text-[11px] font-medium text-unbound-onSurfaceVariant">Previous</p>
                    <p className="text-sm font-bold text-unbound-onSurface">{prevCategory.title}</p>
                  </div>
                </button>
              ) : (
                <div />
              )}

              {nextCategory && (
                <button
                  type="button"
                  onClick={() => selectCategory(nextCategory.id)}
                  className="flex items-center justify-end gap-3 rounded-2xl border border-unbound-outline bg-unbound-surfaceLow p-4 text-right transition-colors hover:border-unbound-primary/60"
                >
                  <div>
                    <p className="text-[11px] font-medium text-unbound-onSurfaceVariant">Next</p>
                    <p className="text-sm font-bold text-unbound-onSurface">{nextCategory.title}</p>
                  </div>
                  <ArrowRight className="h-4 w-4 shrink-0 text-unbound-primary" />
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
