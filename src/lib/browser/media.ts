import type { VideoClip } from "./types";
import { hostOf } from "./url";

export function youtubeIdFromUrl(raw: string): string | null {
  try {
    const url = new URL(raw);
    const host = url.hostname.replace(/^www\./, "");
    if (host === "youtu.be") {
      const id = url.pathname.split("/").filter(Boolean)[0] ?? "";
      return /^[\w-]{11}$/.test(id) ? id : null;
    }
    if (host === "youtube.com" || host === "m.youtube.com" || host === "music.youtube.com" || host === "youtube-nocookie.com") {
      const v = url.searchParams.get("v");
      if (v && /^[\w-]{11}$/.test(v)) return v;
      const m = url.pathname.match(/\/(?:embed|shorts|live)\/([\w-]{11})/);
      if (m?.[1]) return m[1];
    }
  } catch {
    return null;
  }
  return null;
}

function absolutize(href: string, base: string): string | null {
  try {
    return new URL(href, base).href;
  } catch {
    return null;
  }
}

export function extractMedia(html: string, pageUrl: string): VideoClip[] {
  const clips: VideoClip[] = [];
  const seen = new Set<string>();

  const addYt = (id: string, title: string) => {
    if (!/^[\w-]{11}$/.test(id) || seen.has(id)) return;
    seen.add(id);
    clips.push({ id, title: title || "YouTube video", channel: "YouTube", category: "Page" });
  };

  for (const match of html.matchAll(
    /https?:\/\/(?:www\.)?(?:youtube\.com\/(?:watch\?[^"'<\s]*v=|embed\/|shorts\/)|youtu\.be\/)([\w-]{11})/gi,
  )) {
    addYt(match[1] ?? "", "Video from page");
  }

  for (const match of html.matchAll(/<video\b([^>]*)>/gi)) {
    const tag = match[1] ?? "";
    const src = tag.match(/src=["']([^"']+)["']/i)?.[1];
    const poster = tag.match(/poster=["']([^"']+)["']/i)?.[1];
    const abs = src ? absolutize(src, pageUrl) : null;
    if (abs && /\.(mp4|webm|ogg|m4v)(\?|$)/i.test(abs) && !seen.has(abs)) {
      seen.add(abs);
      clips.push({
        id: abs,
        title: "Page video",
        channel: hostOf(pageUrl),
        category: "Page",
        src: abs,
        poster: poster ? absolutize(poster, pageUrl) ?? undefined : undefined,
      });
    }
  }

  for (const match of html.matchAll(/<source\b[^>]*src=["']([^"']+)["'][^>]*>/gi)) {
    const abs = absolutize(match[1] ?? "", pageUrl);
    if (abs && /\.(mp4|webm|ogg|m4v)(\?|$)/i.test(abs) && !seen.has(abs)) {
      seen.add(abs);
      clips.push({
        id: abs,
        title: "Page video",
        channel: hostOf(pageUrl),
        category: "Page",
        src: abs,
      });
    }
  }

  return clips.slice(0, 8);
}

export function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return "0:00";
  const s = Math.floor(seconds);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = s % 60;
  const pad = (n: number) => String(n).padStart(2, "0");
  return h > 0 ? `${h}:${pad(m)}:${pad(r)}` : `${m}:${pad(r)}`;
}

export function clipThumb(clip: VideoClip): string | undefined {
  if (clip.poster) return clip.poster;
  if (!clip.src && /^[\w-]{11}$/.test(clip.id)) {
    return `https://i.ytimg.com/vi/${clip.id}/hqdefault.jpg`;
  }
  return undefined;
}
