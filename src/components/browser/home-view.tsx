import { CloudSun, Mic, Plus, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import { BlazeWordmark } from "./logo";
import type { NewsCategory, NewsItem, WeatherInfo } from "@/lib/browser/types";
import { useBrowserStore } from "@/lib/browser/store";
import { faviconFor } from "@/lib/browser/url";
import { cn } from "@/lib/utils";

const CATS: { id: NewsCategory; label: string }[] = [
  { id: "foryou", label: "For you" },
  { id: "world", label: "World" },
  { id: "popular", label: "Popular" },
  { id: "today", label: "Today" },
];

export function HomeView({
  news,
  weather,
  weatherCity,
  onSearch,
  onOpen,
  onListen,
}: {
  news: NewsItem[];
  weather: WeatherInfo | null;
  weatherCity: string;
  onSearch: (q: string) => void;
  onOpen: (url: string, title?: string) => void;
  onListen: () => void;
}) {
  const shortcuts = useBrowserStore((s) => s.shortcuts);
  const setPanel = useBrowserStore((s) => s.setPanel);
  const removeShortcut = useBrowserStore((s) => s.removeShortcut);
  const setScreen = useBrowserStore((s) => s.setScreen);
  const [cat, setCat] = useState<NewsCategory>("foryou");
  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState(false);

  const feed = useMemo(() => {
    const filtered = news.filter((n) => n.category === cat);
    return (filtered.length ? filtered : news).slice(0, 12);
  }, [news, cat]);

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-y-auto">
      <header className="px-4 pb-2 pt-1">
        <div className="mb-4 flex items-center justify-between">
          <BlazeWordmark />
          <span className="rounded-full bg-primary-soft px-3 py-1 text-xs font-semibold text-primary-ink">
            Speed mode
          </span>
        </div>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (query.trim()) onSearch(query);
          }}
        >
          <label className="flex min-h-12 items-center rounded-2xl bg-surface px-1 shadow-card">
            <span className="sr-only">Search the web</span>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search or enter site"
              className="min-w-0 flex-1 bg-transparent px-4 py-3 text-base text-fg outline-none placeholder:text-subtle"
              autoCapitalize="none"
              autoCorrect="off"
              suppressHydrationWarning
            />
            <button
              type="button"
              onClick={onListen}
              className="press grid size-11 place-items-center text-primary"
              aria-label="Voice search"
            >
              <Mic className="size-5" />
            </button>
          </label>
        </form>
      </header>

      {weather ? (
        <button
          type="button"
          onClick={() => setPanel("settings")}
          className="press mx-4 mt-3 flex items-center gap-3 rounded-2xl bg-surface px-4 py-3 text-left shadow-card"
        >
          <span className="grid size-10 place-items-center rounded-xl bg-primary-soft text-primary">
            <CloudSun className="size-5" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-semibold tabular-nums">
              {weather.temp}° · {weather.label}
            </span>
            <span className="block text-xs text-muted">{weather.city}</span>
          </span>
          <span className="text-xs font-medium text-primary">Change</span>
        </button>
      ) : (
        <p className="mx-4 mt-3 text-sm text-muted">{weatherCity}</p>
      )}

      <section className="px-4 pt-5">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold">Shortcuts</h2>
          <button
            type="button"
            className="text-xs font-medium text-primary"
            onClick={() => setEditing((v) => !v)}
          >
            {editing ? "Done" : "Edit"}
          </button>
        </div>
        <div className="grid grid-cols-4 gap-x-2 gap-y-4">
          {shortcuts.map((s) => (
            <div key={s.id} className="relative flex flex-col items-center gap-1.5">
              <button
                type="button"
                onClick={() => {
                  if (editing) {
                    removeShortcut(s.id);
                    return;
                  }
                  if (/youtube\.com|youtu\.be/i.test(s.url)) {
                    setScreen("videos");
                    return;
                  }
                  onOpen(s.url, s.title);
                }}
                className="press flex w-full flex-col items-center gap-1.5"
              >
                <span className="grid size-12 place-items-center rounded-2xl bg-surface shadow-card">
                  <img
                    src={faviconFor(s.url, 64)}
                    alt=""
                    className="size-6 rounded-md"
                    onError={(e) => {
                      e.currentTarget.style.display = "none";
                    }}
                  />
                </span>
                <span className="w-full truncate text-center text-xs font-medium text-muted">
                  {s.title}
                </span>
              </button>
              {editing ? (
                <span className="pointer-events-none absolute -right-0.5 -top-0.5 grid size-5 place-items-center rounded-full bg-danger text-primary-fg">
                  <Trash2 className="size-3" />
                </span>
              ) : null}
            </div>
          ))}
          <button
            type="button"
            onClick={() => setPanel("shortcuts")}
            className="press flex flex-col items-center gap-1.5"
          >
            <span className="grid size-12 place-items-center rounded-2xl border border-dashed border-border bg-surface text-muted">
              <Plus className="size-5" />
            </span>
            <span className="text-xs font-medium text-muted">Add</span>
          </button>
        </div>
      </section>

      <section className="px-4 pb-6 pt-6">
        <div className="mb-3 flex gap-1 overflow-x-auto">
          {CATS.map((c) => (
            <button
              key={c.id}
              type="button"
              onClick={() => setCat(c.id)}
              className={cn(
                "press shrink-0 rounded-full px-3.5 py-1.5 text-sm font-medium",
                cat === c.id ? "bg-primary text-primary-fg" : "bg-surface text-muted shadow-card",
              )}
            >
              {c.label}
            </button>
          ))}
        </div>
        <div className="stagger-in flex flex-col gap-3">
          {feed.map((item, i) => (
            <NewsCard key={item.id} item={item} featured={i === 0} onOpen={onOpen} />
          ))}
        </div>
      </section>
    </div>
  );
}

function NewsCard({
  item,
  featured,
  onOpen,
}: {
  item: NewsItem;
  featured: boolean;
  onOpen: (url: string, title?: string) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onOpen(item.url, item.title)}
      className="press overflow-hidden rounded-2xl bg-surface text-left shadow-card"
    >
      {featured && item.image ? (
        <span className="relative block aspect-video overflow-hidden">
          <img
            src={item.image}
            alt=""
            className="size-full object-cover outline outline-1 -outline-offset-1 outline-fg/10"
          />
          <span className="absolute inset-x-0 bottom-0 bg-linear-to-t from-fg/80 to-transparent p-3 pt-10 text-sm font-semibold text-primary-fg">
            {item.title}
          </span>
        </span>
      ) : (
        <span className="flex gap-3 p-3">
          <span className="min-w-0 flex-1">
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-primary">
              {item.source}
            </span>
            <span className="block text-sm font-semibold leading-snug">{item.title}</span>
            {item.snippet ? (
              <span className="mt-1 line-clamp-2 block text-xs leading-relaxed text-muted">
                {item.snippet}
              </span>
            ) : null}
          </span>
          {item.image ? (
            <img
              src={item.image}
              alt=""
              className="size-20 shrink-0 rounded-xl object-cover outline outline-1 -outline-offset-1 outline-fg/10"
            />
          ) : (
            <span className="grid size-20 shrink-0 place-items-center rounded-xl bg-primary-soft text-lg font-semibold text-primary">
              {item.title.slice(0, 1)}
            </span>
          )}
        </span>
      )}
    </button>
  );
}
