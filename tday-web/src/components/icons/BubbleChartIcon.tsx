import type { SVGProps } from "react";

/**
 * BubbleChart icon matching Android's `Icons.Rounded.BubbleChart`.
 * Three overlapping circles of different sizes — used as the floater
 * tab icon in the root dock and as the floater page background watermark.
 *
 * Accepts the same props as a lucide icon (<svg> element) so it can be
 * used interchangeably with lucide components.
 */
export default function BubbleChartIcon(props: SVGProps<SVGSVGElement>) {
  const {
    width = 24,
    height = 24,
    strokeWidth,
    className,
    ...rest
  } = props;

  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={width}
      height={height}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth ?? 1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      {...rest}
    >
      {/* Large circle — bottom-left */}
      <circle cx="8" cy="14" r="6" />
      {/* Medium circle — top-right */}
      <circle cx="17" cy="8" r="4.5" />
      {/* Small circle — top-center-left */}
      <circle cx="12" cy="4" r="2.5" />
    </svg>
  );
}
