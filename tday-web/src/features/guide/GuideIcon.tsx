import {
  AlarmClock,
  Bell,
  BellRing,
  Calendar,
  Car,
  CarFront,
  CheckCheck,
  CircleHelp,
  Cloud,
  Download,
  Flag,
  GripVertical,
  Hand,
  KeyRound,
  Keyboard,
  LayoutDashboard,
  LayoutGrid,
  List,
  ListTodo,
  type LucideIcon,
  Pin,
  Plus,
  Pointer,
  RefreshCw,
  Repeat,
  Search,
  Sparkles,
  SquarePlus,
  WandSparkles,
  Waves,
  WifiOff,
} from "lucide-react";

// Maps the Lucide glyph names authored in the shared GuideCatalog to their
// components. A shared commonTest can assert every catalog icon exists here.
const ICONS: Record<string, LucideIcon> = {
  "alarm-clock": AlarmClock,
  bell: Bell,
  "bell-ring": BellRing,
  calendar: Calendar,
  car: Car,
  "car-front": CarFront,
  "check-check": CheckCheck,
  cloud: Cloud,
  download: Download,
  flag: Flag,
  "grip-vertical": GripVertical,
  hand: Hand,
  "key-round": KeyRound,
  keyboard: Keyboard,
  "layout-dashboard": LayoutDashboard,
  "layout-grid": LayoutGrid,
  list: List,
  "list-todo": ListTodo,
  pin: Pin,
  plus: Plus,
  pointer: Pointer,
  "refresh-cw": RefreshCw,
  repeat: Repeat,
  search: Search,
  sparkles: Sparkles,
  "square-plus": SquarePlus,
  "wand-sparkles": WandSparkles,
  waves: Waves,
  "wifi-off": WifiOff,
};

export function GuideIcon({
  name,
  className,
}: {
  name: string;
  className?: string;
}) {
  const Icon = ICONS[name] ?? CircleHelp;
  return <Icon className={className} aria-hidden="true" />;
}
