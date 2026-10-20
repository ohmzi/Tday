import { useLayoutEffect } from "react";
import { Outlet } from "react-router-dom";
import {
  NativeAppPageLayout,
  nativeAppScrollAttribute,
} from "@/components/app/nativeAppLayout";
import { usePathname } from "@/lib/navigation";
import { scrollTo } from "@/lib/scroll";

export default function GeneralLayout() {
  const pathname = usePathname();

  // Every route under here shares one scroll container — React Router keeps this
  // layout mounted across sibling routes, so without this the previous screen's
  // scroll position carries over and a tab switch lands you mid-feed.
  // "auto", not "smooth": this is a screen change, not a gesture, and a smooth
  // scroll would visibly slide the screen you just opened.
  useLayoutEffect(() => {
    scrollTo(document.querySelector<HTMLElement>(`[${nativeAppScrollAttribute}]`), {
      top: 0,
      behavior: "auto",
    });
  }, [pathname]);

  return (
    <NativeAppPageLayout>
      <Outlet />
    </NativeAppPageLayout>
  );
}
