import {
  Cake,
  CalendarClock,
  CalendarDays,
  CheckCircle2,
  Clock,
  Ellipsis,
  Flag,
  Home,
  Inbox,
  KeyRound,
  Layers,
  Leaf,
  ListPlus,
  MoonStar,
  Plus,
  Search,
} from "lucide-react";

// Static, decorative stand-in for the home dashboard — mirrors NativeHomeDashboard's
// real layout (centered max-w-6xl column: brand header, date hero, the 2×3 category-tile
// grid, "My Lists" rows, plus the floating nav + Add Task FAB) so the blurred backdrop
// behind the auth card looks like the actual app. It only ever shows blurred, so the
// data is faked.
const TILES = [
  { label: "Scheduled", color: "#D98F4B", count: 18, Icon: CalendarClock },
  { label: "Priority", color: "#C97880", count: 8, Icon: Flag },
  { label: "Overdue", color: "#E06F66", count: 81, Icon: Clock },
  { label: "All", color: "#68717A", count: 99, Icon: Layers },
  { label: "Completed", color: "#719F84", count: 31, Icon: CheckCircle2 },
  { label: "Calendar", color: "#9A89D2", count: 18, Icon: CalendarDays },
] as const;

const LISTS = [
  { name: "Urdu", accent: "hsl(var(--accent-pink))", count: 7, Icon: KeyRound },
  { name: "Boat", accent: "hsl(var(--accent-gold))", count: 6, Icon: Cake },
  { name: "Cars", accent: "hsl(var(--accent-brick))", count: 1, Icon: Inbox },
] as const;

const TODAY_TILE_COLOR = "#6EA8E1";

function TileOverlay() {
  return (
    <>
      <div className="pointer-events-none absolute -left-14 -top-20 h-44 w-52 rounded-full bg-white/20 blur-2xl" />
      <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(135deg,rgba(255,255,255,0.12),rgba(231,243,255,0.10)_45%,rgba(255,242,250,0.08)_68%,transparent)]" />
    </>
  );
}

const circleButtonClass =
  "flex h-14 w-14 items-center justify-center rounded-full border border-white/70 bg-card/90 text-foreground dark:border-white/10";

export default function MockHomeBackdrop() {
  const titleDate = new Date().toLocaleDateString(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
  });

  return (
    <div className="relative h-full w-full overflow-hidden px-4 pt-4 sm:px-6 sm:pt-6 lg:px-10">
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-4 sm:gap-5">
        {/* Brand header + circular action buttons */}
        <header className="flex min-h-14 items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <MoonStar className="h-7 w-7 text-amber-400" />
            <span className="text-3xl font-black tracking-tight text-foreground">{"T'Day"}</span>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <span className={circleButtonClass}>
              <Search className="h-6 w-6 stroke-[2.6]" />
            </span>
            <span className={circleButtonClass}>
              <ListPlus className="h-6 w-6 stroke-[2.6]" />
            </span>
            <span className={circleButtonClass}>
              <Ellipsis className="h-6 w-6 stroke-[2.6]" />
            </span>
          </div>
        </header>

        {/* Date hero */}
        <div
          className="relative flex h-[70px] items-center justify-between overflow-hidden rounded-[26px] px-5 text-white"
          style={{ backgroundColor: TODAY_TILE_COLOR }}
        >
          <TileOverlay />
          <span className="relative truncate text-[1.38rem] font-black leading-none tracking-tight">
            {titleDate}
          </span>
          <span className="relative text-[2.1rem] font-black leading-none">0</span>
        </div>

        {/* Category tile grid */}
        <div className="grid grid-cols-2 gap-2.5 lg:grid-cols-3">
          {TILES.map(({ label, color, count, Icon }) => (
            <div
              key={label}
              className="relative min-h-[94px] overflow-hidden rounded-[26px] p-3 text-white"
              style={{ backgroundColor: color }}
            >
              <TileOverlay />
              <Icon className="pointer-events-none absolute -bottom-6 -right-5 h-24 w-24 stroke-[1.8] text-white/[0.18]" />
              <div className="relative flex h-full flex-col justify-between">
                <div className="flex items-start justify-between gap-3">
                  <Icon className="h-6 w-6 stroke-[2.5] text-white" />
                  <span className="text-[1.72rem] font-black leading-none">{count}</span>
                </div>
                <p className="truncate text-[1.28rem] font-black leading-6 tracking-tight">
                  {label}
                </p>
              </div>
            </div>
          ))}
        </div>

        {/* My Lists */}
        <h2 className="px-1 pt-12 text-[1.75rem] font-black leading-8 text-foreground sm:pt-0">
          My Lists
        </h2>
        <div className="space-y-2">
          {LISTS.map(({ name, accent, count, Icon }) => (
            <div
              key={name}
              className="relative flex h-[70px] items-center gap-3 overflow-hidden rounded-[26px] px-5 text-white"
              style={{
                background: `color-mix(in srgb, hsl(var(--card-muted)) 34%, ${accent} 66%)`,
              }}
            >
              <TileOverlay />
              <Icon className="pointer-events-none absolute -bottom-9 -right-7 h-28 w-28 stroke-[1.75] text-white/[0.18]" />
              <Icon className="relative h-6 w-6 shrink-0 stroke-[2.5] text-white" />
              <span className="relative min-w-0 flex-1 truncate text-[1.38rem] font-black leading-none tracking-tight">
                {name}
              </span>
              <span className="relative text-[1.5rem] font-black leading-none">{count}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Floating bottom nav */}
      <div className="absolute bottom-6 left-1/2 flex -translate-x-1/2 items-center gap-1 rounded-full border border-white/10 bg-card/90 p-2 shadow-lg">
        <span className="flex items-center gap-2 rounded-full bg-muted/60 px-4 py-2 text-accent">
          <Home className="h-5 w-5" />
          <span className="text-[15px] font-bold">Home</span>
        </span>
        <span className="px-3 py-2 text-muted-foreground">
          <Leaf className="h-5 w-5" />
        </span>
        <span className="px-3 py-2 text-muted-foreground">
          <Ellipsis className="h-5 w-5" />
        </span>
      </div>

      {/* Add Task FAB */}
      <div
        className="absolute bottom-6 right-10 flex items-center gap-2 rounded-full px-5 py-3 text-white shadow-lg"
        style={{ backgroundColor: TODAY_TILE_COLOR }}
      >
        <Plus className="h-5 w-5 stroke-[2.6]" />
        <span className="text-[15px] font-bold">Add Task</span>
      </div>
    </div>
  );
}
