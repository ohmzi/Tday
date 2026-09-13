import { Skeleton } from "@/components/ui/skeleton";
import { Modal, ModalContent, ModalFooter, ModalHeader, ModalOverlay } from "@/components/ui/Modal";

/**
 * The confirm dialog, drawn while the chunk that holds the real one is still in
 * flight.
 *
 * The two delete dialogs are `lazy()`, and their Suspense boundaries answered
 * with `null` — so a Delete tap made before the chunk lands puts nothing at all
 * on screen. Nothing is the same answer the app gives to a tap it never
 * received. The window is narrow (the boundary renders unconditionally, so the
 * import starts when the row mounts rather than when the button is pressed) but
 * it is exactly the window where an unanswered tap costs most: a cold cache or
 * a bad connection, where the user's next move is to press it again.
 *
 * Not `ModalPlaceholder` beside this file, which stands in for the edit form —
 * six fields and a notes box. A confirm dialog is a line, a sentence and two
 * buttons, and a placeholder of the wrong shape would resize the card under the
 * content it is waiting for.
 *
 * Built from the Modal primitives rather than a portal of its own so that the
 * scrim, the card, the enter and the click-to-dismiss are the same ones the
 * real dialog arrives with; the only thing this file decides is what fills the
 * card. It is dismissible for that reason too — a tap that can be answered can
 * also be taken back, and a scrim that swallows both while a chunk downloads
 * would be a worse answer than none.
 */
export default function ConfirmPlaceholder({ onCancel }: { onCancel: () => void }) {
  return (
    <Modal open onOpenChange={(next) => { if (!next) onCancel(); }}>
      <ModalOverlay>
        <ModalContent>
          {/* No copy, because there is none to give: the strings the real
              dialog reads are in the chunk being waited on. `aria-busy` is what
              a screen reader can be told without inventing a sentence. */}
          <div aria-busy="true">
            <ModalHeader>
              {/* The title's line and the description's, at the heights the
                  real ones occupy (`text-lg` over `text-sm`), so the card the
                  chunk lands into is the card already on screen. */}
              <Skeleton className="h-5 w-2/5" />
              <Skeleton className="h-4 w-4/5" />
            </ModalHeader>

            <ModalFooter className="mt-4">
              <Skeleton className="h-10 w-full sm:w-24" />
              <Skeleton className="h-10 w-full sm:w-24" />
            </ModalFooter>
          </div>
        </ModalContent>
      </ModalOverlay>
    </Modal>
  );
}
