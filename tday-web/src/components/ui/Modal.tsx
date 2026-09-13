import { cn } from '@/lib/utils';
import { useFadeUnmount } from '@/hooks/useFadeUnmount';
import React, { createContext, useContext, useState } from 'react'
import { createPortal } from 'react-dom'

/**
 * How long the modal's exit is given to play. Read twice on purpose: once by the CSS
 * (`data-[state=closed]:duration-200` below) and once by `useModalPresence`, which is what
 * actually keeps the portal in the DOM for that long. Two numbers tuned to look alike is how
 * an exit ends up half-played; one number read twice cannot drift.
 */
export const MODAL_EXIT_MS = 200;

/**
 * Whether the modal's subtree should still be rendered right now — true while it is open, and
 * for `MODAL_EXIT_MS` after it closes so the exit has frames to run in.
 *
 * Exported because the guard has to be made at whichever component owns the decision to render
 * the modal at all. `ModalOverlay` uses it for its own portal, but a caller that wraps the whole
 * modal in `if (!open) return null` or in `{open && <Dialog …/>}` takes the subtree away one
 * level higher up, and no amount of care inside here can survive that. Those callers call this
 * instead of testing the flag directly.
 */
export function useModalPresence(open: boolean, durationMs: number = MODAL_EXIT_MS): boolean {
    return useFadeUnmount(open, durationMs);
}

const ModalContext = createContext<{
    isOpen: boolean;
    setIsOpen: (open: boolean) => void;
} | null>(null);


const Modal = ({
    open: controlledOpen,
    onOpenChange,
    children
}: {
    open?: boolean,
    onOpenChange?: (open: boolean) => void,
    children: React.ReactNode
}) => {
    const [uncontrolledOpen, setUncontrolledOpen] = useState(false);

    // 2. Determine if we are controlled or not
    const isControlled = controlledOpen !== undefined;
    const isOpen = isControlled ? controlledOpen : uncontrolledOpen;

    // 3. Unified change handler
    const handleOpenChange = (value: boolean) => {
        if (!isControlled) {
            setUncontrolledOpen(value);
        }
        onOpenChange?.(value);
    };

    return (
        <ModalContext.Provider value={{ isOpen, setIsOpen: handleOpenChange }}>
            {children}
        </ModalContext.Provider>
    );
};

const ModalClose = ({
    children,
    className
}: {
    children: React.ReactNode,
    className?: string
}) => {
    const context = useContext(ModalContext);

    if (!context) {
        throw new Error("ModalClose must be used within a Modal");
    }

    const { setIsOpen } = context;

    return (
        <div
            className={cn("cursor-pointer w-fit", className)}
            onClick={() => setIsOpen(false)}
        >
            {children}
        </div>
    );
};



const ModalOverlay = ({ children }: { children: React.ReactElement }) => {

    const context = useContext(ModalContext);

    // `present` outlives `isOpen` by MODAL_EXIT_MS. This used to be `if (!isOpen) return null`,
    // which took the portal out of the document on the same frame the flag flipped — so the
    // scrim and the card were simply gone, and any exit declared on them was unreachable
    // regardless of what it said. `data-state` is what the exit is keyed to, and it reads
    // `isOpen`, not `present`, so a modal reopened mid-exit goes straight back to entering.
    // Called above the context guard so the hook order never depends on the throw.
    const present = useModalPresence(context?.isOpen ?? false);

    if (!context) throw new Error("ModalOverlay must be used within a Modal");
    const { isOpen, setIsOpen } = context;

    if (!present) return null;
    return createPortal(

        <div
            data-state={isOpen ? "open" : "closed"}
            className="fixed inset-0 z-50 bg-black/65 flex items-center justify-center data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:duration-200 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:duration-200"
            onMouseDown={(e) => e.stopPropagation()}
            onPointerDown={(e) => e.stopPropagation()}
            onTouchStart={(e) => e.stopPropagation()}
            onClick={(e) => {
                if (e.target === e.currentTarget) {
                    e.preventDefault();
                    e.stopPropagation();
                    // defer close to avoid click-through
                    requestAnimationFrame(() => setIsOpen(false));
                }
            }}
        >
            {children}
        </div>
        , document.body
    )

}

const ModalContent = ({ children, className }: { children: React.ReactNode, className?: string }) => {
    // Outside a Modal there is no close to animate, so the card is simply always "open".
    const isOpen = useContext(ModalContext)?.isOpen ?? true;
    return (
        <div
            data-state={isOpen ? "open" : "closed"}
            className={cn(
                "bg-background rounded-lg w-full max-w-lg p-6",
                "data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95 data-[state=open]:slide-in-from-bottom-8 data-[state=open]:duration-200",
                "data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95 data-[state=closed]:slide-out-to-bottom-8 data-[state=closed]:duration-200",
                className,
            )}
            onMouseDown={(e) => e.stopPropagation()}
            onPointerDown={(e) => e.stopPropagation()}
            onTouchStart={(e) => e.stopPropagation()}
        >
            {children}
        </div>
    )
}


const ModalHeader = ({ children, className = "" }: { children: React.ReactNode, className?: string }) => (
    <div className={`flex flex-col space-y-1.5 text-center sm:text-left ${className}`}>
        {children}
    </div>
);

const ModalTitle = ({ children, className = "" }: { children: React.ReactNode, className?: string }) => (
    <h2 className={`text-lg font-semibold leading-none tracking-tight ${className}`}>
        {children}
    </h2>
);

const ModalDescription = ({ children, className = "" }: { children: React.ReactNode, className?: string }) => (
    <p className={`text-sm text-muted-foreground ${className}`}>
        {children}
    </p>
);

const ModalFooter = ({ children, className = "" }: { children: React.ReactNode, className?: string }) => (
    <div className={`flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2 gap-2 mt-6 ${className}`} >
        {children}
    </div>
);

export { Modal, ModalContent, ModalDescription, ModalFooter, ModalHeader, ModalOverlay, ModalTitle, ModalClose }

