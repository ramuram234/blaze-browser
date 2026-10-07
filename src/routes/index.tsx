import { createFileRoute } from "@tanstack/react-router";
import { BrowserShell } from "@/components/browser/shell";
import { fetchNews, fetchWeather } from "@/lib/browser/fetchers";
import type { NewsItem, WeatherInfo } from "@/lib/browser/types";

export const Route = createFileRoute("/")({
  loader: async () => {
    const news = await fetchNews().catch(() => [] as NewsItem[]);
    const weather = await fetchWeather({ data: { city: "delhi" } }).catch(() => null as WeatherInfo | null);
    return { news, weather };
  },
  component: Home,
});

function Home() {
  const { news, weather } = Route.useLoaderData();
  return <BrowserShell news={news} initialWeather={weather} />;
}
