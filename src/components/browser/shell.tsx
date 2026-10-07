import { useCallback, useEffect, useState, type ReactNode } from "react";
import { AddressBar, BottomNav, StatusTick, ToastHost } from "./chrome";
import { HomeView } from "./home-view";
import { LiveFrame, ReaderView, SearchResults } from "./page-view";
import { DownloadsView, ListPanel, MenuSheet, TabsView, VideosView } from "./overlays";
import { GesturePlayer } from "./player";
import { fetchPage, fetchWeather, searchVideos, searchWeb } from "@/lib/browser/fetchers";
import { youtubeIdFromUrl } from "@/lib/browser/media";
import { FEATURED_VIDEOS } from "@/lib/browser/videos";
import { useActiveTab, useBrowserStore } from "@/lib/browser/store";
import type { Article, NewsItem, SearchHit, VideoClip, WeatherInfo } from "@/lib/browser/types";
import { parseOmnibox } from "@/lib/browser/url";
import { cn } from "@/lib/utils";

export function BrowserShell({
  news,
  initialWeather,
}: {
  news: NewsItem[];
  initialWeather: WeatherInfo | null;
}) {
  const tab = useActiveTab();
  const screen = useBrowserStore((s) => s.screen);
  const panel = useBrowserStore((s) => s.panel);
  const turbo = useBrowserStore((s) => s.turbo);
  const city = useBrowserStore((s) => s.city);
  const updateTab = useBrowserStore((s) => s.updateTab);
  const addHistory = useBrowserStore((s) => s.addHistory);
  const addDownload = useBrowserStore((s) => s.addDownload);
  const showToast = useBrowserStore((s) => s.showToast);
  const setPanel = useBrowserStore((s) => s.setPanel);
  const setScreen = useBrowserStore((s) => s.setScreen);
  const openPlayer = useBrowserStore((s) => s.openPlayer);

  const [omnibox, setOmnibox] = useState("");
  const [hits, setHits] = useState<SearchHit[]>([]);
  const [article, setArticle] = useState<Article | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fullSite, setFullSite] = useState(false);
  const [weather, setWeather] = useState<WeatherInfo | null>(initialWeather);
  const [videoHits, setVideoHits] = useState<VideoClip[]>([]);
  const [videoLoading, setVideoLoading] = useState(false);

  useEffect(() => {
    setOmnibox(tab.kind === "search" ? tab.query : tab.url);
    setFullSite(false);
    setError(null);
  }, [tab.id, tab.kind, tab.query, tab.url]);

  useEffect(() => {
    let cancelled = false;
    fetchWeather({ data: { city } })
      .then((w) => {
        if (!cancelled) setWeather(w);
      })
      .catch(() => {
        if (!cancelled) setWeather(null);
      });
    return () => {
      cancelled = true;
    };
  }, [city]);

  const openUrl = useCallback(
    async (raw: string, title?: string) => {
      const parsed = parseOmnibox(raw);
      if (parsed.type === "search") {
        await runSearch(parsed.value);
        return;
      }
      const url = parsed.value;
      const yt = youtubeIdFromUrl(url);
      if (yt) {
        addHistory(title || "YouTube", url);
        openPlayer(
          [{ id: yt, title: title || "YouTube", channel: "YouTube", category: "Web" }, ...FEATURED_VIDEOS.filter((c) => c.id !== yt)],
          0,
        );
        return;
      }
      updateTab(tab.id, { kind: "page", url, title: title || url, query: "" });
      addHistory(title || url, url);
      setLoading(true);
      setError(null);
      setArticle(null);
      setFullSite(!turbo);
      setScreen("home");
      try {
        const page = await fetchPage({ data: { url } });
        setArticle(page);
        updateTab(tab.id, { title: page.title, url: page.url });
        addHistory(page.title, page.url);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Could not load this page.");
      } finally {
        setLoading(false);
      }
    },
    [addHistory, tab.id, turbo, updateTab, setScreen, openPlayer],
  );

  const runSearch = useCallback(
    async (q: string) => {
      const query = q.trim();
      if (!query) return;
      updateTab(tab.id, { kind: "search", query, title: query, url: "" });
      setScreen("home");
      setLoading(true);
      setError(null);
      try {
        const next = await searchWeb({ data: { q: query } });
        setHits(next);
        if (!next.length) setError("No results. Try a different search.");
      } catch (err) {
        setError(err instanceof Error ? err.message : "Search failed.");
        setHits([]);
      } finally {
        setLoading(false);
      }
    },
    [tab.id, updateTab, setScreen],
  );

  const onSubmit = () => {
    const parsed = parseOmnibox(omnibox);
    if (parsed.type === "url") void openUrl(parsed.value);
    else void runSearch(parsed.value);
  };

  const onListen = () => {
    const Speech = (window as unknown as {
      webkitSpeechRecognition?: new () => {
        lang: string;
        start: () => void;
        onresult: ((ev: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null;
      };
    }).webkitSpeechRecognition;
    if (!Speech) {
      showToast("Voice search needs Chrome on your phone");
      return;
    }
    const rec = new Speech();
    rec.lang = "en-IN";
    rec.onresult = (ev) => {
      const text = ev.results[0]?.[0]?.transcript ?? "";
      setOmnibox(text);
      if (text) void runSearch(text);
    };
    rec.start();
  };

  const savePage = () => {
    if (!article) return;
    addDownload({
      name: article.title,
      url: article.url,
      source: article.site,
      status: "saved",
    });
    showToast("Saved to downloads");
  };

  let body: ReactNode = null;
  if (screen === "tabs") body = <TabsView />;
  else if (screen === "videos") {
    body = (
      <VideosView
        results={videoHits}
        loading={videoLoading}
        onPlay={(queue, i) => openPlayer(queue, i)}
        onSearch={(q) => {
          setVideoLoading(true);
          searchVideos({ data: { q } })
            .then(setVideoHits)
            .catch(() => setVideoHits([]))
            .finally(() => setVideoLoading(false));
        }}
      />
    );
  } else if (screen === "downloads") body = <DownloadsView />;
  else if (tab.kind === "home") {
    body = (
      <HomeView
        news={news}
        weather={weather}
        weatherCity={city}
        onSearch={(q) => {
          setOmnibox(q);
          void runSearch(q);
        }}
        onOpen={(url, title) => void openUrl(url, title)}
        onListen={onListen}
      />
    );
  } else if (tab.kind === "search") {
    body = (
      <>
        <AddressBar value={omnibox} onChange={setOmnibox} onSubmit={onSubmit} onBack={() => useBrowserStore.getState().goHome()} loading={loading} />
        <SearchResults
          query={tab.query}
          hits={hits}
          loading={loading}
          error={error}
          onOpen={(url, title) => void openUrl(url, title)}
          onPlayUrl={(url, title) => {
            const id = youtubeIdFromUrl(url);
            if (!id) return;
            openPlayer(
              [
                { id, title, channel: "YouTube", category: "Search" },
                ...FEATURED_VIDEOS.filter((c) => c.id !== id),
              ],
              0,
            );
          }}
        />
      </>
    );
  } else if (fullSite && tab.url) {
    body = (
      <>
        <AddressBar value={omnibox} onChange={setOmnibox} onSubmit={onSubmit} onBack={() => setFullSite(false)} loading={loading} />
        <LiveFrame url={tab.url} turboOff onNavigate={(url) => void openUrl(url)} />
      </>
    );
  } else {
    body = (
      <>
        <AddressBar value={omnibox} onChange={setOmnibox} onSubmit={onSubmit} onBack={() => useBrowserStore.getState().goHome()} loading={loading} />
        <ReaderView
          article={article}
          loading={loading}
          error={error}
          mode={turbo ? "turbo" : "live"}
          onOpenLink={(url, title) => void openUrl(url, title)}
          onFullSite={() => setFullSite(true)}
          onDownload={savePage}
          onPlay={(queue, i) => openPlayer(queue, i)}
        />
      </>
    );
  }

  return (
    <div className="min-h-dvh bg-bg md:flex md:items-center md:justify-center md:bg-desk md:p-5">
      <div
        className={cn(
          "relative mx-auto flex h-dvh w-full flex-col overflow-hidden bg-bg",
          "md:h-[90dvh] md:max-w-sm md:rounded-3xl md:shadow-card md:ring-1 md:ring-fg/10",
        )}
      >
        <div className="hidden shrink-0 md:block">
          <StatusTick />
        </div>
        {tab.isPrivate && screen === "home" ? (
          <p className="bg-elevated px-4 py-1.5 text-center text-xs font-medium text-muted">
            Private tab · history is not saved
          </p>
        ) : null}
        {body}
        <BottomNav onMenu={() => setPanel(panel === "menu" ? "none" : "menu")} />
        {panel === "menu" ? <MenuSheet onNavigate={(url, title) => void openUrl(url, title)} /> : null}
        {panel === "bookmarks" ||
        panel === "history" ||
        panel === "settings" ||
        panel === "shortcuts" ||
        panel === "find" ? (
          <ListPanel kind={panel} />
        ) : null}
        <ToastHost />
        <GesturePlayer />
      </div>
    </div>
  );
}

