"use client";

import React from "react";
import Image from "next/image";
import { Link } from "@/i18n/navigation";
import { Button } from "@/components/ui/button";
import {
  ArrowRight,
  BrainCircuit,
  CalendarDays,
  FileText,
  Lock,
  Sparkles,
  Wand2,
} from "lucide-react";
import { Card, CardContent, CardHeader } from "@/components/ui/card";

type Slide = {
  title: string;
  description: string;
  bullets: string[];
  icon: React.ComponentType<{ className?: string }>;
};

const slides: Slide[] = [
  {
    title: "A calmer way to run your day",
    description:
      "T'Day brings your tasks, notes, and schedule into one focused workspace so planning feels simple and execution stays sharp.",
    bullets: [
      "One home for planning, writing, and tracking",
      "Fast capture that turns into clear action",
      "Built for momentum, not busywork",
    ],
    icon: Sparkles,
  },
  {
    title: "Daily Briefing with AI",
    description:
      "AI Summary scans your open work and produces a short briefing on what is urgent, what is blocked, and what deserves attention first.",
    bullets: [
      "Priority snapshots in plain language",
      "Highlights overdue chains and bottlenecks",
      "Suggests the next best focus block",
    ],
    icon: BrainCircuit,
  },
  {
    title: "Type naturally, schedule instantly",
    description:
      "Write tasks the way you speak. T'Day parses timing context and turns free text into structured reminders and due dates.",
    bullets: [
      "Recognizes dates and times as you type",
      "Infers duration from natural phrasing",
      "Creates reminders without extra forms",
      "Understands follow-up intent in context",
    ],
    icon: Wand2,
  },
  {
    title: "Timeline clarity at every zoom level",
    description:
      "Switch between month, week, and day views to understand workload shape, avoid collisions, and rebalance your plan quickly.",
    bullets: [
      "Monthly, weekly, and daily perspectives",
      "Color tags for projects and priorities",
      "Drag tasks to reschedule in seconds",
      "Overlap warnings before conflicts grow",
    ],
    icon: CalendarDays,
  },
  {
    title: "Notes that feel like a workspace",
    description:
      "Capture ideas in a clean editor built for planning sessions, docs, and meeting notes that stay connected to your tasks.",
    bullets: [
      "Markdown-first writing flow",
      "Rich formatting for structured notes",
      "Code snippets with syntax highlighting",
      "Nested lists for deep outlines",
    ],
    icon: FileText,
  },
  {
    title: "Security built into every layer",
    description:
      "Across web, mobile, and backend services, T'Day applies practical safeguards for transport, sessions, storage, and access boundaries.",
    bullets: [
      "Encrypted transport on authenticated traffic",
      "Revocable sessions with server-side controls",
      "Protected data paths in cache and storage",
      "Default-deny APIs with abuse monitoring",
    ],
    icon: Lock,
  },
];

const mockTasks = [
  {
    title: "Prepare sprint review deck",
    time: "9:30 AM",
    meta: "Work · High",
    status: "Due today",
  },
  {
    title: "Gym and mobility session",
    time: "12:15 PM",
    meta: "Health · Medium",
    status: "1h duration",
  },
  {
    title: "Pick up groceries",
    time: "5:40 PM",
    meta: "Home · Low",
    status: "Reminder set",
  },
  {
    title: "Write project design notes",
    time: "8:00 PM",
    meta: "Deep Work · Medium",
    status: "Notion page linked",
  },
];

export default function OnboardingLanding() {
  const [activeSlide, setActiveSlide] = React.useState(0);
  const isLastSlide = activeSlide === slides.length - 1;
  const currentSlide = slides[activeSlide];
  const SlideIcon = currentSlide.icon;

  return (
    <main className="relative min-h-screen overflow-hidden bg-background text-foreground">
      <div className="absolute inset-0 bg-gradient-to-br from-background via-background to-muted/35" />
      <div className="absolute inset-0 bg-[radial-gradient(1200px_700px_at_12%_12%,hsla(var(--accent),0.11),transparent),radial-gradient(900px_500px_at_90%_18%,hsla(var(--primary),0.1),transparent)]" />

      <div className="absolute inset-0 p-3 sm:p-5 lg:p-8">
        <div className="h-full w-full rounded-3xl border border-border/70 bg-card/55 p-3 blur-xl sm:p-4">
          <MockTodayWorkspace />
        </div>
      </div>

      <div className="absolute inset-0 bg-background/60" />

      <div className="relative z-10 flex min-h-screen items-center justify-center px-4 py-10">
        <Card className="w-full max-w-3xl gap-0 rounded-3xl border-border/70 bg-card/88 py-0 shadow-2xl backdrop-blur-md">
          <CardHeader className="space-y-5 px-6 pt-6 sm:px-8 sm:pt-8">
            <div className="mx-auto flex items-center justify-center">
              <Image src="/tday-icon.svg" alt="T'Day" width={56} height={56} />
            </div>

            <div className="flex items-center gap-3 text-muted-foreground">
              <div className="rounded-xl border border-border bg-background/70 p-2">
                <SlideIcon className="h-5 w-5 text-accent" />
              </div>
              <div className="text-sm font-semibold tracking-wide">
                Step {activeSlide + 1} of {slides.length}
              </div>
            </div>
          </CardHeader>

          <CardContent className="px-6 pb-6 sm:px-8 sm:pb-8">
            <h1 className="text-3xl font-serif font-semibold leading-tight text-foreground">
              {currentSlide.title}
            </h1>
            <p className="mt-4 text-sm leading-6 text-muted-foreground sm:text-base">
              {currentSlide.description}
            </p>

            <ul className="mt-6 space-y-3 rounded-2xl border border-border/70 bg-background/55 p-4">
              {currentSlide.bullets.map((bullet) => (
                <li
                  key={bullet}
                  className="flex items-start gap-3 text-sm text-foreground sm:text-base"
                >
                  <span className="mt-0.5 text-accent">→</span>
                  <span>{bullet}</span>
                </li>
              ))}
            </ul>

            <div className="mt-6 flex items-center gap-2">
              {slides.map((slide, index) => (
                <button
                  key={slide.title}
                  type="button"
                  onClick={() => setActiveSlide(index)}
                  aria-label={`Go to slide ${index + 1}`}
                  className={`h-2 rounded-full transition-all duration-200 ${
                    index === activeSlide
                      ? "w-8 bg-accent"
                      : "w-2 bg-muted-foreground/35 hover:bg-muted-foreground/60"
                  }`}
                />
              ))}
            </div>

            {!isLastSlide ? (
              <div className="mt-8 flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
                <Button
                  asChild
                  variant="outline"
                  className="w-full border-border/80 bg-background/80 text-foreground hover:bg-muted sm:w-auto"
                >
                  <Link href="/login">Skip and Login</Link>
                </Button>

                <Button
                  type="button"
                  onClick={() =>
                    setActiveSlide((prev) => Math.min(prev + 1, slides.length - 1))
                  }
                  className="w-full sm:w-auto"
                >
                  Next
                  <ArrowRight className="ml-2 h-4 w-4" />
                </Button>
              </div>
            ) : (
              <div className="mt-8 grid gap-3 sm:grid-cols-2">
                <Button asChild>
                  <Link href="/login">Login</Link>
                </Button>
                <Button
                  asChild
                  variant="outline"
                  className="border-border/80 bg-background/80 text-foreground hover:bg-muted"
                >
                  <Link href="/register">Create User</Link>
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </main>
  );
}

function MockTodayWorkspace() {
  return (
    <div className="flex h-full gap-3 rounded-2xl border border-border/70 bg-card/75 p-3 sm:gap-4 sm:p-4">
      <aside className="hidden w-64 shrink-0 flex-col rounded-2xl border border-border/70 bg-background/75 p-4 text-foreground lg:flex">
        <div className="mb-5 flex items-center gap-3">
          <div className="h-9 w-9 rounded-xl bg-accent/20" />
          <div>
            <p className="text-sm font-semibold">{"T'Day"}</p>
            <p className="text-xs text-muted-foreground">Planner Workspace</p>
          </div>
        </div>

        <nav className="space-y-2 text-sm">
          <div className="rounded-xl bg-accent/18 px-3 py-2 text-foreground">Today</div>
          <div className="rounded-xl px-3 py-2 text-muted-foreground">Completed</div>
          <div className="rounded-xl px-3 py-2 text-muted-foreground">Calendar</div>
          <div className="rounded-xl px-3 py-2 text-muted-foreground">Notes</div>
          <div className="rounded-xl px-3 py-2 text-muted-foreground">Settings</div>
        </nav>

        <div className="mt-6 border-t border-border/70 pt-4">
          <p className="mb-2 text-xs uppercase tracking-wide text-muted-foreground">
            Lists
          </p>
          <div className="space-y-2 text-sm">
            <div className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-emerald-300" />
              Product
            </div>
            <div className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-violet-300" />
              Personal
            </div>
            <div className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-amber-300" />
              Errands
            </div>
          </div>
        </div>
      </aside>

      <section className="flex min-w-0 flex-1 flex-col rounded-2xl border border-border/70 bg-background/75 p-4 sm:p-5">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/70 pb-4">
          <div>
            <p className="text-xs uppercase tracking-wide text-muted-foreground">
              Today View
            </p>
            <h2 className="text-xl font-semibold text-foreground sm:text-2xl">
              Tuesday, Focus Session
            </h2>
          </div>
          <div className="rounded-full border border-border/70 bg-card/70 px-3 py-1 text-xs text-muted-foreground">
            4 Tasks Scheduled
          </div>
        </div>

        <div className="mt-4 grid gap-3">
          {mockTasks.map((task) => (
            <article
              key={task.title}
              className="rounded-xl border border-border/70 bg-card/75 p-3 sm:p-4"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h3 className="text-sm font-medium text-foreground sm:text-base">
                    {task.title}
                  </h3>
                  <p className="mt-1 text-xs text-muted-foreground sm:text-sm">
                    {task.meta}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-xs font-medium text-accent sm:text-sm">{task.time}</p>
                  <p className="mt-1 text-[11px] text-muted-foreground sm:text-xs">
                    {task.status}
                  </p>
                </div>
              </div>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
