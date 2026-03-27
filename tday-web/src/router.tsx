import { createBrowserRouter, Navigate, Outlet } from "react-router-dom";
import { DEFAULT_LOCALE } from "@/i18n";
import ProtectedRoute from "@/pages/ProtectedRoute";
import { lazy, Suspense } from "react";
import { Loader2 } from "lucide-react";

const LandingPage = lazy(() => import("@/pages/LandingPage"));
const LoginPage = lazy(() => import("@/pages/LoginPage"));
const RegisterPage = lazy(() => import("@/pages/RegisterPage"));
const PrivacyPage = lazy(() => import("@/pages/PrivacyPage"));
const TermsPage = lazy(() => import("@/pages/TermsPage"));
const BlogsPage = lazy(() => import("@/pages/BlogsPage"));
const BlogArticlePage = lazy(() => import("@/pages/BlogArticlePage"));
const NotFoundPage = lazy(() => import("@/pages/NotFoundPage"));
const AppLayout = lazy(() => import("@/pages/AppLayout"));
const GeneralLayout = lazy(() => import("@/pages/GeneralLayout"));
const CalendarLayout = lazy(() => import("@/pages/CalendarLayout"));
const TodayPage = lazy(() => import("@/pages/TodayPage"));
const TodoPage = lazy(() => import("@/pages/TodoPage"));
const PriorityPage = lazy(() => import("@/pages/PriorityPage"));
const ScheduledPage = lazy(() => import("@/pages/ScheduledPage"));
const CompletedPage = lazy(() => import("@/pages/CompletedPage"));
const AddTaskPage = lazy(() => import("@/pages/AddTaskPage"));
const AdminPage = lazy(() => import("@/pages/AdminPage"));
const SettingsPage = lazy(() => import("@/pages/SettingsPage"));
const ListPage = lazy(() => import("@/pages/ListPage"));
const NotePage = lazy(() => import("@/pages/NotePage"));
const CalendarPage = lazy(() => import("@/pages/CalendarPage"));
const AuthLayout = lazy(() => import("@/pages/AuthLayout"));

function LazyFallback() {
  return (
    <div className="flex h-screen w-full items-center justify-center bg-background">
      <Loader2 className="h-8 w-8 animate-spin text-accent" />
    </div>
  );
}

function SuspenseOutlet() {
  return (
    <Suspense fallback={<LazyFallback />}>
      <Outlet />
    </Suspense>
  );
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <Navigate to={`/${DEFAULT_LOCALE}`} replace />,
  },
  {
    path: `/:locale`,
    element: <SuspenseOutlet />,
    children: [
      { index: true, element: <LandingPage /> },
      {
        element: <AuthLayout />,
        children: [
          { path: "login", element: <LoginPage /> },
          { path: "register", element: <RegisterPage /> },
        ],
      },
      { path: "privacy", element: <PrivacyPage /> },
      { path: "terms", element: <TermsPage /> },
      { path: "blogs", element: <BlogsPage /> },
      { path: "blogs/page/:slug", element: <BlogArticlePage /> },
      {
        path: "app",
        element: <ProtectedRoute />,
        children: [
          {
            element: <AppLayout />,
            children: [
              {
                element: <GeneralLayout />,
                children: [
                  { path: "tday", element: <TodayPage /> },
                  { path: "todo", element: <TodoPage /> },
                  { path: "priority", element: <PriorityPage /> },
                  { path: "scheduled", element: <ScheduledPage /> },
                  { path: "completed", element: <CompletedPage /> },
                  { path: "add-task", element: <AddTaskPage /> },
                  { path: "admin", element: <AdminPage /> },
                  { path: "settings", element: <SettingsPage /> },
                  { path: "list/:id", element: <ListPage /> },
                  { path: "note/:id", element: <NotePage /> },
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
