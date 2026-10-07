import { createFileRoute } from "@tanstack/react-router";
import { sanitizeDocument } from "@/lib/browser/parse";
import { assertPublicHttpUrl } from "@/lib/browser/url";

const UA =
  "BlazeBrowser/1.0 (Android mobile browser) Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36";

export const Route = createFileRoute("/api/proxy")({
  server: {
    handlers: {
      GET: async ({ request }) => {
        const raw = new URL(request.url).searchParams.get("url") ?? "";
        let target: URL;
        try {
          target = assertPublicHttpUrl(raw);
        } catch (err) {
          const message = err instanceof Error ? err.message : "Invalid address";
          return new Response(message, { status: 400, headers: { "content-type": "text/plain" } });
        }

        const ctrl = new AbortController();
        const timer = setTimeout(() => ctrl.abort(), 8000);
        try {
          const res = await fetch(target.href, {
            signal: ctrl.signal,
            redirect: "follow",
            headers: {
              "user-agent": UA,
              accept: "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
            },
          });
          const buf = await res.arrayBuffer();
          if (buf.byteLength > 1_800_000) {
            return new Response("Page is too large for turbo view.", { status: 413 });
          }
          const type = res.headers.get("content-type") ?? "text/html";
          if (!/html|xml|text\//i.test(type)) {
            return new Response(buf, {
              headers: {
                "content-type": type,
                "x-content-type-options": "nosniff",
              },
            });
          }
          const html = new TextDecoder("utf-8").decode(buf);
          const adBlock = new URL(request.url).searchParams.get("ads") !== "1";
          const body = sanitizeDocument(html, res.url || target.href, adBlock);
          return new Response(body, {
            headers: {
              "content-type": "text/html; charset=utf-8",
              "x-content-type-options": "nosniff",
              "cache-control": "no-store",
            },
          });
        } catch {
          return new Response("Could not load this page.", { status: 502, headers: { "content-type": "text/plain" } });
        } finally {
          clearTimeout(timer);
        }
      },
    },
  },
});
