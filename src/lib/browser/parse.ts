import type { Article, ArticleLink, NewsItem, SearchHit } from "./types";
import { extractMedia } from "./media";
import { faviconFor, hostOf } from "./url";

export function decodeEntities(input: string): string {
  return input
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&nbsp;/gi, " ")
    .replace(/&/gi, "&")
    .replace(/"/gi, '"')
    .replace(/&#39;|'/gi, "'")
    .replace(/</gi, "<")
    .replace(/>/gi, ">")
    .replace(/&#(\d+);/g, (_, n) => {
      const code = Number(n);
      return Number.isFinite(code) ? String.fromCharCode(code) : _;
    })
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => {
      const code = Number.parseInt(n, 16);
      return Number.isFinite(code) ? String.fromCharCode(code) : _;
    });
}

export function stripTags(input: string): string {
  return decodeEntities(input.replace(/<script[\s\S]*?<\/script>/gi, " ").replace(/<style[\s\S]*?<\/style>/gi, " ").replace(/<[^>]+>/g, " "))
    .replace(/\s+/g, " ")
    .trim();
}

function attr(html: string, name: string): string | null {
  const re = new RegExp(`${name}\\s*=\\s*["']([^"']+)["']`, "i");
  return html.match(re)?.[1] ?? null;
}

function metaContent(html: string, key: string): string | null {
  const property = html.match(
    new RegExp(`<meta[^>]+(?:property|name)=["']${key}["'][^>]*>`, "i"),
  )?.[0];
  if (property) {
    const content = attr(property, "content");
    if (content) return decodeEntities(content);
  }
  const reversed = html.match(
    new RegExp(`<meta[^>]+content=["']([^"']+)["'][^>]*(?:property|name)=["']${key}["'][^>]*>`, "i"),
  );
  return reversed ? decodeEntities(reversed[1] ?? "") : null;
}

function absolutize(href: string, base: string): string | null {
  try {
    return new URL(href, base).href;
  } catch {
    return null;
  }
}

export function extractArticle(html: string, url: string): Article {
  const rawTitle = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1] ?? "";
  const title =
    stripTags(metaContent(html, "og:title") ?? rawTitle) || hostOf(url);
  const description = stripTags(
    metaContent(html, "og:description") ?? metaContent(html, "description") ?? "",
  );
  const imageRaw = metaContent(html, "og:image") ?? metaContent(html, "twitter:image");
  const image = imageRaw ? absolutize(imageRaw, url) : null;
  const paragraphs = [...html.matchAll(/<p\b[^>]*>([\s\S]*?)<\/p>/gi)]
    .map((m) => stripTags(m[1] ?? ""))
    .filter((text) => text.length > 60 && !/cookie|subscribe to our|enable javascript/i.test(text))
    .filter((text, i, arr) => arr.findIndex((other) => other === text) === i)
    .slice(0, 28);

  const links: ArticleLink[] = [];
  const seen = new Set<string>();
  for (const match of html.matchAll(/<a\b[^>]*href=["']([^"'#]+)["'][^>]*>([\s\S]*?)<\/a>/gi)) {
    const abs = absolutize(match[1] ?? "", url);
    const label = stripTags(match[2] ?? "");
    if (!abs || !label || label.length < 12 || label.length > 90) continue;
    if (seen.has(abs) || abs === url) continue;
    if (!/^https?:/i.test(abs)) continue;
    seen.add(abs);
    links.push({ title: label, url: abs });
    if (links.length >= 12) break;
  }

  return {
    url,
    title,
    site: hostOf(url),
    description,
    image,
    paragraphs,
    links,
    favicon: faviconFor(url),
    videos: extractMedia(html, url),
  };
}

export function unwrapDuckDuckGo(href: string): string {
  try {
    const url = new URL(href, "https://duckduckgo.com");
    const uddg = url.searchParams.get("uddg");
    if (uddg) return uddg;
    return url.href.startsWith("//") ? `https:${url.href}` : url.href;
  } catch {
    return href;
  }
}

export function parseDuckDuckGoHtml(html: string): SearchHit[] {
  const hits: SearchHit[] = [];
  const seen = new Set<string>();
  const blocks = html.match(/<a[^>]*class="[^"]*result__a[^"]*"[^>]*>[\s\S]*?<\/a>/gi) ?? [];
  for (const block of blocks) {
    const href = attr(block, "href");
    const title = stripTags(block);
    if (!href || !title) continue;
    const url = unwrapDuckDuckGo(href.startsWith("//") ? `https:${href}` : href);
    if (!/^https?:/i.test(url) || seen.has(url)) continue;
    seen.add(url);
    hits.push({ title, url, snippet: "", source: "web" });
    if (hits.length >= 12) break;
  }

  const snippets = html.match(/<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*>[\s\S]*?<\/a>/gi) ?? [];
  snippets.forEach((block, i) => {
    const hit = hits[i];
    if (hit) hit.snippet = stripTags(block);
  });

  if (hits.length === 0) {
    for (const match of html.matchAll(/href="([^"]*uddg=[^"]+)"[^>]*>([\s\S]*?)<\/a>/gi)) {
      const url = unwrapDuckDuckGo(match[1] ?? "");
      const title = stripTags(match[2] ?? "");
      if (!title || !/^https?:/i.test(url) || seen.has(url)) continue;
      seen.add(url);
      hits.push({ title, url, snippet: "", source: "web" });
      if (hits.length >= 12) break;
    }
  }
  return hits;
}

export function parseOpenSearch(payload: unknown): SearchHit[] {
  if (!Array.isArray(payload) || payload.length < 4) return [];
  const titles = payload[1];
  const snippets = payload[2];
  const urls = payload[3];
  if (!Array.isArray(titles) || !Array.isArray(urls)) return [];
  const hits: SearchHit[] = [];
  for (let i = 0; i < titles.length; i += 1) {
    const title = String(titles[i] ?? "");
    const url = String(urls[i] ?? "");
    const snippet = Array.isArray(snippets) ? String(snippets[i] ?? "") : "";
    if (!title || !url) continue;
    hits.push({ title, url, snippet, source: "wiki" });
  }
  return hits;
}

interface WikiSummary {
  title?: string;
  extract?: string;
  thumbnail?: { source?: string };
  content_urls?: { desktop?: { page?: string } };
}

function wikiItem(entry: WikiSummary, category: NewsItem["category"], source: string): NewsItem | null {
  const title = entry.title?.trim();
  const url = entry.content_urls?.desktop?.page;
  if (!title || !url) return null;
  return {
    id: `${category}-${url}`,
    title,
    snippet: (entry.extract ?? "").trim(),
    url,
    image: entry.thumbnail?.source ?? null,
    source,
    category,
  };
}

export function parseWikipediaFeatured(json: unknown): NewsItem[] {
  if (!json || typeof json !== "object") return [];
  const data = json as {
    tfa?: WikiSummary;
    news?: Array<{ links?: WikiSummary[]; story?: string }>;
    mostread?: { articles?: WikiSummary[] };
    image?: { title?: string; thumbnail?: { source?: string }; description?: { text?: string }; file_page?: string };
  };
  const items: NewsItem[] = [];

  const tfa = data.tfa ? wikiItem(data.tfa, "today", "Wikipedia") : null;
  if (tfa) items.push(tfa);

  if (data.image?.title) {
    items.push({
      id: `today-image-${data.image.title}`,
      title: data.image.title.replace(/Image of the day/i, "").trim() || "Picture of the day",
      snippet: data.image.description?.text ?? "Featured photograph from Wikimedia Commons.",
      url: data.image.file_page ?? "https://commons.wikimedia.org/wiki/Main_Page",
      image: data.image.thumbnail?.source ?? null,
      source: "Commons",
      category: "today",
    });
  }

  for (const story of data.news ?? []) {
    const first = story.links?.[0];
    const parsed = first ? wikiItem(first, "world", "Wikipedia") : null;
    if (parsed) {
      parsed.snippet = stripTags(story.story ?? parsed.snippet);
      items.push(parsed);
    }
  }

  for (const article of data.mostread?.articles ?? []) {
    const parsed = wikiItem(article, "popular", "Wikipedia");
    if (parsed) items.push(parsed);
  }

  return items;
}

export function parseRss(xml: string, source: string, category: NewsItem["category"]): NewsItem[] {
  const blocks = xml.match(/<item[\s\S]*?<\/item>/gi) ?? xml.match(/<entry[\s\S]*?<\/entry>/gi) ?? [];
  const items: NewsItem[] = [];
  for (const block of blocks) {
    const title = stripTags(block.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1] ?? "");
    const link =
      block.match(/<link[^>]*href=["']([^"']+)["']/i)?.[1] ??
      stripTags(block.match(/<link[^>]*>([\s\S]*?)<\/link>/i)?.[1] ?? "");
    const snippet = stripTags(
      block.match(/<(?:description|summary|content)[^>]*>([\s\S]*?)<\/(?:description|summary|content)>/i)?.[1] ?? "",
    );
    const image =
      block.match(/<media:thumbnail[^>]+url=["']([^"']+)/i)?.[1] ??
      block.match(/<media:content[^>]+url=["']([^"']+)/i)?.[1] ??
      block.match(/<enclosure[^>]+url=["']([^"']+)/i)?.[1] ??
      block.match(/<img[^>]+src=["']([^"']+)/i)?.[1] ??
      null;
    if (!title || !link) continue;
    items.push({
      id: `${source}-${link}`,
      title,
      snippet,
      url: link,
      image,
      source,
      category,
    });
    if (items.length >= 12) break;
  }
  return items;
}

const AD_HOST = /(doubleclick|googlesyndication|adservice|facebook\.net|scorecardresearch|taboola|outbrain|adsystem)/i;

export function sanitizeDocument(html: string, pageUrl: string, adBlock: boolean): string {
  let out = html;
  out = out.replace(/<meta[^>]+http-equiv=["']content-security-policy["'][^>]*>/gi, "");
  out = out.replace(/<script[\s\S]*?<\/script>/gi, "");
  out = out.replace(/\son[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, "");
  out = out.replace(/<iframe[\s\S]*?<\/iframe>/gi, "");
  out = out.replace(/<object[\s\S]*?<\/object>/gi, "");
  if (adBlock) {
    out = out.replace(/<img\b[^>]+>/gi, (tag) => (AD_HOST.test(tag) ? "" : tag));
    out = out.replace(/<a\b[^>]+>/gi, (tag) => (AD_HOST.test(tag) ? "<a>" : tag));
  }
  let origin = pageUrl;
  try {
    origin = new URL(pageUrl).href;
  } catch {
    /* keep */
  }
  const intercept = `<base href="${origin}"><script>
(function(){
  function send(type, extra){ try { parent.postMessage(Object.assign({type:type}, extra||{}), '*'); } catch(e) {} }
  document.addEventListener('click', function(e){
    var n = e.target;
    while (n && n.tagName !== 'A') n = n.parentNode;
    if (!n || !n.href) return;
    if ((n.getAttribute('href')||'').indexOf('javascript:') === 0) { e.preventDefault(); return; }
    e.preventDefault();
    send('blaze:nav', { url: n.href });
  }, true);
  document.addEventListener('submit', function(e){ e.preventDefault(); }, true);
  send('blaze:meta', { title: document.title || '', url: ${JSON.stringify(pageUrl)} });
})();
</script>`;
  if (/<head/i.test(out)) {
    out = out.replace(/<head([^>]*)>/i, `<head$1>${intercept}`);
  } else {
    out = `<!doctype html><html><head>${intercept}</head><body>${out}</body></html>`;
  }
  return out;
}

export const WMO_LABELS: Record<number, string> = {
  0: "Clear",
  1: "Mostly clear",
  2: "Partly cloudy",
  3: "Overcast",
  45: "Fog",
  48: "Icy fog",
  51: "Light drizzle",
  61: "Rain",
  63: "Rain",
  71: "Snow",
  80: "Showers",
  95: "Storms",
};

export function weatherLabel(code: number): string {
  if (WMO_LABELS[code]) return WMO_LABELS[code];
  if (code >= 50 && code < 60) return "Drizzle";
  if (code >= 60 && code < 70) return "Rain";
  if (code >= 70 && code < 80) return "Snow";
  if (code >= 80 && code < 90) return "Showers";
  if (code >= 90) return "Storms";
  return "Fair";
}
