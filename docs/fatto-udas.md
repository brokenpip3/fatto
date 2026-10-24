# Fatto UDAs

Fatto uses these [Taskwarrior User Defined Attributes (UDAs)](https://taskwarrior.org/docs/udas/) when you enable the corresponding features.

| UDA | Value | Purpose | Optional |
| --- | --- | --- | --- |
| `fatto_display_due` | `v1:local-datetime` | Show the due date with a time in the device timezone. | Yes |
| `fatto_display_scheduled` | `v1:local-datetime` | Show the scheduled date with a time in the device timezone. | Yes |
| `fatto_display_wait` | `v1:local-datetime` | Show the wait date with a time in the device timezone. | Yes |
| `reviewed` | Date/time | Record when a task was last reviewed; the same UDA used by [Taskshell (`tasksh`)](https://taskwarrior.org/docs/review/). | Yes — used by Review |
