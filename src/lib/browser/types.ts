export type ThemeMode = "light" | "dark" | "system";
export type FontScale = "sm" | "md" | "lg";
export type AppScreen = "home" | "tabs" | "videos" | "downloads";
export type OverlayPanel =
  | "none"
  | "menu"
  | "settings"
  | "bookmarks"
  | "history"
  | "shortcuts"
  | "find";
export type NewsCategory = "foryou" | "world" | "popular" | "today";
export type TabKind = "home" | "search" | "page";
export type PageMode = "turbo" | "live" | "proxy";

export interface Tab {
  id: string;
  kind: TabKind;
  title: string;
  url: string;
  query: string;
  isPrivate: boolean;
  createdAt: number;
}

export interface Bookmark {
  id: string;
  title: string;
  url: string;
  createdAt: number;
}

export interface HistoryItem {
  id: string;
  title: string;
  url: string;
  visitedAt: number;
}

export interface DownloadItem {
  id: string;
  name: string;
  url: string;
  source: string;
  status: "saved" | "opened";
  createdAt: number;
}

export interface Shortcut {
  id: string;
  title: string;
  url: string;
}

export interface SearchHit {
  title: string;
  url: string;
  snippet: string;
  source: "web" | "wiki";
}

export interface NewsItem {
  id: string;
  title: string;
  snippet: string;
  url: string;
  image: string | null;
  source: string;
  category: NewsCategory;
}

export interface WeatherInfo {
  city: string;
  temp: number;
  label: string;
  code: number;
}

export interface ArticleLink {
  title: string;
  url: string;
}

export interface Article {
  url: string;
  title: string;
  site: string;
  description: string;
  image: string | null;
  paragraphs: string[];
  links: ArticleLink[];
  favicon: string;
  videos: VideoClip[];
}

export interface VideoClip {
  id: string;
  title: string;
  channel: string;
  category: string;
  src?: string;
  poster?: string;
}
