![header](./header.webp)

<details>
<summary><b>Disclaimer</b></summary>

About 80% of this project was vibecoded. I never wrote anything in klotin in my life and I have a very basic understanding of Rust.

I just wanted a TaskWarrior client for Android, it started as a weekend experiment and accidentally grew into a functional application. I'm releasing it into the wild because the community has been seeking of an opens source syncing TaskWarrior 3.x client for Android for far too long.

It works nice for me and I hope it'll work for you too (but it could be buggy and ruin your tasks, so backup your data before using it - but I said it could, not that it will for sure :D ).
</details>

## What is this?

Fatto (italian for "*done*") is a handy TaskWarrior client for Android. It syncs with any standard `taskchampion-sync-server`, filters your tasks, manages your projects, and respects your privacy.

## Features

- **sync**: bi-directional sync with any [`taskchampion-sync-server`](https://github.com/GothenburgBitFactory/taskchampion-sync-server), or directly with AWS S3 / S3-compatible storage (e.g. minio)
- **task**: comprehensive task management with filtering, sorting, and detail editing
- **projects**: hierarchical project management with pending task counts
- **tags**: auto-resizing tag list with pending task counts
- **calendar**: intuitive date pickers for due/scheduled dates
- **notifications**: daily summaries of due/scheduled tasks
- **auto-suggestions**: smart suggestions for projects and tags during task creation

see [roadmap](./ROADMAP.md) for the plan for future features and improvements.

## Installation

### From F-Droid

[<img height="80" alt="Get it on F-Droid"
src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
/>](https://f-droid.org/app/com.brokenpip3.fatto)

### Manually and beta testing

You can download the latest apk from the [releases](https://github.com/brokenpip3/fatto/releases/latest) page.

By time to time we also release beta versions, you can test it without interfering with the stable version, the stable version is a different app from an android perspective, so you can keep both or only use the stable one.

## S3 sync

Settings → Sync → *S3 storage* syncs directly with a bucket, with no server in between.

| Field | AWS S3 | S3-compatible service (minio, Garage, ...) |
| --- | --- | --- |
| Bucket | the bucket name, e.g. `my-tasks` | same |
| Endpoint URL | leave empty | required, with scheme: `https://minio.example.com` |
| Region | the region the bucket was created in, e.g. `eu-west-2` (empty means `us-east-1`) | whatever the service expects, often empty |
| Access Key ID | 20 upper-case characters, e.g. `AKIAIOSFODNN7EXAMPLE` | the service's key |
| Secret Access Key | 40 characters, e.g. `wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY` | the service's secret |
| Encryption Secret | your passphrase; must match the one used on your other clients | same |

The keys are used verbatim: no quotes around them, and no escaping of the `/` in the
secret. The key needs `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject` and
`s3:ListBucket` on the bucket. Temporary credentials (`ASIA...`) are not supported,
since they also require a session token.

If a sync fails with `SignatureDoesNotMatch`, the access key ID or the secret access
key does not match what the bucket expects: re-enter both, watching for characters
added or changed by the keyboard while typing.

## Architecture

The backend is built in Rust using the official [taskChampion](https://github.com/GothenburgBitFactory/taskchampion), which provides a robust and efficient way to manage tasks and sync with `taskchampion-sync` servers. The frontend is written in Kotlin using Jetpack Compose.

## Contributing

If you want to poke around the internals or build it yourself, the entire development environment is done via nix devshells.
You can build the debug apk with: `just build-debug`. The `justfile` handles the heavy lifting, including cross-compiling the Rust JNI libraries and orchestrating the Kotlin build.
