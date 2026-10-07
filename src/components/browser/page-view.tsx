import { ExternalLink, FileDown, Globe, Play } from "lucide-react";
import { useMemo } from "react";
import type { Article, VideoClip } from "@/lib/browser/types";
import { clipThumb, youtubeIdFromUrl } from "@/lib/browser/media";
import { useBrowserStore } from "@/lib/browser/store";
import { cn } from "@/lib/utils";

export function SearchResults({
  query,
  hits,
  loading,
  error,
  onOpen,
  onPlayUrl,
}: {
  query: string;
  hits: { title: string; url: string; snippet: string; source: "web" | "wiki" }[];
  loading: boolean;
  error: string | null;
  onOpen: (url: string, title?: string) => void;
  onPlayUrl?: (url: string, title: string) => void;
}) {
  return (
    <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
      <p className="mb-3 text-xs font-medium text-muted">
        {loading ? "Searching…" : `Results for “${query}”`}
      </p>
      {error ? <p className="rounded-xl bg-primary-soft px-3 py-2 text-sm text-primary-ink">{error}</p> : null}
      {loading ? (
        <div className="flex flex-col gap-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-20 animate-pulse rounded-2xl bg-surface" />
          ))}
        </div>
      ) : (
        <ul className="flex flex-col gap-2">
          {hits.map((hit) => (
            <li key={hit.url}>
              <button
                type="button"
                onClick={() => {
                  if (onPlayUrl && youtubeIdFromUrl(hit.url)) {
                    onPlayUrl(hit.url, hit.title);
                    return;
                  }
                  onOpen(hit.url, hit.title);
                }}
                className="press w-full rounded-2xl bg-surface px-3.5 py-3 text-left shadow-card"
              >
                <span className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted">
                  {youtubeIdFromUrl(hit.url) ? "Video" : hit.source === "wiki" ? "Wikipedia" : "Web"}
                  <span className="truncate font-normal normal-case tracking-normal">{new URL(hit.url).hostname}</span>
                </span>
                <span className="mt-1 block text-sm font-semibold leading-snug">{hit.title}</span>
                {hit.snippet ? (
                  <span className="mt-1 line-clamp-2 block text-xs leading-relaxed text-muted">{hit.snippet}</span>
                ) : null}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function ReaderView({
  article,
  loading,
  error,
  mode,
  onOpenLink,
  onFullSite,
  onDownload,
  onPlay,
}: {
  article: Article | null;
  loading: boolean;
  error: string | null;
  mode: "turbo" | "live" | "proxy";
  onOpenLink: (url: string, title?: string) => void;
  onFullSite: () => void;
  onDownload: () => void;
  onPlay?: (queue: VideoClip[], index: number) => void;
}) {
  const fontScale = useBrowserStore((s) => s.fontScale);
  const findQuery = useBrowserStore((s) => s.findQuery);
  const size = fontScale === "sm" ? "text-sm" : fontScale === "lg" ? "text-lg" : "text-base";

  const paragraphs = useMemo(() => {
    if (!article) return [];
    const q = findQuery.trim().toLowerCase();
    if (!q) return article.paragraphs;
    return article.paragraphs.filter((p) => p.toLowerCase().includes(q));
  }, [article, findQuery]);

  if (loading) {
    return (
      <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto px-4 py-6">
        <div className="h-6 w-2/3 animate-pulse rounded-md bg-surface" />
        <div className="h-40 animate-pulse rounded-2xl bg-surface" />
        <div className="h-4 w-full animate-pulse rounded bg-surface" />
        <div className="h-4 w-5/6 animate-pulse rounded bg-surface" />
        <div className="h-4 w-4/6 animate-pulse rounded bg-surface" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
        <Globe className="size-10 text-primary" />
        <h2 className="text-lg font-semibold">Couldn’t open the page</h2>
        <p className="max-w-sm text-sm text-muted">{error}</p>
        <button type="button" onClick={onFullSite} className="press rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-fg">
          Try full site
        </button>
      </div>
    );
  }

  if (!article) return null;

  return (
    <article className="min-h-0 flex-1 overflow-y-auto px-4 pb-8 pt-2">
      <div className="mb-3 flex items-center gap-2 text-xs text-muted">
        <img src={article.favicon} alt="" className="size-4 rounded-sm" />
        <span className="truncate">{article.site}</span>
        <span className="ml-auto rounded-full bg-primary-soft px-2 py-0.5 font-semibold text-primary-ink">
          {mode === "turbo" ? "Turbo page" : "Reader"}
        </span>
      </div>
      <h1 className="text-2xl font-semibold leading-tight tracking-tight">{article.title}</h1>
      {article.description ? <p className="mt-2 text-sm leading-relaxed text-muted">{article.description}</p> : null}
      <div className="mt-3 flex gap-2">
        <button
          type="button"
          onClick={onFullSite}
          className="press inline-flex min-h-10 items-center gap-1.5 rounded-full bg-surface px-3 text-xs font-semibold shadow-card"
        >
          <ExternalLink className="size-3.5" />
          Full site
        </button>
        <button
          type="button"
          onClick={onDownload}
          className="press inline-flex min-h-10 items-center gap-1.5 rounded-full bg-surface px-3 text-xs font-semibold shadow-card"
        >
          <FileDown className="size-3.5" />
          Save
        </button>
      </div>
      {article.image ? (
        <img
          src={article.image}
          alt=""
          className="mt-4 w-full rounded-2xl object-cover outline outline-1 -outline-offset-1 outline-fg/10"
        />
      ) : null}
      {article.videos?.length && onPlay ? (
        <section className="mt-5">
          <h2 className="mb-2 text-sm font-semibold">Videos on this page</h2>
          <div className="flex flex-col gap-2">
            {article.videos.map((clip, i) => (
              <button
                key={clip.id}
                type="button"
                onClick={() => onPlay(article.videos, i)}
                className="press flex gap-3 overflow-hidden rounded-2xl bg-surface p-2 text-left shadow-card"
              >
                {clipThumb(clip) ? (
                  <img
                    src={clipThumb(clip)}
                    alt=""
                    className="h-16 w-28 shrink-0 rounded-xl object-cover outline outline-1 -outline-offset-1 outline-fg/10"
                  />
                ) : (
                  <span className="grid h-16 w-28 shrink-0 place-items-center rounded-xl bg-primary-soft text-primary">
                    <Play className="size-5 translate-x-0.5" />
                  </span>
                )}
                <span className="min-w-0 py-1">
                  <span className="block truncate text-sm font-semibold">{clip.title}</span>
                  <span className="block text-xs text-muted">{clip.channel}</span>
                </span>
              </button>
            ))}
          </div>
        </section>
      ) : null}
      <div className={cn("mt-5 space-y-4 leading-relaxed text-fg", size)}>
        {(paragraphs.length ? paragraphs : ["This page has little readable text. Try full site for the original layout."]).map(
          (p, i) => (
            <p key={i}>{highlight(p, findQuery)}</p>
          ),
        )}
      </div>
      {article.links.length ? (
        <section className="mt-8">
          <h2 className="mb-2 text-sm font-semibold">On this site</h2>
          <ul className="flex flex-col gap-1.5">
            {article.links.map((link) => (
              <li key={link.url}>
                <button
                  type="button"
                  onClick={() => onOpenLink(link.url, link.title)}
                  className="press w-full rounded-xl bg-surface px-3 py-2.5 text-left text-sm shadow-card"
                >
                  {link.title}
                </button>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </article>
  );
}

function highlight(text: string, query: string) {
  const q = query.trim();
  if (!q) return text;
  const idx = text.toLowerCase().indexOf(q.toLowerCase());
  if (idx < 0) return text;
  return (
    <>
      {text.slice(0, idx)}
      <mark className="mark-hit">{text.slice(idx, idx + q.length)}</mark>
      {text.slice(idx + q.length)}
    </>
  );
}

export function LiveFrame({
  url,
  turboOff,
  onNavigate,
}: {
  url: string;
  turboOff: boolean;
  onNavigate: (url: string) => void;
}) {
  const src = turboOff ? `/api/proxy?url=${encodeURIComponent(url)}` : url;

  return (
    <iframe
      title="Page"
      src={src}
      className="min-h-0 flex-1 bg-surface"
      sandbox="allow-scripts allow-same-origin allow-popups-to-escape-sandbox allow-forms"
      referrerPolicy="no-referrer"
      onLoad={(e) => {
        try {
          const doc = e.currentTarget.contentDocument;
          if (!doc) return;
          const title = doc.title;
          if (title) useBrowserStore.getState().updateTab(useBrowserStore.getState().activeTabId, { title });
        } catch {
          /* cross-origin live frame */
        }
      }}
      ref={(frame) => {
        if (!frame) return;
        const handler = (event: MessageEvent) => {
          if (event.source !== frame.contentWindow) return;
          const data = event.data as { type?: string; url?: string; title?: string };
          if (data?.type === "blaze:nav" && data.url) onNavigate(data.url);
          if (data?.type === "blaze:meta" && data.title) {
            useBrowserStore.getState().updateTab(useBrowserStore.getState().activeTabId, {
              title: data.title,
            });
          }
        };
        window.addEventListener("message", handler);
        (frame as HTMLIFrameElement & { __off?: () => void }).__off = () =>
          window.removeEventListener("message", handler);
      }}
    />
  );
}
