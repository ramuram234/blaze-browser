import {
  Bookmark,
  Clock3,
  EyeOff,
  Globe,
  History,
  Moon,
  Plus,
  Play,
  Settings,
  Share2,
  Shield,
  Sun,
  Trash2,
  Type,
  X,
  Zap,
} from "lucide-react";
import { useState, type ReactNode } from "react";
import { FEATURED_VIDEOS, VIDEO_CATEGORIES } from "@/lib/browser/videos";
import { clipThumb } from "@/lib/browser/media";
import { useActiveTab, useBrowserStore } from "@/lib/browser/store";
import { CITIES, CITY_KEYS } from "@/lib/browser/cities";
import { faviconFor, hostOf, parseOmnibox } from "@/lib/browser/url";
import { cn } from "@/lib/utils";
import type { OverlayPanel, VideoClip } from "@/lib/browser/types";

export function TabsView() {
  const tabs = useBrowserStore((s) => s.tabs);
  const activeTabId = useBrowserStore((s) => s.activeTabId);
  const setActiveTab = useBrowserStore((s) => s.setActiveTab);
  const closeTab = useBrowserStore((s) => s.closeTab);
  const createTab = useBrowserStore((s) => s.createTab);

  return (
    <div className="flex min-h-0 flex-1 flex-col bg-bg">
      <header className="flex items-center justify-between px-4 py-3">
        <h1 className="text-lg font-semibold">Windows</h1>
        <span className="text-sm text-muted tabular-nums">{tabs.length}</span>
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-4">
        <div className="grid grid-cols-2 gap-3">
          {tabs.map((tab) => (
            <div
              key={tab.id}
              className={cn(
                "relative overflow-hidden rounded-2xl bg-surface p-3 text-left shadow-card",
                tab.id === activeTabId && "ring-2 ring-primary",
                tab.isPrivate && "bg-elevated",
              )}
            >
              <button
                type="button"
                onClick={() => setActiveTab(tab.id)}
                className="press w-full text-left"
              >
                <span className="mb-6 flex items-center gap-2 pr-8">
                  {tab.url ? (
                    <img src={faviconFor(tab.url)} alt="" className="size-4 rounded-sm" />
                  ) : (
                    <Globe className="size-4 text-primary" />
                  )}
                  <span className="truncate text-xs font-medium">{tab.title}</span>
                </span>
                <span className="block truncate text-xs text-muted">
                  {tab.isPrivate ? "Private" : tab.url ? hostOf(tab.url) : "Home"}
                </span>
              </button>
              <button
                type="button"
                onClick={() => closeTab(tab.id)}
                className="absolute right-1.5 top-1.5 grid size-8 place-items-center rounded-full text-muted"
                aria-label="Close tab"
              >
                <X className="size-4" />
              </button>
            </div>
          ))}
        </div>
      </div>
      <div className="flex gap-2 px-4 pb-3">
        <button
          type="button"
          onClick={() => createTab()}
          className="press flex min-h-12 flex-1 items-center justify-center gap-2 rounded-2xl bg-primary font-semibold text-primary-fg"
        >
          <Plus className="size-4" />
          New tab
        </button>
        <button
          type="button"
          onClick={() => createTab({ private: true })}
          className="press flex min-h-12 flex-1 items-center justify-center gap-2 rounded-2xl bg-surface font-semibold shadow-card"
        >
          <EyeOff className="size-4" />
          Private
        </button>
      </div>
    </div>
  );
}

export function VideosView({
  results,
  loading,
  onSearch,
  onPlay,
}: {
  results: VideoClip[];
  loading: boolean;
  onSearch: (q: string) => void;
  onPlay: (queue: VideoClip[], index: number) => void;
}) {
  const [cat, setCat] = useState("All");
  const [q, setQ] = useState("");
  const list = results.length
    ? results
    : FEATURED_VIDEOS.filter((v) => cat === "All" || v.category === cat);

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <header className="px-4 pb-2 pt-3">
        <h1 className="text-lg font-semibold">Videos</h1>
        <form
          className="mt-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (q.trim()) onSearch(q);
          }}
        >
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search videos"
            className="min-h-11 w-full rounded-xl bg-surface px-4 text-sm shadow-card outline-none placeholder:text-subtle"
          />
        </form>
        <div className="mt-3 flex gap-1 overflow-x-auto">
          {VIDEO_CATEGORIES.map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => setCat(c)}
              className={cn(
                "press shrink-0 rounded-full px-3 py-1.5 text-xs font-semibold",
                cat === c ? "bg-primary text-primary-fg" : "bg-surface text-muted shadow-card",
              )}
            >
              {c}
            </button>
          ))}
        </div>
      </header>
      {list[0] ? (
        <p className="px-4 pb-1 text-xs text-muted">
          Tap a clip for the swipe player — seek, volume, brightness, preload.
        </p>
      ) : null}
      <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-4">
        {loading ? <p className="py-6 text-sm text-muted">Looking up videos…</p> : null}
        <div className="grid grid-cols-1 gap-3">
          {list.map((clip, i) => (
            <button
              key={clip.id}
              type="button"
              onClick={() => onPlay(list, i)}
              className="press flex gap-3 overflow-hidden rounded-2xl bg-surface p-2 text-left shadow-card"
            >
              {clipThumb(clip) ? (
                <img
                  src={clipThumb(clip)}
                  alt=""
                  className="h-20 w-32 shrink-0 rounded-xl object-cover outline outline-1 -outline-offset-1 outline-fg/10"
                />
              ) : (
                <span className="grid h-20 w-32 shrink-0 place-items-center rounded-xl bg-primary-soft text-primary">
                  <Play className="size-6 translate-x-0.5" />
                </span>
              )}
              <span className="min-w-0 py-1">
                <span className="block text-sm font-semibold leading-snug">{clip.title}</span>
                <span className="mt-1 block text-xs text-muted">
                  {clip.channel} · {clip.category}
                </span>
              </span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

export function DownloadsView() {
  const downloads = useBrowserStore((s) => s.downloads);
  const clearDownloads = useBrowserStore((s) => s.clearDownloads);

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <header className="flex items-center justify-between px-4 py-3">
        <h1 className="text-lg font-semibold">Downloads</h1>
        {downloads.length ? (
          <button type="button" onClick={clearDownloads} className="text-sm font-medium text-primary">
            Clear
          </button>
        ) : null}
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-4">
        {downloads.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
            <p className="font-semibold">No files yet</p>
            <p className="max-w-xs text-sm text-muted">
              Save a turbo page or open a file from the page menu and it will land here.
            </p>
          </div>
        ) : (
          <ul className="flex flex-col gap-2">
            {downloads.map((d) => (
              <li key={d.id}>
                <a
                  href={d.url}
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-3 rounded-2xl bg-surface px-3 py-3 shadow-card"
                >
                  <span className="grid size-10 place-items-center rounded-xl bg-primary-soft text-xs font-bold text-primary">
                    {d.name.slice(0, 1).toUpperCase()}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold">{d.name}</span>
                    <span className="block truncate text-xs text-muted">{d.source}</span>
                  </span>
                </a>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

export function MenuSheet({
  onNavigate,
}: {
  onNavigate: (url: string, title?: string) => void;
}) {
  const tab = useActiveTab();
  const setPanel = useBrowserStore((s) => s.setPanel);
  const setTheme = useBrowserStore((s) => s.setTheme);
  const theme = useBrowserStore((s) => s.theme);
  const turbo = useBrowserStore((s) => s.turbo);
  const setTurbo = useBrowserStore((s) => s.setTurbo);
  const createTab = useBrowserStore((s) => s.createTab);
  const toggleBookmark = useBrowserStore((s) => s.toggleBookmark);
  const showToast = useBrowserStore((s) => s.showToast);
  const dark = theme === "dark";

  const items: { label: string; icon: typeof Bookmark; onClick: () => void }[] = [
    {
      label: "Bookmarks",
      icon: Bookmark,
      onClick: () => setPanel("bookmarks"),
    },
    {
      label: "History",
      icon: History,
      onClick: () => setPanel("history"),
    },
    {
      label: dark ? "Day mode" : "Night mode",
      icon: dark ? Sun : Moon,
      onClick: () => setTheme(dark ? "light" : "dark"),
    },
    {
      label: "Private tab",
      icon: EyeOff,
      onClick: () => createTab({ private: true }),
    },
    {
      label: tab.url ? "Bookmark" : "Home",
      icon: Bookmark,
      onClick: () => {
        if (!tab.url) return;
        toggleBookmark(tab.title, tab.url);
        showToast("Saved to bookmarks");
        setPanel("none");
      },
    },
    {
      label: turbo ? "Turbo on" : "Turbo off",
      icon: Zap,
      onClick: () => setTurbo(!turbo),
    },
    {
      label: "Share link",
      icon: Share2,
      onClick: async () => {
        const text = tab.url || "https://blaze.browser";
        try {
          await navigator.clipboard.writeText(text);
          showToast("Link copied");
        } catch {
          showToast(text);
        }
        setPanel("none");
      },
    },
    {
      label: "Find in page",
      icon: Type,
      onClick: () => setPanel("find"),
    },
    {
      label: "Settings",
      icon: Settings,
      onClick: () => setPanel("settings"),
    },
  ];

  return (
    <div className="absolute inset-0 z-30 flex flex-col justify-end bg-fg/40" onClick={() => setPanel("none")}>
      <div
        className="sheet-in rounded-t-3xl bg-surface px-4 pb-[max(1rem,env(safe-area-inset-bottom))] pt-3"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-border" />
        <div className="grid grid-cols-3 gap-2">
          {items.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.label}
                type="button"
                onClick={item.onClick}
                className="press flex min-h-20 flex-col items-center justify-center gap-2 rounded-2xl bg-bg px-2 text-center"
              >
                <Icon className="size-5 text-primary" />
                <span className="text-xs font-medium leading-tight">{item.label}</span>
              </button>
            );
          })}
        </div>
        {tab.url ? (
          <button
            type="button"
            className="press mt-3 min-h-11 w-full rounded-2xl bg-bg text-sm font-medium text-muted"
            onClick={() => {
              onNavigate(tab.url, tab.title);
              setPanel("none");
            }}
          >
            Reload page
          </button>
        ) : null}
      </div>
    </div>
  );
}

export function ListPanel({
  kind,
}: {
  kind: Extract<OverlayPanel, "bookmarks" | "history" | "settings" | "shortcuts" | "find">;
}) {
  const setPanel = useBrowserStore((s) => s.setPanel);
  const title =
    kind === "bookmarks"
      ? "Bookmarks"
      : kind === "history"
        ? "History"
        : kind === "settings"
          ? "Settings"
          : kind === "shortcuts"
            ? "Add shortcut"
            : "Find in page";

  return (
    <div className="absolute inset-0 z-30 flex flex-col bg-bg">
      <header className="flex items-center gap-1 px-2 pt-2">
        <button
          type="button"
          onClick={() => setPanel("none")}
          className="press grid size-11 place-items-center"
          aria-label="Close"
        >
          <X className="size-5" />
        </button>
        <h1 className="text-lg font-semibold">{title}</h1>
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
        {kind === "bookmarks" ? <BookmarksList /> : null}
        {kind === "history" ? <HistoryList /> : null}
        {kind === "settings" ? <SettingsList /> : null}
        {kind === "shortcuts" ? <AddShortcut /> : null}
        {kind === "find" ? <FindBox /> : null}
      </div>
    </div>
  );
}

function BookmarksList() {
  const bookmarks = useBrowserStore((s) => s.bookmarks);
  const removeBookmark = useBrowserStore((s) => s.removeBookmark);
  const updateTab = useBrowserStore((s) => s.updateTab);
  const activeTabId = useBrowserStore((s) => s.activeTabId);
  const addHistory = useBrowserStore((s) => s.addHistory);
  const setPanel = useBrowserStore((s) => s.setPanel);
  const setScreen = useBrowserStore((s) => s.setScreen);

  if (!bookmarks.length) {
    return <Empty label="No bookmarks yet" hint="Star a page from the address bar." />;
  }
  return (
    <ul className="flex flex-col gap-2">
      {bookmarks.map((b) => (
        <li key={b.id} className="flex items-center gap-1 rounded-2xl bg-surface shadow-card">
          <button
            type="button"
            className="flex min-w-0 flex-1 items-center gap-3 px-3 py-3 text-left"
            onClick={() => {
              updateTab(activeTabId, { kind: "page", url: b.url, title: b.title, query: "" });
              addHistory(b.title, b.url);
              setScreen("home");
              setPanel("none");
            }}
          >
            <img src={faviconFor(b.url)} alt="" className="size-5 rounded-sm" />
            <span className="min-w-0">
              <span className="block truncate text-sm font-semibold">{b.title}</span>
              <span className="block truncate text-xs text-muted">{hostOf(b.url)}</span>
            </span>
          </button>
          <button
            type="button"
            className="grid size-11 place-items-center text-muted"
            onClick={() => removeBookmark(b.id)}
            aria-label="Remove"
          >
            <Trash2 className="size-4" />
          </button>
        </li>
      ))}
    </ul>
  );
}

function HistoryList() {
  const history = useBrowserStore((s) => s.history);
  const clearHistory = useBrowserStore((s) => s.clearHistory);
  const updateTab = useBrowserStore((s) => s.updateTab);
  const activeTabId = useBrowserStore((s) => s.activeTabId);
  const setPanel = useBrowserStore((s) => s.setPanel);
  const setScreen = useBrowserStore((s) => s.setScreen);

  if (!history.length) return <Empty label="History is empty" hint="Pages you visit will show up here." />;
  return (
    <div>
      <button type="button" onClick={clearHistory} className="mb-3 text-sm font-medium text-primary">
        Clear all
      </button>
      <ul className="flex flex-col gap-2">
        {history.map((h) => (
          <li key={h.id}>
            <button
              type="button"
              className="flex w-full items-center gap-3 rounded-2xl bg-surface px-3 py-3 text-left shadow-card"
              onClick={() => {
                updateTab(activeTabId, { kind: "page", url: h.url, title: h.title, query: "" });
                setScreen("home");
                setPanel("none");
              }}
            >
              <Clock3 className="size-4 shrink-0 text-muted" />
              <span className="min-w-0">
                <span className="block truncate text-sm font-semibold">{h.title}</span>
                <span className="block truncate text-xs text-muted">{hostOf(h.url)}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

function SettingsList() {
  const theme = useBrowserStore((s) => s.theme);
  const setTheme = useBrowserStore((s) => s.setTheme);
  const turbo = useBrowserStore((s) => s.turbo);
  const setTurbo = useBrowserStore((s) => s.setTurbo);
  const adBlock = useBrowserStore((s) => s.adBlock);
  const setAdBlock = useBrowserStore((s) => s.setAdBlock);
  const fontScale = useBrowserStore((s) => s.fontScale);
  const setFontScale = useBrowserStore((s) => s.setFontScale);
  const city = useBrowserStore((s) => s.city);
  const setCity = useBrowserStore((s) => s.setCity);

  return (
    <div className="flex flex-col gap-3">
      <Row label="Appearance">
        {(["light", "dark", "system"] as const).map((mode) => (
          <Chip key={mode} active={theme === mode} onClick={() => setTheme(mode)}>
            {mode}
          </Chip>
        ))}
      </Row>
      <Row label="Turbo pages">
        <Toggle on={turbo} onChange={setTurbo} />
      </Row>
      <Row label="Block ads">
        <Toggle on={adBlock} onChange={setAdBlock} />
      </Row>
      <Row label="Text size">
        {(["sm", "md", "lg"] as const).map((s) => (
          <Chip key={s} active={fontScale === s} onClick={() => setFontScale(s)}>
            {s === "sm" ? "S" : s === "md" ? "M" : "L"}
          </Chip>
        ))}
      </Row>
      <Row label="Weather city">
        <select
          value={city}
          onChange={(e) => setCity(e.target.value)}
          className="min-h-10 rounded-xl bg-bg px-3 text-sm outline-none"
        >
          {CITY_KEYS.map((key) => (
            <option key={key} value={key}>
              {CITIES[key]?.label ?? key}
            </option>
          ))}
        </select>
      </Row>
      <p className="flex items-start gap-2 pt-2 text-xs leading-relaxed text-muted">
        <Shield className="mt-0.5 size-4 shrink-0" />
        Turbo mode loads a simplified page so news, articles, and docs stay fast even on a slow
        connection. Full site uses a proxy when the original page blocks embedding.
      </p>
    </div>
  );
}

function AddShortcut() {
  const addShortcut = useBrowserStore((s) => s.addShortcut);
  const setPanel = useBrowserStore((s) => s.setPanel);
  const showToast = useBrowserStore((s) => s.showToast);
  const [title, setTitle] = useState("");
  const [url, setUrl] = useState("");

  return (
    <form
      className="flex flex-col gap-3"
      onSubmit={(e) => {
        e.preventDefault();
        const parsed = parseOmnibox(url);
        if (parsed.type !== "url") {
          showToast("Enter a full site address");
          return;
        }
        addShortcut(title || hostOf(parsed.value), parsed.value);
        setPanel("none");
      }}
    >
      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="Name"
        className="min-h-11 rounded-xl bg-surface px-3 text-sm shadow-card outline-none"
      />
      <input
        value={url}
        onChange={(e) => setUrl(e.target.value)}
        placeholder="https://example.com"
        className="min-h-11 rounded-xl bg-surface px-3 text-sm shadow-card outline-none"
      />
      <button type="submit" className="press min-h-11 rounded-2xl bg-primary font-semibold text-primary-fg">
        Add shortcut
      </button>
    </form>
  );
}

function FindBox() {
  const findQuery = useBrowserStore((s) => s.findQuery);
  const setFindQuery = useBrowserStore((s) => s.setFindQuery);
  const setPanel = useBrowserStore((s) => s.setPanel);
  return (
    <form
      className="flex flex-col gap-3"
      onSubmit={(e) => {
        e.preventDefault();
        setPanel("none");
      }}
    >
      <input
        value={findQuery}
        onChange={(e) => setFindQuery(e.target.value)}
        placeholder="Find in this page"
        className="min-h-11 rounded-xl bg-surface px-3 text-sm shadow-card outline-none"
        autoFocus
      />
      <button type="submit" className="press min-h-11 rounded-2xl bg-primary font-semibold text-primary-fg">
        Highlight matches
      </button>
    </form>
  );
}

function Empty({ label, hint }: { label: string; hint: string }) {
  return (
    <div className="py-16 text-center">
      <p className="font-semibold">{label}</p>
      <p className="mt-1 text-sm text-muted">{hint}</p>
    </div>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-2xl bg-surface px-4 py-3 shadow-card">
      <span className="text-sm font-medium">{label}</span>
      <span className="flex flex-wrap items-center justify-end gap-1.5">{children}</span>
    </div>
  );
}

function Chip({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "press min-h-8 rounded-full px-3 text-xs font-semibold capitalize",
        active ? "bg-primary text-primary-fg" : "bg-bg text-muted",
      )}
    >
      {children}
    </button>
  );
}

function Toggle({ on, onChange }: { on: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      onClick={() => onChange(!on)}
      className={cn(
        "relative h-7 w-12 rounded-full transition-colors duration-150",
        on ? "bg-primary" : "bg-border",
      )}
    >
      <span
        className={cn(
          "absolute top-0.5 size-6 rounded-full bg-primary-fg transition-transform duration-150",
          on ? "translate-x-5" : "translate-x-0.5",
        )}
      />
    </button>
  );
}
