import {
  ChevronLeft,
  ChevronRight,
  Pause,
  Play,
  SkipBack,
  SkipForward,
  Sun,
  Volume2,
  X,
} from "lucide-react";
import { useCallback, useEffect, useRef, useState, type PointerEvent, type ReactNode } from "react";
import { clipThumb, formatTime } from "@/lib/browser/media";
import { useBrowserStore } from "@/lib/browser/store";
import type { VideoClip } from "@/lib/browser/types";
import { cn } from "@/lib/utils";

type Gesture = "none" | "seek" | "volume" | "bright";

interface YTPlayer {
  playVideo: () => void;
  pauseVideo: () => void;
  seekTo: (s: number, allowSeekAhead: boolean) => void;
  getCurrentTime: () => number;
  getDuration: () => number;
  getPlayerState: () => number;
  getVolume: () => number;
  setVolume: (n: number) => void;
  isMuted: () => boolean;
  unMute: () => void;
  getVideoLoadedFraction: () => number;
  loadVideoById: (id: string) => void;
  destroy: () => void;
}

let ytPromise: Promise<void> | null = null;

function ensureYouTube(): Promise<void> {
  if (typeof window === "undefined") return Promise.reject(new Error("ssr"));
  const w = window as Window & {
    YT?: { Player: new (el: HTMLElement, opts: object) => YTPlayer };
    onYouTubeIframeAPIReady?: () => void;
  };
  if (w.YT?.Player) return Promise.resolve();
  if (ytPromise) return ytPromise;
  ytPromise = new Promise((resolve) => {
    w.onYouTubeIframeAPIReady = () => resolve();
    if (!document.querySelector('script[src*="youtube.com/iframe_api"]')) {
      const script = document.createElement("script");
      script.src = "https://www.youtube.com/iframe_api";
      document.head.appendChild(script);
    }
  });
  return ytPromise;
}

function clamp(n: number, min: number, max: number) {
  return Math.min(max, Math.max(min, n));
}

export function GesturePlayer() {
  const queue = useBrowserStore((s) => s.playerQueue);
  const index = useBrowserStore((s) => s.playerIndex);
  const closePlayer = useBrowserStore((s) => s.closePlayer);
  const setPlayerIndex = useBrowserStore((s) => s.setPlayerIndex);
  const clip = queue[index];
  if (!clip) return null;
  return (
    <PlayerStage
      key={clip.id}
      clip={clip}
      queue={queue}
      index={index}
      onClose={closePlayer}
      onIndex={setPlayerIndex}
    />
  );
}

function PlayerStage({
  clip,
  queue,
  index,
  onClose,
  onIndex,
}: {
  clip: VideoClip;
  queue: VideoClip[];
  index: number;
  onClose: () => void;
  onIndex: (i: number) => void;
}) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const ytHostRef = useRef<HTMLDivElement | null>(null);
  const ytRef = useRef<YTPlayer | null>(null);
  const stageRef = useRef<HTMLDivElement | null>(null);
  const startRef = useRef({ x: 0, y: 0, vol: 80, bright: 100, time: 0, duration: 0 });
  const gestureRef = useRef<Gesture>("none");
  const pointerRef = useRef<number | null>(null);

  const [playing, setPlaying] = useState(true);
  const [time, setTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [buffered, setBuffered] = useState(0);
  const [volume, setVolume] = useState(80);
  const [brightness, setBrightness] = useState(100);
  const [waiting, setWaiting] = useState(true);
  const [hud, setHud] = useState<{ kind: Gesture; label: string } | null>(null);
  const [chrome, setChrome] = useState(true);
  const [hint, setHint] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const isFile = Boolean(clip.src);
  const next = queue[index + 1];

  const readTime = useCallback(() => {
    const yt = ytRef.current;
    const video = videoRef.current;
    if (video) {
      setTime(video.currentTime || 0);
      setDuration(video.duration && Number.isFinite(video.duration) ? video.duration : 0);
      if (video.buffered.length) {
        setBuffered(video.buffered.end(video.buffered.length - 1) / (video.duration || 1));
      }
      setPlaying(!video.paused);
      setWaiting(video.readyState < 3 && !video.paused);
      return;
    }
    if (yt) {
      try {
        setTime(yt.getCurrentTime() || 0);
        setDuration(yt.getDuration() || 0);
        setBuffered(yt.getVideoLoadedFraction() || 0);
        const state = yt.getPlayerState();
        setPlaying(state === 1);
        setWaiting(state === 3);
      } catch {
        /* player not ready */
      }
    }
  }, []);

  useEffect(() => {
    const id = window.setInterval(readTime, 250);
    const hintTimer = window.setTimeout(() => setHint(false), 3200);
    const chromeTimer = window.setTimeout(() => setChrome(false), 2800);
    return () => {
      window.clearInterval(id);
      window.clearTimeout(hintTimer);
      window.clearTimeout(chromeTimer);
    };
  }, [readTime]);

  useEffect(() => {
    if (isFile) return;
    const host = ytHostRef.current;
    if (!host) return;
    let dead = false;
    ensureYouTube()
      .then(() => {
        if (dead || !ytHostRef.current) return;
        const w = window as unknown as {
          YT: { Player: new (el: HTMLElement, opts: object) => YTPlayer };
        };
        ytRef.current = new w.YT.Player(ytHostRef.current, {
          videoId: clip.id,
          playerVars: {
            autoplay: 1,
            controls: 0,
            rel: 0,
            modestbranding: 1,
            playsinline: 1,
            fs: 0,
            disablekb: 1,
            iv_load_policy: 3,
            origin: window.location.origin,
          },
          events: {
            onReady: (ev: { target: YTPlayer }) => {
              ev.target.unMute();
              ev.target.setVolume(volume);
              ev.target.playVideo();
              setWaiting(false);
            },
            onStateChange: (ev: { data: number }) => {
              if (ev.data === 0 && index < queue.length - 1) onIndex(index + 1);
              if (ev.data === 1) setWaiting(false);
              if (ev.data === 3) setWaiting(true);
            },
            onError: () => setError("This video cannot play here. Try another clip."),
          },
        });
      })
      .catch(() => setError("Could not load the player."));
    return () => {
      dead = true;
      try {
        ytRef.current?.destroy();
      } catch {
        /* ignore */
      }
      ytRef.current = null;
    };
    // volume is applied on ready from the render that created the player
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clip.id, isFile, index, onIndex, queue.length]);

  const applyVolume = (v: number) => {
    const nextVol = clamp(Math.round(v), 0, 100);
    setVolume(nextVol);
    if (videoRef.current) videoRef.current.volume = nextVol / 100;
    try {
      ytRef.current?.setVolume(nextVol);
      ytRef.current?.unMute();
    } catch {
      /* ignore */
    }
  };

  const seekTo = (t: number) => {
    const d = duration || startRef.current.duration || 0;
    const nextTime = clamp(t, 0, d || t);
    if (videoRef.current) videoRef.current.currentTime = nextTime;
    try {
      ytRef.current?.seekTo(nextTime, true);
    } catch {
      /* ignore */
    }
    setTime(nextTime);
  };

  const togglePlay = () => {
    const video = videoRef.current;
    if (video) {
      if (video.paused) void video.play();
      else video.pause();
      setPlaying(!video.paused);
      return;
    }
    try {
      const state = ytRef.current?.getPlayerState();
      if (state === 1) ytRef.current?.pauseVideo();
      else ytRef.current?.playVideo();
    } catch {
      /* ignore */
    }
  };

  const onPointerDown = (e: PointerEvent<HTMLDivElement>) => {
    if ((e.target as HTMLElement).closest("[data-chrome]")) return;
    pointerRef.current = e.pointerId;
    e.currentTarget.setPointerCapture(e.pointerId);
    const rect = e.currentTarget.getBoundingClientRect();
    startRef.current = {
      x: e.clientX,
      y: e.clientY,
      vol: volume,
      bright: brightness,
      time,
      duration: duration || videoRef.current?.duration || ytRef.current?.getDuration() || 0,
    };
    gestureRef.current = "none";
    void rect;
  };

  const onPointerMove = (e: PointerEvent<HTMLDivElement>) => {
    if (pointerRef.current !== e.pointerId) return;
    const dx = e.clientX - startRef.current.x;
    const dy = e.clientY - startRef.current.y;
    const rect = stageRef.current?.getBoundingClientRect();
    const w = rect?.width || 1;
    const h = rect?.height || 1;
    if (gestureRef.current === "none") {
      if (Math.hypot(dx, dy) < 14) return;
      if (Math.abs(dx) > Math.abs(dy) * 1.15) gestureRef.current = "seek";
      else gestureRef.current = startRef.current.x - (rect?.left ?? 0) < w / 2 ? "bright" : "volume";
      setHint(false);
    }
    if (gestureRef.current === "seek") {
      const span = Math.max(startRef.current.duration * 0.45, 40);
      const nextTime = clamp(startRef.current.time + (dx / w) * span, 0, startRef.current.duration || span);
      setHud({
        kind: "seek",
        label: `${dx >= 0 ? "+" : "−"}${formatTime(Math.abs(nextTime - startRef.current.time))}  ${formatTime(nextTime)}`,
      });
      setTime(nextTime);
    } else if (gestureRef.current === "volume") {
      const nextVol = clamp(startRef.current.vol - (dy / h) * 120, 0, 100);
      applyVolume(nextVol);
      setHud({ kind: "volume", label: `${Math.round(nextVol)}%` });
    } else if (gestureRef.current === "bright") {
      const nextBright = clamp(startRef.current.bright - (dy / h) * 120, 8, 100);
      setBrightness(nextBright);
      setHud({ kind: "bright", label: `${Math.round(nextBright)}%` });
    }
  };

  const onPointerUp = (e: PointerEvent<HTMLDivElement>) => {
    if (pointerRef.current !== e.pointerId) return;
    pointerRef.current = null;
    const dx = e.clientX - startRef.current.x;
    const dy = e.clientY - startRef.current.y;
    const g = gestureRef.current;
    gestureRef.current = "none";
    if (g === "none" && Math.hypot(dx, dy) < 10) {
      togglePlay();
      setChrome((c) => !c);
    }
    if (g === "seek") {
      const rect = stageRef.current?.getBoundingClientRect();
      const w = rect?.width || 1;
      const span = Math.max(startRef.current.duration * 0.45, 40);
      seekTo(startRef.current.time + (dx / w) * span);
    }
    window.setTimeout(() => setHud(null), 280);
  };

  return (
    <div
      ref={stageRef}
      className="absolute inset-0 z-50 flex flex-col bg-desk text-primary-fg"
      style={{ touchAction: "none" }}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
    >
      <div
        className="relative min-h-0 flex-1 overflow-hidden"
        style={{ filter: `brightness(${brightness / 100})` }}
      >
        {isFile ? (
          <video
            ref={videoRef}
            src={clip.src}
            poster={clip.poster}
            className="size-full object-contain"
            autoPlay
            playsInline
            preload="auto"
            onClick={(e) => e.preventDefault()}
            onWaiting={() => setWaiting(true)}
            onPlaying={() => {
              setWaiting(false);
              setPlaying(true);
            }}
            onPause={() => setPlaying(false)}
            onEnded={() => {
              if (index < queue.length - 1) onIndex(index + 1);
            }}
            onLoadedMetadata={() => {
              if (videoRef.current) videoRef.current.volume = volume / 100;
              readTime();
            }}
            onProgress={readTime}
            onError={() => setError("Could not load this file.")}
          />
        ) : (
          <div ref={ytHostRef} className="size-full [&_iframe]:size-full" />
        )}
        {waiting && !error ? (
          <div className="pointer-events-none absolute inset-0 grid place-items-center">
            <span className="size-10 animate-spin rounded-full border-2 border-primary-fg/20 border-t-primary" />
          </div>
        ) : null}
        {!playing && !waiting ? (
          <div className="pointer-events-none absolute inset-0 grid place-items-center">
            <span className="grid size-16 place-items-center rounded-full bg-fg/50">
              <Play className="size-8 translate-x-0.5" />
            </span>
          </div>
        ) : null}
        {error ? (
          <p className="absolute inset-x-6 top-1/2 -translate-y-1/2 text-center text-sm">{error}</p>
        ) : null}
      </div>

      {hud ? (
        <div className="pointer-events-none absolute inset-0 z-10">
          {hud.kind === "seek" ? (
            <div className="absolute left-1/2 top-1/2 flex -translate-x-1/2 -translate-y-1/2 items-center gap-2 rounded-2xl bg-fg/70 px-4 py-3 text-sm font-semibold tabular-nums">
              {hud.label.startsWith("+") ? <SkipForward className="size-4" /> : <SkipBack className="size-4" />}
              {hud.label}
            </div>
          ) : null}
          {hud.kind === "volume" ? (
            <SideMeter
              side="right"
              icon={<Volume2 className="size-4" />}
              value={volume}
              label={hud.label}
            />
          ) : null}
          {hud.kind === "bright" ? (
            <SideMeter
              side="left"
              icon={<Sun className="size-4" />}
              value={brightness}
              label={hud.label}
            />
          ) : null}
        </div>
      ) : null}

      {hint ? (
        <div className="pointer-events-none absolute inset-x-6 top-1/3 z-10 rounded-2xl bg-fg/70 px-4 py-3 text-center text-xs leading-relaxed">
          Swipe left or right to seek. Right edge up/down is volume. Left edge up/down is brightness.
        </div>
      ) : null}

      <div
        data-chrome
        className={cn(
          "absolute inset-x-0 top-0 z-20 flex items-start justify-between bg-linear-to-b from-desk/80 to-transparent px-2 pt-2 pb-8 transition-opacity duration-150",
          chrome ? "opacity-100" : "opacity-0",
        )}
      >
        <button
          type="button"
          onClick={onClose}
          className="press grid size-11 place-items-center"
          aria-label="Close player"
        >
          <X className="size-5" />
        </button>
        <div className="min-w-0 flex-1 px-2 pt-2.5">
          <p className="truncate text-sm font-semibold">{clip.title}</p>
          <p className="truncate text-xs text-primary-fg/70">{clip.channel}</p>
        </div>
        <span className="mt-2 rounded-full bg-primary px-2 py-0.5 text-xs font-semibold">
          {Math.round(buffered * 100)}% loaded
        </span>
      </div>

      <div
        data-chrome
        className={cn(
          "absolute inset-x-0 bottom-0 z-20 bg-linear-to-t from-desk/90 to-transparent px-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-8 transition-opacity duration-150",
          chrome ? "opacity-100" : "opacity-0",
        )}
      >
        <div className="relative mb-3 h-1.5 overflow-hidden rounded-full bg-primary-fg/20">
          <div className="absolute inset-y-0 left-0 bg-primary-fg/35" style={{ width: `${buffered * 100}%` }} />
          <div className="absolute inset-y-0 left-0 bg-primary" style={{ width: `${duration ? (time / duration) * 100 : 0}%` }} />
        </div>
        <div className="mb-2 flex justify-between text-xs tabular-nums text-primary-fg/80">
          <span>{formatTime(time)}</span>
          <span>{formatTime(duration)}</span>
        </div>
        <div className="flex items-center justify-center gap-6">
          <button
            type="button"
            className="press grid size-11 place-items-center disabled:opacity-30"
            disabled={index <= 0}
            onClick={() => onIndex(index - 1)}
            aria-label="Previous"
          >
            <ChevronLeft className="size-7" />
          </button>
          <button
            type="button"
            className="press grid size-14 place-items-center rounded-full bg-primary text-primary-fg"
            onClick={togglePlay}
            aria-label={playing ? "Pause" : "Play"}
          >
            {playing ? <Pause className="size-7" /> : <Play className="size-7 translate-x-0.5" />}
          </button>
          <button
            type="button"
            className="press grid size-11 place-items-center disabled:opacity-30"
            disabled={index >= queue.length - 1}
            onClick={() => onIndex(index + 1)}
            aria-label="Next"
          >
            <ChevronRight className="size-7" />
          </button>
        </div>
      </div>

      {next?.src ? (
        <video src={next.src} preload="auto" muted playsInline className="pointer-events-none hidden" />
      ) : next && clipThumb(next) ? (
        <img src={clipThumb(next)} alt="" className="hidden" />
      ) : null}
    </div>
  );
}

function SideMeter({
  side,
  icon,
  value,
  label,
}: {
  side: "left" | "right";
  icon: ReactNode;
  value: number;
  label: string;
}) {
  return (
    <div
      className={cn(
        "absolute top-1/2 flex -translate-y-1/2 items-center gap-2",
        side === "left" ? "left-4" : "right-4 flex-row-reverse",
      )}
    >
      <div className="relative h-28 w-1.5 overflow-hidden rounded-full bg-primary-fg/20">
        <span
          className="absolute inset-x-0 bottom-0 bg-primary"
          style={{ height: `${clamp(value, 0, 100)}%` }}
        />
      </div>
      <div className="flex flex-col items-center gap-1">
        {icon}
        <span className="text-xs font-semibold tabular-nums">{label}</span>
      </div>
    </div>
  );
}
