# Features

What T'Day does and which platform does it. The README has the short version; this page adds the
detail.

The in-app **How-To & Tips** guide (Settings → How-To & Tips) is the always-current version of this
list. It is built from [`GuideCatalog.kt`](../shared/src/commonMain/kotlin/com/ohmz/tday/shared/guide/GuideCatalog.kt),
so if this page and the app disagree, the app is right.

- [Core concepts](#core-concepts)
- [Feature matrix](#feature-matrix)
- [Natural-language scheduling](#natural-language-scheduling)
- [Home-screen widgets](#home-screen-widgets)
- [In-car surfaces](#in-car-surfaces)
- [Dashboard widget (Homarr)](#dashboard-widget-homarr)
- [AI assistants (MCP)](#ai-assistants-mcp)
- [Task summaries](#task-summaries)
- [Languages](#languages)

---

## Core concepts

T'Day sorts work by whether it has a date.

| Concept           | What it is                                                                                                   |
|-------------------|--------------------------------------------------------------------------------------------------------------|
| Scheduled task    | A task with a due date. It shows up in Today, Scheduled, Calendar, Overdue, reminders, and repeats.          |
| Floater (Anytime) | A task with no date. It has priority, pinning, ordering, and list membership, but it never goes overdue.    |
| List              | A named, colored group for scheduled tasks.                                                                  |
| Floater list      | A named group for floaters. It is separate from scheduled lists.                                             |
| Completed history | Everything you've finished, scheduled and Anytime, with its list information kept.                           |
| Local Mode        | A workspace that stays on the device. No server, no account, no upload. In a browser it lives in that browser's storage, encrypted with a passphrase by default. |
| Server Mode       | A workspace on your own server. Changes save on the device first and sync in the background.                |

Adding a date to a floater turns it into a scheduled task, and removing the date turns it back. The
full data contract is in [DATA_MODEL.md](DATA_MODEL.md).

## Feature matrix

✅ available · — not on this platform · **Server** needs Server Mode

### Getting started and capture

| Feature                   | What it does                                                                         | Web | Android | iOS |
|---------------------------|--------------------------------------------------------------------------------------|:---:|:-------:|:---:|
| Scheduled and Anytime feeds | Two main feeds, one tap apart in the dock                                          | ✅  | ✅      | ✅  |
| Default home screen       | Choose whether the app opens on Scheduled or Floaters                                | ✅  | ✅      | ✅  |
| Type the date             | "Buy milk tomorrow 5pm" sets the due date and cleans up the title                    | ✅  | ✅      | ✅  |
| Calendar                  | Month, week, and day views of scheduled tasks                                        | ✅  | ✅      | ✅  |
| Priorities and pinning    | Low, Medium, or High, and pin a task to the top of its list                          | ✅  | ✅      | ✅  |
| Brain dump                | Paste a wall of text and T'Day splits it into tasks                                  | ✅  | —       | —   |
| Steps                     | A checklist inside a task                                                            | ✅  | —       | —   |
| Share into T'Day          | Send text from any app into a new task                                               | ✅  | ✅      | ✅  |

### Organizing

| Feature                 | What it does                                                                          | Web | Android | iOS |
|-------------------------|---------------------------------------------------------------------------------------|:---:|:-------:|:---:|
| Lists and floater lists | Named, colored groups, with an icon picked from the list's name                       | ✅  | ✅      | ✅  |
| Shared lists            | Invite someone on your server as an Editor or Viewer (**Server**)                     | ✅  | ✅      | ✅  |
| Reusable lists          | Reset a checklist to run it again: packing, chores, groceries                         | ✅  | ✅      | ✅  |
| Promote and float       | Give a floater a date, or let a stale task float                                      | ✅  | ✅      | ✅  |
| Quick Defer             | One tap to later today, tonight, tomorrow, or next week                               | ✅  | ✅      | ✅  |
| Overdue and Morning Sweep | A place for late tasks, and a one-card-at-a-time way to triage them                 | ✅  | ✅      | ✅  |
| Resting floaters        | Floaters you haven't touched fade after a month and rest after three                  | ✅  | ✅      | ✅  |
| Bulk actions            | Select several tasks and act on all of them                                           | ✅  | ✅      | ✅  |
| Drag to reorder         | Put tasks in the order you want                                                       | ✅  | ✅      | ✅  |
| Search                  | Find tasks by title, note, or list name                                               | ✅  | ✅      | ✅  |
| Completed history       | Look back on what you finished                                                        | ✅  | ✅      | ✅  |
| Day Done                | A calm "All done for today" when every task for today is finished                     | ✅  | ✅      | ✅  |
| Week in Review          | A Sunday recap of what you cleared                                                    | ✅  | —       | —   |
| Swipe and long-press actions | Quick actions on a task row without opening it                                   | —   | ✅      | ✅  |
| Copy task details       | Copy a task's title, notes, due date, and priority as text                            | ✅  | ✅      | ✅  |

### Repeats and reminders

| Feature             | What it does                                                                     | Web | Android | iOS |
|---------------------|----------------------------------------------------------------------------------|:---:|:-------:|:---:|
| Repeating tasks     | Daily, weekly, and custom repeats (RFC 5545 rules)                               | ✅  | ✅      | ✅  |
| Repeat suggestions  | Notices a task you finish on a rhythm and offers to make it repeat               | ✅  | ✅      | ✅  |
| Reminders           | A notification before a task is due, with Snooze 1h and Tonight actions          | —   | ✅      | ✅  |
| Quiet hours         | Hold reminders overnight                                                         | —   | ✅      | ✅  |
| Day Ahead digest    | One morning notification with today's counts                                     | —   | ✅      | ✅  |
| Web push            | Due-task notifications even when the tab is closed (**Server**)                  | ✅  | —       | —   |
| UnifiedPush         | Server push without Google, through your own distributor (**Server**)            | —   | ✅      | —   |

### Widgets and other surfaces

| Feature                      | What it does                                                          | Web | Android | iOS |
|------------------------------|-----------------------------------------------------------------------|:---:|:-------:|:---:|
| Today and Floater widgets    | Your tasks on the home screen, with complete and quick-add            | —   | ✅      | ✅  |
| Widget for a single list     | Point a widget at one list instead of the whole feed                  | —   | ✅      | ✅  |
| Quick Settings tile and shortcuts | Add a task from Quick Settings or the app icon's long-press menu | —   | ✅      | —   |
| Apple Watch                  | Today's tasks on your wrist, with a watch-face complication           | —   | —       | ✅  |
| Focus filters                | Let an iOS Focus decide which lists appear in Today                   | —   | —       | ✅  |
| In-car view                  | CarPlay on iOS, a simplified car screen on Android                    | —   | ✅      | ✅  |
| Device calendar              | Copy dated tasks into a "T'Day" calendar on your phone                | —   | ✅      | ✅  |
| Keyboard shortcuts           | `N` for a new task, `Ctrl/⌘ K` for the command palette, `?` for help  | ✅  | —       | —   |
| Installable app (PWA)        | Install the web app to your desktop or home screen                    | ✅  | —       | —   |

### Modes, sync, and your data

| Feature                 | What it does                                                                   | Web | Android | iOS |
|-------------------------|--------------------------------------------------------------------------------|:---:|:-------:|:---:|
| Local Mode              | Use T'Day fully offline with no server or account                              | ✅  | ✅      | ✅  |
| Server Mode             | Real-time sync across devices through your own server                          | ✅  | ✅      | ✅  |
| Offline sync            | Keep working offline; changes replay in order when you reconnect               | —   | ✅      | ✅  |
| Export and import       | Download everything as one JSON file. Importing always adds and never overwrites. | ✅  | ✅      | ✅  |
| Move to a server        | Take your Local Mode data to a server in one deliberate step                   | —   | ✅      | ✅  |
| In-app updates          | Download and install the latest release from inside the app                    | —   | ✅      | —   |

### Integrations

| Feature              | What it does                                                                           | Where                         |
|----------------------|----------------------------------------------------------------------------------------|-------------------------------|
| Task summaries       | A short summary of your day, optionally written by a local AI model (**Server**)       | Web, Android, iOS             |
| API keys             | Personal keys, read-only or full access, for dashboards and scripts (**Server**)       | Created in web Settings       |
| Homarr widget        | Your tasks on a [Homarr](https://homarr.dev) dashboard (**Server**)                    | [Details](#dashboard-widget-homarr) |
| AI assistants (MCP)  | Let Claude, Cursor, or another MCP client read and manage your tasks (**Server**)      | [Details](#ai-assistants-mcp) |
| Calendar feed        | A read-only ICS link for Apple Calendar, Google Calendar, and others (**Server**)      | [API_INTEGRATION.md](API_INTEGRATION.md#calendar-feed-read-only-ics) |

---

## Natural-language scheduling

Type a date or time into a task title and T'Day fills in the due date. "Buy milk tomorrow at 5pm"
sets the due date to 5:00 PM tomorrow, highlights the phrase as you type, and saves the task as
"Buy milk".

This runs **entirely on the device**, with no AI model and no network, so it works in Local Mode
too. Each platform uses its own date parser:

| Platform | Parser                                                                    |
|----------|---------------------------------------------------------------------------|
| Web      | [chrono-node](https://github.com/wanasit/chrono) (client-side JavaScript) |
| iOS      | Foundation `NSDataDetector` (Apple's built-in date detector)              |
| Android  | [Natty](https://github.com/joestelmach/natty) (bundled JVM library)       |

Parsing uses the device timezone and stores the result as a UTC instant, so the same task shows the
correct local time on every device. This is unrelated to the optional Ollama model, which only
writes [task summaries](#task-summaries).

Platform internals: [android-compose/README.md](../android-compose/README.md#natural-language-scheduling)
and [ios-swiftUI/README.md](../ios-swiftUI/README.md#natural-language-scheduling).

## Home-screen widgets

Both mobile apps ship **Today** (scheduled tasks due today) and **Floater** (Anytime tasks)
widgets in small, medium, and large sizes. You can complete a task from the widget or start a new
one with its plus button.

To show a single list instead of a whole feed:

- **Android** has a third widget, **List**. Each placed List widget picks its own list, so two can
  show two different lists.
- **iOS** lets you point either widget at one list with long-press ▸ Edit Widget.

On both platforms a scheduled list renders with due times and a floater list renders undated.

Widgets refresh as soon as a task changes. After every add, edit, complete, or delete, and when the
app goes to the background, the app pushes a refresh instead of waiting for the system's slow
schedule. Android uses `GlanceAppWidgetManager` with a WorkManager fallback; iOS uses `WidgetCenter`
reloads backed by an App Group snapshot. A 15-minute background refresh on each platform is the
safety net.

Architecture, file layout, and integration checklists: [WIDGET_SYNC.md](WIDGET_SYNC.md).

## In-car surfaces

Both apps offer a simplified Today/Floater view for driving: list templates, icon controls, and
voice capture for adding a task.

- **iOS: CarPlay.** A CarPlay template scene built on `CPListTemplate`. Adding a task goes through
  App Intents and Siri. Real CarPlay deployment waits on Apple granting the CarPlay entitlement for
  the app's category; until then the code builds but stays entitlement-gated.
- **Android: internal car screen (not Android Auto).** An in-app surface at `tday://car`. It
  deliberately doesn't declare Android Auto metadata, because Google Play's car-app categories
  don't include task apps. It uses on-device speech recognition for adding a task and falls back
  to the normal create sheet when speech isn't available.

Platform detail: [ios-swiftUI/README.md → CarPlay](../ios-swiftUI/README.md#carplay) and
[android-compose/README.md → Car Surface](../android-compose/README.md#car-surface).

## Dashboard widget (Homarr)

A **Tday Tasks** widget puts your tasks on a [Homarr](https://homarr.dev) dashboard. It shows
Today, Scheduled, Overdue, or Floater, and lets you complete (with undo), quick-add, edit, and
delete tasks without leaving the dashboard.

It isn't an iframe (T'Day sends `X-Frame-Options: DENY`). The widget calls T'Day's
token-authenticated REST API from Homarr's own backend, so you don't need an extra service.

1. In T'Day, open **Settings → Dashboard access → Generate key**. A read-only key is enough for a
   dashboard. Copy it; it is shown once.
2. In Homarr, add the **Tday** integration with your T'Day server URL and the key.
3. Add the **Tday Tasks** widget and pick a view.

The widget and integration live in the [`ohmzi/homarr`](https://github.com/ohmzi/homarr) fork, with
user docs in [`ohmzi/documentation`](https://github.com/ohmzi/documentation/tree/develop). To build
your own integration on the same API, see [API_INTEGRATION.md](API_INTEGRATION.md).

## AI assistants (MCP)

T'Day speaks the [Model Context Protocol](https://modelcontextprotocol.io), so an AI assistant can
read and change your tasks by talking to your own server. The endpoint is built into the backend at
`https://<your-tday>/mcp` and uses the same API keys as the REST API. There is nothing extra to
install.

```bash
claude mcp add --transport http tday https://tday.example.com/mcp --header "Authorization: Bearer tday_<keyId>_<secret>"
```

That example is Claude Code. For claude.ai and Claude Desktop connectors, set
**Authentication → None** and pass the key as an `api-key` header instead. T'Day accepts it on
either `Authorization` or `X-API-Key`.

- A **Full access** key lets the assistant create and change tasks.
- A **Read-only** key lets it read tasks, and tells it plainly why it can't write.

The tools follow T'Day's own rules. A task with a date becomes a scheduled task and one without
becomes an Anytime task. Naming a list that doesn't exist returns the lists that do, instead of
quietly creating a new one.

Per-client setup, scopes, and the tool reference: [MCP.md](MCP.md).

## Task summaries

T'Day can summarize your day in a sentence or two. With a local [Ollama](https://ollama.com) model
the summary reads naturally. Without one, the backend writes a deterministic summary. Either way it
never depends on a cloud AI service, and nothing leaves your server.

Turning on the AI model: [SETUP.md → AI summaries](SETUP.md#optional-ai-summaries-with-ollama).
Design rationale: [ADR 004](adr/004-local-ai-via-ollama.md).

## Languages

The web app ships in 10 languages: English, German, Spanish, French, Italian, Japanese, Malay,
Portuguese, Russian, and Chinese. All of them are bundled into the build, so they work offline. The
mobile apps use platform-local string resources.
