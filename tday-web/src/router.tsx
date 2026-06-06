import { createBrowserRouter, Navigate, Outlet } from "react-router-dom";
import * as Sentry from "@sentry/react";
import { resolveInitialLocale } from "@/i18n";
import ProtectedRoute from "@/pages/ProtectedRoute";
import { lazy, Suspense } from "react";
import RouteErrorPage from "@/pages/RouteErrorPage";
import AppShellSkeleton from "@/components/app/AppShellSkeleton";

const LandingPage = lazy(() => import("@/pages/LandingPage"));
const LoginPage = lazy(() => import("@/pages/LoginPage"));
const RegisterPage = lazy(() => import("@/pages/RegisterPage"));
const ForgotPasswordPage = lazy(() => import("@/pages/ForgotPasswordPage"));
const PrivacyPage = lazy(() => import("@/pages/PrivacyPage"));
const TermsPage = lazy(() => import("@/pages/TermsPage"));
const BlogsPage = lazy(() => import("@/pages/BlogsPage"));
const BlogArticlePage = lazy(() => import("@/pages/BlogArticlePage"));
const NotFoundPage = lazy(() => import("@/pages/NotFoundPage"));
const AppLayout = lazy(() => import("@/pages/AppLayout"));
const GeneralLayout = lazy(() => import("@/pages/GeneralLayout"));
const CalendarLayout = lazy(() => import("@/pages/CalendarLayout"));
const TodayPage = lazy(() => import("@/pages/TodayPage"));
const TodayTasksPage = lazy(() => import("@/pages/TodayTasksPage"));
const OverduePage = lazy(() => import("@/pages/OverduePage"));
const TodoPage = lazy(() => import("@/pages/TodoPage"));
const PriorityPage = lazy(() => import("@/pages/PriorityPage"));
const ScheduledPage = lazy(() => import("@/pages/ScheduledPage"));
const CompletedPage = lazy(() => import("@/pages/CompletedPage"));
const FloaterPage = lazy(() => import("@/pages/FloaterPage"));
const FloaterListPage = lazy(() => import("@/pages/FloaterListPage"));
const AdminPage = lazy(() => import("@/pages/AdminPage"));
const AdminRoute = lazy(() => import("@/pages/AdminRoute"));
const SettingsPage = lazy(() => import("@/pages/SettingsPage"));
const VersionPage = lazy(() => import("@/pages/VersionPage"));
const VersionRedirectPage = lazy(() => import("@/pages/VersionRedirectPage"));
const ListPage = lazy(() => import("@/pages/ListPage"));
const CalendarPage = lazy(() => import("@/pages/CalendarPage"));
const AuthLayout = lazy(() => import("@/pages/AuthLayout"));

function LazyFallback() {
  return <AppShellSkeleton />;
}

function SuspenseOutlet() {
  return (
    <Suspense fallback={<LazyFallback />}>
      <Outlet />
    </Suspense>
  );
}

const sentryCreateBrowserRouter =
  Sentry.wrapCreateBrowserRouterV7(createBrowserRouter);

export const router = sentryCreateBrowserRouter([
  {
    path: "/",
    element: <Navigate to={`/${resolveInitialLocale()}`} replace />,
    errorElement: <RouteErrorPage />,
  },
  {
    path: `/:locale`,
    element: <SuspenseOutlet />,
    errorElement: <RouteErrorPage />,
    children: [
      { index: true, element: <LandingPage /> },
      {
        element: <AuthLayout />,
        children: [
          { path: "login", element: <LoginPage /> },
          { path: "register", element: <RegisterPage /> },
          { path: "forgot-password", element: <ForgotPasswordPage /> },
        ],
      },
      { path: "privacy", element: <PrivacyPage /> },
      { path: "terms", element: <TermsPage /> },
      { path: "blogs", element: <BlogsPage /> },
      { path: "blogs/page/:slug", element: <BlogArticlePage /> },
      {
        path: "app",
        element: <ProtectedRoute />,
        errorElement: <RouteErrorPage />,
        children: [
          {
            element: <AppLayout />,
            children: [
              {
                element: <GeneralLayout />,
                children: [
                  { path: "tday", element: <TodayPage /> },
                  { path: "today", element: <TodayTasksPage /> },
                  { path: "overdue", element: <OverduePage /> },
                  { path: "todo", element: <TodoPage /> },
                  { path: "priority", element: <PriorityPage /> },
                  { path: "scheduled", element: <ScheduledPage /> },
                  { path: "completed", element: <CompletedPage /> },
                  { path: "floater", element: <FloaterPage /> },
                  { path: "floater-list/:id", element: <FloaterListPage /> },
                  { path: "settings", element: <SettingsPage /> },
                  {
                    element: <AdminRoute />,
                    children: [
                      { path: "admin", element: <AdminPage /> },
                      { path: "admin/version", element: <VersionPage /> },
                    ],
                  },
                  {
                    path: "version",
                    element: <VersionRedirectPage />,
                  },
                  { path: "list/:id", element: <ListPage /> },
                ],
              },
              {
                element: <CalendarLayout />,
                children: [
                  { path: "calendar", element: <CalendarPage /> },
                ],
              },
            ],
          },
        ],
      },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
