type ClickableToastProps = {
  title: string;
  description?: string;
  onClick: () => void;
  variant?: "default" | "destructive";
};

export default function ClickableToast({
  title,
  description,
  onClick,
}: ClickableToastProps) {
  // Content only — no box of its own. The surrounding sonner <li> supplies the
  // frosted, fully-rounded pill (surface, border, padding, blur) and the 13px
  // base font size, so clickable toasts look identical to plain ones. The title
  // uses the same font-extrabold weight and centered alignment as a regular
  // toast's title — otherwise this custom toast (data-styled="false") renders
  // larger/lighter than the rest.
  return (
    <button
      type="button"
      onClick={onClick}
      className="block w-full min-w-0 text-center text-popover-foreground focus-visible:outline-none"
    >
      <span className="block font-extrabold leading-tight">{title}</span>
      {description && (
        <span className="mt-0.5 block text-[13px] font-medium leading-snug text-current/75">
          {description}
        </span>
      )}
    </button>
  );
}
