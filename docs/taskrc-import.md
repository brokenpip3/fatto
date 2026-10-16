# Taskrc import

pen **Settings → Taskrc**, paste your configuration, preview it, then apply.

| Key | Imported as |
| --- | --- |
| `sync.server.url` | Sync server URL |
| `sync.server.client_id` | Client UUID |
| `sync.encryption_secret` | Encryption secret |
| `sync.aws.bucket` | S3 bucket |
| `sync.aws.access_key_id` | S3 access key ID |
| `sync.aws.secret_access_key` | S3 secret access key |
| `sync.aws.region` | S3 region (optional) |
| `sync.aws.endpoint` | S3-compatible endpoint (optional) |
| `context` | Active context name |
| `default.project` | Default project; empty disables it |
| `weekstart` | `monday` or `sunday` |

unsupported keys and `include` directives are skipped and reported in the preview
