import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import type { Article, NewsItem, SearchHit, VideoClip, WeatherInfo } from "./types";
import {
  extractArticle,
  parseDuckDuckGoHtml,
  parseOpenSearch,
  parseRss,
  parseWikipediaFeatured,
  weatherLabel,
} from "./parse";
import { assertPublicHttpUrl } from "./url";
import { CITIES } from "./cities";

const UA =
  "BlazeBrowser/1.0 (Android mobile browser; +https://grok.com) Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36";

const FALLBACK_NEWS: NewsItem[] = [
  {
    id: "fallback-web",
    title: "How a mobile browser really loads a page",
    snippet:
      "DNS, TLS, HTML, and the first paint — a plain-language tour of the round trip behind every tap.",
    url: "https://en.wikipedia.org/wiki/Web_browser",
    image: null,
    source: "Guide",
    category: "foryou",
  },
  {
    id: "fallback-wiki",
    title: "Wikipedia, the free encyclopedia",
    snippet: "Start on a random article or jump into today's featured story.",
    url: "https://en.wikipedia.org/wiki/Main_Page",
    image: null,
    source: "Wikipedia",
    category: "today",
  },
  {
    id: "fallback-http",
    title: "Hypertext Transfer Protocol",
    snippet: "The language browsers and servers speak, from status codes to headers.",
    url: "https://en.wikipedia.org/wiki/HTTP",
    image: null,
    source: "Wikipedia",
    category: "popular",
  },
];

async function fetchText(url: string, timeoutMs = 7000): Promise<string> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      signal: ctrl.signal,
      redirect: "follow",
      headers: {
        "user-agent": UA,
        accept: "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8",
      },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const buf = await res.arrayBuffer();
    if (buf.byteLength > 1_800_000) throw new Error("Page is too large.");
    const ct = res.headers.get("content-type") ?? "";
    const charset = /charset=([\w-]+)/i.exec(ct)?.[1] ?? "utf-8";
    try {
      return new TextDecoder(charset).decode(buf);
    } catch {
      return new TextDecoder("utf-8").decode(buf);
    }
  } finally {
    clearTimeout(timer);
  }
}

async function fetchJson(url: string, timeoutMs = 7000): Promise<unknown> {
  const text = await fetchText(url, timeoutMs);
  return JSON.parse(text) as unknown;
}

function uniqueNews(items: NewsItem[]): NewsItem[] {
  const seen = new Set<string>();
  const out: NewsItem[] = [];
  for (const item of items) {
    if (seen.has(item.url)) continue;
    seen.add(item.url);
    out.push(item);
  }
  return out;
}

export const fetchNews = createServerFn({ method: "GET" }).handler(async () => {
  const now = new Date();
  const y = now.getUTCFullYear();
  const m = String(now.getUTCMonth() + 1).padStart(2, "0");
  const d = String(now.getUTCDate()).padStart(2, "0");
  const wikiUrl = `https://en.wikipedia.org/api/rest_v1/feed/featured/${y}/${m}/${d}`;

  const wiki = fetchJson(wikiUrl)
    .then(parseWikipediaFeatured)
    .catch(() => [] as NewsItem[]);
  const bbc = fetchText("https://feeds.bbci.co.uk/news/world/rss.xml")
    .then((xml) => parseRss(xml, "BBC", "world"))
    .catch(() => [] as NewsItem[]);
  const verge = fetchText("https://www.theverge.com/rss/index.xml")
    .then((xml) => parseRss(xml, "The Verge", "popular"))
    .catch(() => [] as NewsItem[]);

  const [wikiItems, bbcItems, vergeItems] = await Promise.all([wiki, bbc, verge]);
  const foryou = uniqueNews([...wikiItems.slice(0, 6), ...bbcItems.slice(0, 4), ...vergeItems.slice(0, 4)]).map(
    (item) => ({ ...item, category: "foryou" as const }),
  );
  const merged = uniqueNews([...foryou, ...wikiItems, ...bbcItems, ...vergeItems]);
  return merged.length ? merged : FALLBACK_NEWS;
});

export const searchWeb = createServerFn({ method: "POST" })
  .validator(z.object({ q: z.string().min(1).max(300) }))
  .handler(async ({ data }): Promise<SearchHit[]> => {
    const q = data.q.trim();
    const wikiUrl = `https://en.wikipedia.org/w/api.php?action=opensearch&search=${encodeURIComponent(q)}&limit=4&namespace=0&format=json`;
    const ddgUrl = `https://html.duckduckgo.com/html/?q=${encodeURIComponent(q)}`;

    const wiki = fetchJson(wikiUrl)
      .then(parseOpenSearch)
      .catch(() => [] as SearchHit[]);
    const ddg = fetchText(ddgUrl)
      .then(parseDuckDuckGoHtml)
      .catch(() => [] as SearchHit[]);

    const [wikiHits, webHits] = await Promise.all([wiki, ddg]);
    const seen = new Set<string>();
    const out: SearchHit[] = [];
    for (const hit of [...wikiHits, ...webHits]) {
      if (seen.has(hit.url)) continue;
      seen.add(hit.url);
      out.push(hit);
    }
    return out.slice(0, 16);
  });

export const fetchPage = createServerFn({ method: "POST" })
  .validator(z.object({ url: z.string().min(4).max(2000) }))
  .handler(async ({ data }): Promise<Article> => {
    const url = assertPublicHttpUrl(data.url).href;
    const html = await fetchText(url);
    return extractArticle(html, url);
  });

export const fetchWeather = createServerFn({ method: "POST" })
  .validator(z.object({ city: z.string().min(2).max(40) }))
  .handler(async ({ data }): Promise<WeatherInfo> => {
    const key = data.city.toLowerCase();
    const city = CITIES[key] ?? { lat: 28.6139, lon: 77.209, label: "Delhi" };
    const url = `https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lon}&current=temperature_2m,weather_code`;
    const json = (await fetchJson(url)) as {
      current?: { temperature_2m?: number; weather_code?: number };
    };
    const temp = Math.round(json.current?.temperature_2m ?? 0);
    const code = json.current?.weather_code ?? 1;
    return { city: city.label, temp, code, label: weatherLabel(code) };
  });

export const searchVideos = createServerFn({ method: "POST" })
  .validator(z.object({ q: z.string().min(1).max(200) }))
  .handler(async ({ data }): Promise<VideoClip[]> => {
    const q = `${data.q.trim()} site:youtube.com`;
    const html = await fetchText(`https://html.duckduckgo.com/html/?q=${encodeURIComponent(q)}`);
    const hits = parseDuckDuckGoHtml(html);
    const clips: VideoClip[] = [];
    const seen = new Set<string>();
    for (const hit of hits) {
      const id = hit.url.match(/(?:v=|youtu\.be\/)([\w-]{11})/)?.[1];
      if (!id || seen.has(id)) continue;
      seen.add(id);
      clips.push({
        id,
        title: hit.title.replace(/- YouTube$/i, "").trim(),
        channel: "YouTube",
        category: "Search",
      });
      if (clips.length >= 8) break;
    }
    return clips;
  });
