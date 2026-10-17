# Task review

Fatto follows [Taskshell’s review workflow](https://taskwarrior.org/docs/review/) using the `reviewed` date UDA. Marking reviewed or saving edits during review stamps `reviewed:now`; skipping leaves it unchanged.

Default eligibility is `(reviewed.none: or reviewed.before:now-6days) and (+PENDING or +WAITING)`, sorted by `reviewed+,modified+`. Configure the interval, filters, session limit, swipes and reminders in **Settings → Review**.

for CLI compatibility, add to `.taskrc` or install `taskshell` that will add the UDA automatically:

```ini
uda.reviewed.type=date
uda.reviewed.label=Reviewed
```
