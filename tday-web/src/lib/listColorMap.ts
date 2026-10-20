import type { ListColor } from "@/types";

export const listColorMap: {
  name: string;
  value: ListColor;
  tailwind: string;
}[] = [
  { name: "Red", value: "RED", tailwind: "bg-accent-red" },
  { name: "Orange", value: "ORANGE", tailwind: "bg-accent-orange" },
  { name: "Yellow", value: "YELLOW", tailwind: "bg-accent-yellow" },
  { name: "Lime", value: "LIME", tailwind: "bg-accent-lime" },
  { name: "Blue", value: "BLUE", tailwind: "bg-accent-blue" },
  { name: "Purple", value: "PURPLE", tailwind: "bg-accent-purple" },
  { name: "Pink", value: "PINK", tailwind: "bg-accent-pink" },
  { name: "Teal", value: "TEAL", tailwind: "bg-accent-teal" },
  { name: "Coral", value: "CORAL", tailwind: "bg-accent-coral" },
  { name: "Gold", value: "GOLD", tailwind: "bg-accent-gold" },
  { name: "Deep Blue", value: "DEEP_BLUE", tailwind: "bg-accent-deep-blue" },
  { name: "Rose", value: "ROSE", tailwind: "bg-accent-rose" },
  { name: "Light Red", value: "LIGHT_RED", tailwind: "bg-accent-light-red" },
  { name: "Brick", value: "BRICK", tailwind: "bg-accent-brick" },
  { name: "Slate", value: "SLATE", tailwind: "bg-accent-slate" },
];

/**
 * The colour a new list starts with, and the one a list with no stored colour is drawn in, per
 * feed. The same pair as Android's `TDAY_DEFAULT_SCHEDULED_LIST_COLOR_KEY` /
 * `TDAY_DEFAULT_FLOATER_LIST_COLOR_KEY` and iOS's `tdayDefault*ListAccentColorKey`.
 *
 * Each is a soft cousin of its feed's identity colour: BLUE beside the Today tile's blue, TEAL
 * beside the Floater's sage/teal. Existing palette keys, not new ones — the colour is a Postgres
 * enum on the server, so a new key is a migration. Keys, so a list is the same colour everywhere.
 */
export const DEFAULT_SCHEDULED_LIST_COLOR: ListColor = "BLUE";
export const DEFAULT_FLOATER_LIST_COLOR: ListColor = "TEAL";

/** The palette entries behind the two defaults, for pickers that need the swatch itself. */
export const DEFAULT_SCHEDULED_LIST_COLOR_OPTION =
  listColorMap.find((option) => option.value === DEFAULT_SCHEDULED_LIST_COLOR) ?? listColorMap[0];
export const DEFAULT_FLOATER_LIST_COLOR_OPTION =
  listColorMap.find((option) => option.value === DEFAULT_FLOATER_LIST_COLOR) ?? listColorMap[0];
