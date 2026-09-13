import { useTranslation } from "react-i18next";
import { Skeleton } from "@/components/ui/skeleton";
import {
  SheetCard,
  SheetDivider,
  SheetRow,
  SheetSectionTitle,
} from "@/components/ui/sheet-chrome";

/**
 * The calendar task form's body, drawn while the chunk that holds the real one
 * is still in flight.
 *
 * It is only the body. The two files it replaced — `DrawerPlaceholder` and the
 * never-imported `ModalPlaceholder` — each drew a whole surface: their own
 * portal, their own scrim, a card pinned at the geometry the real sheet ends
 * at. That is the shape of the bug they caused. A placeholder that is already
 * the arrived sheet has nothing left to hand over, so the chunk landing could
 * only take it away and let vaul slide an identical sheet into the same place:
 * one tap, two arrivals. Here the sheet itself is eager (see
 * `LazyCalendarTaskFormBody`), arrives once, and this fills it in the meantime.
 *
 * Built from the same `sheet-chrome` pieces the real body uses, for the reason
 * `ConfirmPlaceholder` gives next door: the cards, their radii and their row
 * heights should be the ones the content lands into, not a second set tuned to
 * look like them. The only thing this file decides is what fills them.
 *
 * The section titles are real strings rather than blocks, and that is not the
 * call `ConfirmPlaceholder` made. Its copy lived in the chunk it was waiting
 * on; "Schedule" and "Details" are read from the eagerly-loaded `app`
 * dictionary by this component and by the body alike, so drawing them now is
 * not inventing a sentence — it is drawing the part of the body that is already
 * known, which is two fewer things that change at the handover.
 */
export default function FormBodyPlaceholder() {
  const { t: appDict } = useTranslation("app");

  return (
    // No copy for the fields themselves, so `aria-busy` is what a screen reader
    // can be told without narrating labels the user cannot yet act on.
    <div className="flex flex-col gap-3" aria-busy="true">
      {/* Title + Notes */}
      <SheetCard>
        <div className="px-[18px] pb-2 pt-3">
          {/* The title line at `text-lg`'s height, the notes block at the two
              lines the editor opens with. */}
          <Skeleton className="h-7 w-3/5" />
        </div>
        <SheetDivider />
        <div className="flex flex-col gap-2 px-[18px] py-3">
          <Skeleton className="h-4 w-4/5" />
          <Skeleton className="h-4 w-2/5" />
        </div>
      </SheetCard>

      {/* Schedule */}
      <SheetSectionTitle>{appDict("schedule")}</SheetSectionTitle>
      <SheetCard>
        <PlaceholderRow valueClassName="w-32" />
      </SheetCard>

      {/* Details */}
      <SheetSectionTitle>{appDict("details")}</SheetSectionTitle>
      <SheetCard>
        <PlaceholderRow valueClassName="w-20" />
        <SheetDivider />
        <PlaceholderRow valueClassName="w-16" />
        <SheetDivider />
        <PlaceholderRow valueClassName="w-24" />
      </SheetCard>
    </div>
  );
}

/**
 * One selector row's worth of blocks, laid out by `SheetRow` itself so the
 * 60 px floor and the icon/label/value columns are the real ones rather than a
 * copy of their class strings.
 */
function PlaceholderRow({ valueClassName }: { valueClassName: string }) {
  return (
    <SheetRow
      icon={<Skeleton className="h-[22px] w-[22px] rounded-full" />}
      label={<Skeleton className="h-5 w-20" />}
    >
      <Skeleton className={`h-5 ${valueClassName}`} />
    </SheetRow>
  );
}
