import React from "react";
import { Eye, EyeOff } from "lucide-react";
import clsx from "clsx";
import { cn } from "@/lib/utils";
const EyeToggle = ({
  show,
  setShow,
  className,
}: {
  show: boolean;
  setShow: React.Dispatch<React.SetStateAction<boolean>>;
  className?: string;
}) => {
  return (
    <>
      <button
        type="button"
        aria-label="show-password"
        className={cn(
          clsx(
            "absolute right-[16px] top-1/2 -translate-y-1/2  stroke-form-label hover:stroke-form-label-accent hover:cursor-pointer",
            !show && "hidden"
          ),
          className
        )}
        onClick={() => {
          setShow(!show);
        }}
      >
        <Eye className="stroke-form-label hover:stroke-form-label-accent transition-all duration-300" />
      </button>

      <button
        aria-label="hide-password"
        type="button"
        className={cn(
          clsx(
            "absolute right-[16px] top-1/2 -translate-y-1/2  stroke-form-label hover:stroke-form-label-accent hover:cursor-pointer",
            show && "hidden"
          ),
          className
        )}
        onClick={() => {
          setShow(!show);
        }}
      >
        <EyeOff className="stroke-form-label hover:stroke-form-label-accent transition-all duration-300" />
      </button>
    </>
  );
};

export default EyeToggle;
