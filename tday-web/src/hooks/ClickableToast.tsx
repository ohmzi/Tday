type ClickableToastAction = {
  label: string;
  onClick: () => void;
};

type ClickableToastProps = {
  title: string;
  description?: string;
  onClick: () => void;
  variant?: "default" | "destructive";
  action?: ClickableToastAction;
};

export default function ClickableToast({
  title,
  description,
  onClick,
  action,
}: ClickableToastProps) {
  // Content only — no box of its own. The surrounding sonner <li> supplies the
  // frosted, fully-rounded pill (surface, border, padding, blur) and the 13px
  // base font size, so clickable toasts look identical to plain ones. The title
  // uses the same font-extrabold weight and centered alignment as a regular
  // toast's title — otherwise this custom toast (data-styled="false") renders
  // larger/lighter than the rest. The optional action (e.g. Undo) is a sibling
  // button so the tap-through body and the action stay separate targets.
  return (
    <div className="flex w-full min-w-0 items-center gap-3">
      <button
        type="button"
        onClick={onClick}
        className="block min-w-0 flex-1 text-center text-popover-foreground focus-visible:outline-none"
      >
        <span className="block font-extrabold leading-tight">{title}</span>
        {description && (
          <span className="mt-0.5 block text-[13px] font-medium leading-snug text-current/75">
            {description}
          </span>
        )}
      </button>
      {action ? (
        <button
          type="button"
          onClick={action.onClick}
          className="shrink-0 rounded-full bg-primary px-3 py-1.5 text-[13px] font-bold text-primary-foreground focus-visible:outline-none"
        >
          {action.label}
        </button>
      ) : null}
    </div>
  );
}
