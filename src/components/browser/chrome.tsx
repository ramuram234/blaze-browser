import { useEffect, useState } from "react";
import {
  Bookmark,
  ChevronLeft,
  Download,
  House,
  LayoutGrid,
  Play,
  RotateCw,
  Search,
  SquareStack,
} from "lucide-react";
import { BlazeMark } from "./logo";
import { useActiveTab, useBrowserStore } from "@/lib/browser/store";
import { cn } from "@/lib/utils";
import type { AppScreen } from "@/lib/browser/types";

const NAV: { id: AppScreen; label: string; icon: typeof House }[] = [
  { id: "home", label: "Home", icon: House },
  { id: "videos", label: "Videos", icon: Play },
  { id: "tabs", label: "Windows", icon: SquareStack },
  { id: "downloads", label: "Files", icon: Download },
];

export function BottomNav({ onMenu }: { onMenu: () => void }) {
  const screen = useBrowserStore((s) => s.screen);
  const tabCount = useBrowserStore((s) => s.tabs.length);
  const setScreen = useBrowserStore((s) => s.setScreen);
  const goHome = useBrowserStore((s) => s.goHome);
  const panel = useBrowserStore((s) => s.panel);

  return (
    <nav className="grid grid-cols-5 border-t border-border bg-surface pb-[max(0.4rem,env(safe-area-inset-bottom))] pt-1">
      {NAV.map((item) => {
        const active = screen === item.id && panel === "none";
        const Icon = item.icon;
        return (
          <button
            key={item.id}
            type="button"
            onClick={() => {
              if (item.id === "home") goHome();
              else setScreen(item.id);
            }}
            className={cn(
              "press relative flex min-h-12 w-full min-w-0 flex-col items-center justify-center gap-0.5 text-subtle",
              active && "text-primary",
            )}
          >
            <span className="relative">
              <Icon className="size-5" strokeWidth={active ? 2.4 : 1.9} />
              {item.id === "tabs" ? (
                <span className="absolute -right-2.5 -top-1 rounded-full bg-primary px-1 text-xs font-semibold leading-4 text-primary-fg tabular-nums">
                  {tabCount}
                </span>
              ) : null}
            </span>
            <span className="w-full truncate whitespace-nowrap text-center text-xs font-medium leading-none">{item.label}</span>
          </button>
        );
      })}
      <button
        type="button"
        onClick={onMenu}
        className={cn(
          "press flex min-h-12 w-full min-w-0 flex-col items-center justify-center gap-0.5 text-subtle",
          panel === "menu" && "text-primary",
        )}
      >
        <LayoutGrid className="size-5" strokeWidth={panel === "menu" ? 2.4 : 1.9} />
        <span className="w-full truncate whitespace-nowrap text-center text-xs font-medium leading-none">Menu</span>
      </button>
    </nav>
  );
}

export function AddressBar({
  value,
  onChange,
  onSubmit,
  onBack,
  loading,
}: {
  value: string;
  onChange: (v: string) => void;
  onSubmit: () => void;
  onBack?: () => void;
  loading?: boolean;
}) {
  const tab = useActiveTab();
  const turbo = useBrowserStore((s) => s.turbo);
  const bookmarks = useBrowserStore((s) => s.bookmarks);
  const toggleBookmark = useBrowserStore((s) => s.toggleBookmark);
  const starred = Boolean(tab.url && bookmarks.some((b) => b.url === tab.url));

  return (
    <form
      className="flex items-center gap-1 px-3 pb-2 pt-1"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit();
      }}
    >
      {onBack ? (
        <button type="button" onClick={onBack} className="press grid size-11 place-items-center text-fg" aria-label="Back">
          <ChevronLeft className="size-6" />
        </button>
      ) : (
        <BlazeMark className="size-7 text-primary" />
      )}
      <label className="relative flex min-h-11 flex-1 items-center rounded-xl bg-surface shadow-card">
        <Search className="ml-3 size-4 shrink-0 text-subtle" />
        <input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="Search or enter site"
          className="min-w-0 flex-1 bg-transparent px-2.5 py-2.5 text-sm text-fg outline-none placeholder:text-subtle"
          inputMode="url"
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
          suppressHydrationWarning
          aria-label="Search or enter site"
        />
        {turbo ? (
          <span className="mr-1 rounded-full bg-primary-soft px-2 py-0.5 text-xs font-semibold text-primary-ink">
            Turbo
          </span>
        ) : null}
        {tab.url ? (
          <button
            type="button"
            className="press grid size-10 place-items-center text-muted"
            aria-label={starred ? "Remove bookmark" : "Add bookmark"}
            onClick={() => toggleBookmark(tab.title, tab.url)}
          >
            <Bookmark className={cn("size-4", starred && "fill-primary text-primary")} />
          </button>
        ) : null}
      </label>
      {loading ? (
        <span className="grid size-11 place-items-center text-primary" aria-label="Loading">
          <RotateCw className="size-4 animate-spin" />
        </span>
      ) : null}
    </form>
  );
}

export function ToastHost() {
  const toast = useBrowserStore((s) => s.toast);
  if (!toast) return null;
  return (
    <div className="pointer-events-none absolute inset-x-0 bottom-24 z-40 flex justify-center px-6">
      <p className="rounded-full bg-fg px-4 py-2 text-sm font-medium text-bg shadow-card">{toast}</p>
    </div>
  );
}

export function StatusTick() {
  const [hh, setHh] = useState("");
  useEffect(() => {
    const tick = () =>
      setHh(new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }));
    tick();
    const id = window.setInterval(tick, 30_000);
    return () => window.clearInterval(id);
  }, []);
  return (
    <div className="flex h-7 items-center justify-between px-5 text-xs font-medium text-muted tabular-nums">
      <span suppressHydrationWarning>{hh}</span>
      <span className="size-2.5 rounded-full bg-fg/15" aria-hidden />
    </div>
  );
}
