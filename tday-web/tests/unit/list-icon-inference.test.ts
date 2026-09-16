import { describe, expect, it } from "vitest";
import { Inbox, ShoppingCart } from "lucide-react";
import { inferListIconKey } from "@/lib/listIconInference";
import {
  DEFAULT_LIST_ICON_KEY,
  getListIconForList,
  resolveListIconKey,
} from "@/lib/listIcons";

/**
 * The inference table, pinned as a table.
 *
 * There is no browser here and no way to look at a rendered glyph, so "does the icon
 * read as right" is not a claim this file can make. What it CAN make is the two claims
 * the feature actually rests on: the matcher emits the key we meant for the titles we
 * meant, and — the half that earns its keep — it emits NOTHING for the titles that
 * would fool a lazier matcher. The must-not-match block is the whole argument for
 * whole-word lookup; delete it and the next person can swap in `title.includes(word)`
 * with every other test here still green.
 *
 * The cases are transcribed from the shared `ListIconInferenceTest.kt`, deliberately
 * question for question. The WORDS are generated from one Kotlin table so they cannot
 * fork, but each client hand-writes the fifteen-line rule that uses them, and three
 * rules answering the same questions differently is the failure that codegen alone
 * cannot catch. `tests/guardrails/list-icon-inference-parity.test.ts` guards the table;
 * this file guards the rule.
 */
describe("inferListIconKey", () => {
  it("infers the glyph a title's subject evidences", () => {
    const expected: Record<string, string> = {
      Groceries: "cart",
      "Grocery Shopping": "cart",
      Work: "work",
      Gym: "fitness",
      Reading: "book",
      "Reading List": "book",
      Travel: "flight",
      Recipes: "food",
      Budget: "money",
      Home: "home",
      Passwords: "key",
      Dog: "pets",
      Birthday: "cake",
    };

    const actual = Object.fromEntries(
      Object.keys(expected).map((title) => [title, inferListIconKey(title)]),
    );
    expect(actual).toEqual(expected);
  });

  it("reads case, padding, punctuation and emoji as noise", () => {
    // The same list, typed five ways by five people, is one list.
    for (const title of [
      "groceries",
      "GROCERIES",
      "  Groceries  ",
      "Groceries!!!",
      "Groceries 🛒",
    ]) {
      expect(inferListIconKey(title), title).toBe("cart");
    }
  });

  it("does not see a keyword buried inside another word", () => {
    // Each of these CONTAINS a table keyword and is about something else: "Carpentry"
    // holds `car`, "Firewood" holds `fire`, "Scarlett" holds `car`, "Homework" holds
    // `home`, "Barcelona" holds `bar`. A cart on a carpentry list is worse than the
    // inbox it replaced, which is the rule about being unsure.
    for (const title of [
      "Carpentry",
      "Firewood",
      "Scarlett",
      "Homework",
      "Barcelona",
      "Workaround",
      "Bookkeeping",
    ]) {
      expect(inferListIconKey(title), title).toBeNull();
    }
  });

  it("says nothing about a title with no subject in it", () => {
    for (const title of ["", "   ", "!!!", "---", "2026", "Misc", "Stuff", "Untitled", "Q3"]) {
      expect(inferListIconKey(title), title).toBeNull();
    }
    expect(inferListIconKey(null)).toBeNull();
    expect(inferListIconKey(undefined)).toBeNull();
  });

  it("refuses a title that evidences two different glyphs", () => {
    // Equal warrant for two glyphs is not a reason to take the leftmost.
    for (const title of ["Work Travel", "Gym and Groceries", "Dog Food", "Music Books"]) {
      expect(inferListIconKey(title), title).toBeNull();
    }
  });

  it("still answers when two words point at the same glyph", () => {
    expect(inferListIconKey("Shopping Errands")).toBe("cart");
    expect(inferListIconKey("Gym Workout")).toBe("fitness");
  });

  it("never emits the default key", () => {
    // `normalizeListIconKey` answers "inbox" for null, blank and unknown alike and
    // never says which it was, so "I inferred inbox" and "I have nothing" would reach
    // the render site as one value — and the never-override rule is built on the
    // caller being able to tell them apart.
    expect(inferListIconKey("Inbox")).toBeNull();
  });
});

/**
 * Which key a list is DRAWN with — the half of the feature that decides whether a
 * user's choice survives.
 *
 * Worth its own block because every other guarantee here is defeated by getting this
 * precedence wrong in either direction: inferring over a chosen icon is the app
 * arguing with a decision it can see, and refusing to infer for an unchosen one is the
 * feature not existing.
 */
describe("resolveListIconKey", () => {
  it("infers only when nothing was chosen", () => {
    expect(resolveListIconKey({ name: "Groceries", iconKey: null })).toBe("cart");
    expect(resolveListIconKey({ name: "Groceries" })).toBe("cart");
    // Blank and whitespace are storage noise, not a choice — a list created before the
    // sheets stopped posting the preview can hold either.
    expect(resolveListIconKey({ name: "Groceries", iconKey: "" })).toBe("cart");
    expect(resolveListIconKey({ name: "Groceries", iconKey: "   " })).toBe("cart");
  });

  it("keeps a chosen icon, including the default one", () => {
    expect(resolveListIconKey({ name: "Groceries", iconKey: "work" })).toBe("work");
    // THE case the whole data-model change exists for. "inbox" stored is a user who
    // tapped Inbox; inferring a cart over it would be the guess beating the choice.
    expect(resolveListIconKey({ name: "Groceries", iconKey: DEFAULT_LIST_ICON_KEY })).toBe(
      DEFAULT_LIST_ICON_KEY,
    );
    // Legacy aliases are still a choice, and still normalise.
    expect(resolveListIconKey({ name: "Groceries", iconKey: "briefcase" })).toBe("work");
  });

  it("falls back to the default when the name evidences nothing", () => {
    expect(resolveListIconKey({ name: "Misc" })).toBe(DEFAULT_LIST_ICON_KEY);
    expect(resolveListIconKey({ name: "" })).toBe(DEFAULT_LIST_ICON_KEY);
    expect(resolveListIconKey(null)).toBe(DEFAULT_LIST_ICON_KEY);
    expect(resolveListIconKey(undefined)).toBe(DEFAULT_LIST_ICON_KEY);
  });

  it("hands the display sites a real component for both answers", () => {
    // Every display site calls `getListIconForList`, so the key mapping to a drawable
    // component is the last link in the chain — and it fails silently (an unknown key
    // paints an inbox), which is exactly why it is asserted rather than assumed.
    expect(getListIconForList({ name: "Groceries" })).toBe(ShoppingCart);
    expect(getListIconForList({ name: "Misc" })).toBe(Inbox);
  });
});
