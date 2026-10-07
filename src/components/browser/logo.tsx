import { cn } from "@/lib/utils";

export function BlazeMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={cn("size-8", className)} aria-hidden="true">
      <rect width="32" height="32" rx="10" fill="currentColor" />
      <path
        d="M18.2 6.5c.4 2.4-.2 4.2-1.5 5.8 2.8.2 5.2 1.6 6.6 4.2-1.7-.6-3.3-.5-4.8.2 2.2 1.4 3.4 3.6 3.5 6.3-2.6-1.8-5-2-7.2-.8 1 2.2.8 4.4-.2 6.8-3.4-3.2-5.2-6.7-5.2-10.8 0-5.1 3.4-8.8 8.8-11.7Z"
        fill="#fff"
      />
    </svg>
  );
}

export function BlazeWordmark({ compact = false }: { compact?: boolean }) {
  return (
    <div className="flex items-center gap-2 text-primary">
      <BlazeMark className={compact ? "size-7" : "size-8"} />
      <span className={cn("font-semibold tracking-tight text-fg", compact ? "text-base" : "text-lg")}>
        Blaze
      </span>
    </div>
  );
}
