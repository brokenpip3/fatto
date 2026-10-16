# Recurring tasks

Taskwarrior creates recurring tasks from a series template, each occurrence is a separate task with its own due date, see [how taskwarrior recurrence works](https://taskwarrior.org/docs/recurrence/)

## In Fatto

- create a series with **new task → repeat**, a due date is required
- open **edit series** from a recurring task or **⋮ → recurring series**. Description, project, priority and tag edits apply to future occurrences.
- Use **change schedule** to edit dates or the repeat rule, generated series require replacement, replacing or stopping a series keeps existing tasks.

## Generate tasks on one client

**Fatto does not generate occurrences or handle their expiry**, choose one taskwarrior CLI client to do this.
Enabling generation on several clients can create duplicates, as described in the [Taskwarrior sync manual](https://taskwarrior.org/docs/man/task-sync.5/#avoiding-duplicate-recurring-tasks).

| Client | Setting |
| --- | --- |
| the CLI that generates tasks | `task config recurrence on` |
| every other CLI client | `task config recurrence off` |

On the chosen client run:

```sh
task sync
task all/next
task sync
```

Any report such as `task next` generates the tasks; `task sync` alone does not. Sync Fatto afterward to receive them, completing a task on the phone does not immediately create the next one. This is necessary to avoid duplicates, like mentioned in the taskwarrior sync manual that I linked above.

## Limits

- Supported rules: daily, weekdays, weekly, monthly, yearly, or a positive number of days, weeks, months or years. The until date must be on or after the due date.
- You can keep an imported rule while editing the series’ description, project, priority or tags. To change its schedule, choose a supported rule.
- Fatto preserves recurrence metadata and updates existing status-mask entries when occurrences change status. It reports missing or duplicate occurrences, but does not recreate missing tasks or remove duplicates (again to avoid duplicates, use the CLI to do that)
