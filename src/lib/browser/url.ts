export function faviconFor(url: string, size = 64): string {
  try {
    const host = new URL(url).hostname;
    return `https://www.google.com/s2/favicons?domain=${encodeURIComponent(host)}&sz=${size}`;
  } catch {
    return `https://www.google.com/s2/favicons?domain=wikipedia.org&sz=${size}`;
  }
}

export function hostOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
}

export function looksLikeUrl(input: string): boolean {
  const t = input.trim();
  if (/^https?:\/\//i.test(t)) return true;
  if (/\s/.test(t)) return false;
  return /^[\w-]+(\.[\w-]+)+(:\d+)?(\/.*)?$/i.test(t);
}

export function parseOmnibox(input: string): { type: "url" | "search"; value: string } {
  const t = input.trim();
  if (!t) return { type: "search", value: "" };
  if (/^https?:\/\//i.test(t)) return { type: "url", value: t };
  if (looksLikeUrl(t)) return { type: "url", value: `https://${t}` };
  return { type: "search", value: t };
}

export function assertPublicHttpUrl(raw: string): URL {
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    throw new Error("Enter a valid web address.");
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("Only http and https addresses are allowed.");
  }
  const host = url.hostname.replace(/^\[|\]$/g, "").toLowerCase();
  if (
    host === "localhost" ||
    host === "127.0.0.1" ||
    host === "::1" ||
    host === "0.0.0.0" ||
    host.endsWith(".local") ||
    host.endsWith(".internal")
  ) {
    throw new Error("That address is blocked.");
  }
  if (/^(10\.|127\.|0\.|169\.254\.|192\.168\.)/.test(host)) {
    throw new Error("That address is blocked.");
  }
  if (/^172\.(1[6-9]|2\d|3[0-1])\./.test(host)) {
    throw new Error("That address is blocked.");
  }
  if (/^100\.(6[4-9]|[7-9]\d|1[0-2]\d)\./.test(host)) {
    throw new Error("That address is blocked.");
  }
  return url;
}

export function youtubeThumb(id: string): string {
  return `https://i.ytimg.com/vi/${id}/hqdefault.jpg`;
}

export function youtubeEmbed(id: string): string {
  return `https://www.youtube-nocookie.com/embed/${id}?rel=0&modestbranding=1`;
}

export function uid(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}
