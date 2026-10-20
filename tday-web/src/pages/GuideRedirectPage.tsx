import { Navigate, useParams } from "react-router-dom";
import { DEFAULT_LOCALE } from "@/i18n";

/**
 * The guide used to live at `/:locale/guide` outside the app shell. It now
 * renders inside the app (dock, brand bar, gutters), so old links and any
 * bookmarked topic deep-links land on the in-app route.
 */
export default function GuideRedirectPage() {
  const { locale, topicId } = useParams();
  const loc = locale || DEFAULT_LOCALE;
  const suffix = topicId ? `/${topicId}` : "";

  return <Navigate to={`/${loc}/app/guide${suffix}`} replace />;
}
