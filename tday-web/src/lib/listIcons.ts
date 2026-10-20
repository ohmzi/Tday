import {
  Activity,
  Archive,
  Baby,
  Backpack,
  Book,
  Bookmark,
  BriefcaseBusiness,
  BriefcaseMedical,
  Building2,
  Cake,
  Calendar,
  Camera,
  Car,
  Check,
  Circle,
  CircleDollarSign,
  Clock,
  Code,
  Droplet,
  Dumbbell,
  FileText,
  Flag,
  Flame,
  Gamepad2,
  Gift,
  GraduationCap,
  Hammer,
  Headphones,
  Heart,
  House,
  Inbox,
  Key,
  Landmark,
  Leaf,
  Lightbulb,
  List,
  type LucideIcon,
  MessageCircle,
  Monitor,
  Music,
  Palette,
  PawPrint,
  Pencil,
  Plane,
  Scissors,
  Ship,
  ShoppingBag,
  ShoppingBasket,
  ShoppingCart,
  Smile,
  Snowflake,
  Square,
  Star,
  Sun,
  Train,
  Triangle,
  TriangleAlert,
  Umbrella,
  UsersRound,
  Utensils,
  WalletCards,
  Wine,
} from "lucide-react";
import { inferListIconKey } from "@/lib/listIconInference";

export const DEFAULT_LIST_ICON_KEY = "inbox";

export type ListIconOption = {
  key: string;
  label: string;
  icon: LucideIcon;
};

export const listIconOptions: ListIconOption[] = [
  { key: DEFAULT_LIST_ICON_KEY, label: "Inbox", icon: Inbox },
  { key: "sun", label: "Sun", icon: Sun },
  { key: "calendar", label: "Calendar", icon: Calendar },
  { key: "schedule", label: "Schedule", icon: Clock },
  { key: "flag", label: "Flag", icon: Flag },
  { key: "check", label: "Check", icon: Check },
  { key: "smile", label: "Smile", icon: Smile },
  { key: "list", label: "List", icon: List },
  { key: "bookmark", label: "Bookmark", icon: Bookmark },
  { key: "key", label: "Key", icon: Key },
  { key: "gift", label: "Gift", icon: Gift },
  { key: "cake", label: "Cake", icon: Cake },
  { key: "school", label: "School", icon: GraduationCap },
  { key: "bag", label: "Bag", icon: Backpack },
  { key: "edit", label: "Edit", icon: Pencil },
  { key: "document", label: "Document", icon: FileText },
  { key: "book", label: "Book", icon: Book },
  { key: "work", label: "Work", icon: BriefcaseBusiness },
  { key: "wallet", label: "Wallet", icon: WalletCards },
  { key: "money", label: "Money", icon: CircleDollarSign },
  { key: "fitness", label: "Fitness", icon: Dumbbell },
  { key: "run", label: "Run", icon: Activity },
  { key: "food", label: "Food", icon: Utensils },
  { key: "drink", label: "Drink", icon: Wine },
  { key: "health", label: "Health", icon: BriefcaseMedical },
  { key: "monitor", label: "Monitor", icon: Monitor },
  { key: "music", label: "Music", icon: Music },
  { key: "computer", label: "Computer", icon: Monitor },
  { key: "game", label: "Game", icon: Gamepad2 },
  { key: "headphones", label: "Headphones", icon: Headphones },
  { key: "eco", label: "Eco", icon: Leaf },
  { key: "pets", label: "Pets", icon: PawPrint },
  { key: "child", label: "Child", icon: Baby },
  { key: "family", label: "Family", icon: UsersRound },
  { key: "basket", label: "Basket", icon: ShoppingBasket },
  { key: "cart", label: "Cart", icon: ShoppingCart },
  { key: "mall", label: "Mall", icon: ShoppingBag },
  { key: "inventory", label: "Inventory", icon: Archive },
  { key: "soccer", label: "Soccer", icon: Circle },
  { key: "baseball", label: "Baseball", icon: Circle },
  { key: "basketball", label: "Basketball", icon: Circle },
  { key: "football", label: "Football", icon: Circle },
  { key: "tennis", label: "Tennis", icon: Circle },
  { key: "train", label: "Train", icon: Train },
  { key: "flight", label: "Flight", icon: Plane },
  { key: "boat", label: "Boat", icon: Ship },
  { key: "car", label: "Car", icon: Car },
  { key: "umbrella", label: "Umbrella", icon: Umbrella },
  { key: "drop", label: "Drop", icon: Droplet },
  { key: "snow", label: "Snow", icon: Snowflake },
  { key: "fire", label: "Fire", icon: Flame },
  { key: "tools", label: "Tools", icon: Hammer },
  { key: "scissors", label: "Scissors", icon: Scissors },
  { key: "architecture", label: "Architecture", icon: Landmark },
  { key: "code", label: "Code", icon: Code },
  { key: "idea", label: "Idea", icon: Lightbulb },
  { key: "chat", label: "Chat", icon: MessageCircle },
  { key: "alert", label: "Alert", icon: TriangleAlert },
  { key: "star", label: "Star", icon: Star },
  { key: "heart", label: "Heart", icon: Heart },
  { key: "circle", label: "Circle", icon: Circle },
  { key: "square", label: "Square", icon: Square },
  { key: "triangle", label: "Triangle", icon: Triangle },
  { key: "home", label: "Home", icon: House },
  { key: "city", label: "City", icon: Building2 },
  { key: "bank", label: "Bank", icon: Landmark },
  { key: "camera", label: "Camera", icon: Camera },
  { key: "palette", label: "Palette", icon: Palette },
];

const listIconMap = new Map(listIconOptions.map((option) => [option.key, option.icon]));

export function normalizeListIconKey(iconKey?: string | null) {
  const candidate = iconKey?.trim().toLowerCase();
  if (!candidate) return DEFAULT_LIST_ICON_KEY;

  const normalized =
    candidate === "briefcase"
      ? "work"
      : candidate === "cocktail"
        ? "drink"
        : candidate === "travel"
          ? "flight"
          : candidate;

  return listIconMap.has(normalized) ? normalized : DEFAULT_LIST_ICON_KEY;
}

export function getListIcon(iconKey?: string | null) {
  return listIconMap.get(normalizeListIconKey(iconKey)) ?? Inbox;
}

/** The two fields every display site has, whatever shape of list meta it holds. */
type ListIconSource = { iconKey?: string | null; name?: string | null } | null | undefined;

/**
 * The key a list should actually be DRAWN with: the one its owner chose, or — only
 * when they never chose — one inferred from its name.
 *
 * The precedence is the whole feature, so it is worth spelling out why it is an `??`
 * and not a merge. A stored `iconKey` always wins, INCLUDING when it is `"inbox"`:
 * since the create sheets stopped posting the picker's preview, that value means "the
 * user chose the plain one", and inferring over it would be the app arguing with a
 * decision it can see. `null` is the only value that means "never chosen", which is
 * why the sheets had to be fixed before any of this could be honest.
 *
 * DISPLAY-ONLY, and that is load-bearing rather than tidy. Nothing here writes back
 * through a list mutation or into the local workspace: a guess that persists stops
 * being a guess, the user's "unset" is gone, and the next rename inherits an icon
 * chosen for the old name by nobody.
 *
 * `normalizeListIconKey` still has the last word, so an inferred key the registry has
 * somehow lost falls back to the default exactly like a corrupt stored one. That is a
 * silent fallback — which is why `list-icon-inference-parity.test.ts` asserts every
 * emittable key is in `listIconOptions`, rather than trusting this line to complain.
 */
export function resolveListIconKey(list: ListIconSource) {
  const chosen = list?.iconKey?.trim();
  if (chosen) return normalizeListIconKey(chosen);
  return normalizeListIconKey(inferListIconKey(list?.name));
}

/** `getListIcon` for a whole list, so the inference reaches every display site. */
export function getListIconForList(list: ListIconSource) {
  return getListIcon(resolveListIconKey(list));
}
