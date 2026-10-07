import { create } from "zustand";
import { persist } from "zustand/middleware";
import type {
  AppScreen,
  Bookmark,
  DownloadItem,
  FontScale,
  HistoryItem,
  OverlayPanel,
  Shortcut,
  Tab,
  ThemeMode,
  VideoClip,
} from "./types";
import { hostOf, uid } from "./url";

const DEFAULT_SHORTCUTS: Shortcut[] = [
  { id: "wiki", title: "Wikipedia", url: "https://en.wikipedia.org/wiki/Main_Page" },
  { id: "yt", title: "YouTube", url: "https://www.youtube.com" },
  { id: "reddit", title: "Reddit", url: "https://www.reddit.com" },
  { id: "bbc", title: "BBC", url: "https://www.bbc.com/news" },
  { id: "hn", title: "Hacker News", url: "https://news.ycombinator.com" },
  { id: "mdn", title: "MDN", url: "https://developer.mozilla.org" },
  { id: "github", title: "GitHub", url: "https://github.com" },
  { id: "archive", title: "Archive", url: "https://archive.org" },
];

function blankTab(partial?: Partial<Tab>): Tab {
  return {
    id: uid(),
    kind: "home",
    title: "New tab",
    url: "",
    query: "",
    isPrivate: false,
    createdAt: Date.now(),
    ...partial,
  };
}

interface BrowserState {
  tabs: Tab[];
  activeTabId: string;
  bookmarks: Bookmark[];
  history: HistoryItem[];
  downloads: DownloadItem[];
  shortcuts: Shortcut[];
  theme: ThemeMode;
  turbo: boolean;
  adBlock: boolean;
  fontScale: FontScale;
  city: string;
  screen: AppScreen;
  panel: OverlayPanel;
  toast: string | null;
  findQuery: string;
  playerQueue: VideoClip[];
  playerIndex: number;
  createTab: (opts?: { private?: boolean; activate?: boolean }) => string;
  closeTab: (id: string) => void;
  setActiveTab: (id: string) => void;
  updateTab: (id: string, patch: Partial<Tab>) => void;
  goHome: () => void;
  addBookmark: (title: string, url: string) => void;
  removeBookmark: (id: string) => void;
  toggleBookmark: (title: string, url: string) => void;
  addHistory: (title: string, url: string) => void;
  clearHistory: () => void;
  addDownload: (item: Omit<DownloadItem, "id" | "createdAt">) => void;
  clearDownloads: () => void;
  addShortcut: (title: string, url: string) => void;
  removeShortcut: (id: string) => void;
  setTheme: (theme: ThemeMode) => void;
  setTurbo: (turbo: boolean) => void;
  setAdBlock: (adBlock: boolean) => void;
  setFontScale: (fontScale: FontScale) => void;
  setCity: (city: string) => void;
  setScreen: (screen: AppScreen) => void;
  setPanel: (panel: OverlayPanel) => void;
  setFindQuery: (findQuery: string) => void;
  openPlayer: (queue: VideoClip[], index?: number) => void;
  closePlayer: () => void;
  setPlayerIndex: (index: number) => void;
  showToast: (toast: string | null) => void;
}

function applyTheme(theme: ThemeMode) {
  if (typeof document === "undefined") return;
  const dark =
    theme === "dark" ||
    (theme === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.classList.toggle("dark", dark);
}

const first = blankTab();

export const useBrowserStore = create<BrowserState>()(
  persist(
    (set, get) => ({
      tabs: [first],
      activeTabId: first.id,
      bookmarks: [],
      history: [],
      downloads: [],
      shortcuts: DEFAULT_SHORTCUTS,
      theme: "light",
      turbo: true,
      adBlock: true,
      fontScale: "md",
      city: "delhi",
      screen: "home",
      panel: "none",
      toast: null,
      findQuery: "",
      playerQueue: [],
      playerIndex: 0,
      createTab: (opts) => {
        const tab = blankTab({ isPrivate: Boolean(opts?.private) });
        set((s) => ({
          tabs: [...s.tabs, tab],
          activeTabId: opts?.activate === false ? s.activeTabId : tab.id,
          screen: "home",
          panel: "none",
        }));
        return tab.id;
      },
      closeTab: (id) => {
        const { tabs, activeTabId } = get();
        const remaining = tabs.filter((t) => t.id !== id);
        const nextTabs = remaining.length ? remaining : [blankTab()];
        const nextActive =
          id === activeTabId ? nextTabs[nextTabs.length - 1]!.id : activeTabId;
        set({ tabs: nextTabs, activeTabId: nextActive });
      },
      setActiveTab: (id) => set({ activeTabId: id, screen: "home", panel: "none" }),
      updateTab: (id, patch) =>
        set((s) => ({
          tabs: s.tabs.map((t) => (t.id === id ? { ...t, ...patch } : t)),
        })),
      goHome: () => {
        const { activeTabId } = get();
        set((s) => ({
          screen: "home",
          panel: "none",
          playerQueue: [],
          playerIndex: 0,
          tabs: s.tabs.map((t) =>
            t.id === activeTabId
              ? { ...t, kind: "home", title: t.isPrivate ? "Private tab" : "New tab", url: "", query: "" }
              : t,
          ),
        }));
      },
      addBookmark: (title, url) =>
        set((s) => {
          if (s.bookmarks.some((b) => b.url === url)) return s;
          return {
            bookmarks: [
              { id: uid(), title: title || hostOf(url), url, createdAt: Date.now() },
              ...s.bookmarks,
            ].slice(0, 80),
          };
        }),
      removeBookmark: (id) =>
        set((s) => ({ bookmarks: s.bookmarks.filter((b) => b.id !== id) })),
      toggleBookmark: (title, url) => {
        const existing = get().bookmarks.find((b) => b.url === url);
        if (existing) get().removeBookmark(existing.id);
        else get().addBookmark(title, url);
      },
      addHistory: (title, url) => {
        if (get().tabs.find((t) => t.id === get().activeTabId)?.isPrivate) return;
        set((s) => ({
          history: [
            { id: uid(), title: title || hostOf(url), url, visitedAt: Date.now() },
            ...s.history.filter((h) => h.url !== url),
          ].slice(0, 120),
        }));
      },
      clearHistory: () => set({ history: [] }),
      addDownload: (item) =>
        set((s) => ({
          downloads: [{ ...item, id: uid(), createdAt: Date.now() }, ...s.downloads].slice(0, 40),
        })),
      clearDownloads: () => set({ downloads: [] }),
      addShortcut: (title, url) =>
        set((s) => ({
          shortcuts: [...s.shortcuts, { id: uid(), title, url }].slice(0, 16),
        })),
      removeShortcut: (id) =>
        set((s) => ({ shortcuts: s.shortcuts.filter((x) => x.id !== id) })),
      setTheme: (theme) => {
        applyTheme(theme);
        set({ theme });
      },
      setTurbo: (turbo) => set({ turbo }),
      setAdBlock: (adBlock) => set({ adBlock }),
      setFontScale: (fontScale) => set({ fontScale }),
      setCity: (city) => set({ city }),
      setScreen: (screen) => set({ screen, panel: "none" }),
      setPanel: (panel) => set({ panel }),
      setFindQuery: (findQuery) => set({ findQuery }),
      openPlayer: (queue, index = 0) =>
        set({
          playerQueue: queue,
          playerIndex: Math.max(0, Math.min(index, queue.length - 1)),
          panel: "none",
        }),
      closePlayer: () => set({ playerQueue: [], playerIndex: 0 }),
      setPlayerIndex: (index) =>
        set((s) => ({
          playerIndex: Math.max(0, Math.min(index, s.playerQueue.length - 1)),
        })),
      showToast: (toast) => {
        set({ toast });
        if (toast) window.setTimeout(() => {
          if (get().toast === toast) set({ toast: null });
        }, 1800);
      },
    }),
    {
      name: "blaze-browser",
      partialize: (s) => ({
        tabs: s.tabs.filter((t) => !t.isPrivate).slice(0, 8),
        activeTabId: s.activeTabId,
        bookmarks: s.bookmarks,
        history: s.history,
        downloads: s.downloads,
        shortcuts: s.shortcuts,
        theme: s.theme,
        turbo: s.turbo,
        adBlock: s.adBlock,
        fontScale: s.fontScale,
        city: s.city,
      }),
      onRehydrateStorage: () => (state) => {
        if (state) applyTheme(state.theme);
      },
    },
  ),
);

export function useActiveTab(): Tab {
  return useBrowserStore((s) => s.tabs.find((t) => t.id === s.activeTabId) ?? s.tabs[0]!);
}

export { DEFAULT_SHORTCUTS, applyTheme };
