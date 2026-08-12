use chrono::{DateTime, Utc};
use std::str::FromStr;
use std::sync::{Arc, Mutex};
use taskchampion::server::AwsCredentials;
use taskchampion::storage::inmemory::InMemoryStorage;
use taskchampion::storage::sqlite::SqliteStorage;
use taskchampion::storage::{AccessMode, Storage};
use taskchampion::{Operations, Replica, ServerConfig, Tag, Task, Uuid};
use tokio::runtime::Runtime;

uniffi::setup_scaffolding!();

type Result<T> = std::result::Result<T, TaskError>;

fn parse_rfc3339(date_str: Option<String>) -> Result<Option<DateTime<Utc>>> {
    date_str
        .map(|s| {
            DateTime::parse_from_rfc3339(&s)
                .map_err(|e| TaskError::Internal(e.to_string()))
                .map(|dt| dt.with_timezone(&Utc))
        })
        .transpose()
}

#[derive(thiserror::Error, Debug, uniffi::Error)]
pub enum TaskError {
    #[error("TaskChampion error: {0}")]
    Internal(String),
}

impl From<taskchampion::Error> for TaskError {
    fn from(err: taskchampion::Error) -> Self {
        TaskError::Internal(err.to_string())
    }
}

#[derive(uniffi::Enum, PartialEq, Debug, Clone, Copy)]
pub enum TaskStatus {
    Pending,
    Completed,
    Deleted,
    Recurring,
}

impl From<taskchampion::Status> for TaskStatus {
    fn from(status: taskchampion::Status) -> Self {
        match status {
            taskchampion::Status::Pending => TaskStatus::Pending,
            taskchampion::Status::Completed => TaskStatus::Completed,
            taskchampion::Status::Deleted => TaskStatus::Deleted,
            taskchampion::Status::Recurring => TaskStatus::Recurring,
            taskchampion::Status::Unknown(_) => TaskStatus::Pending,
        }
    }
}

impl From<TaskStatus> for taskchampion::Status {
    fn from(status: TaskStatus) -> Self {
        match status {
            TaskStatus::Pending => taskchampion::Status::Pending,
            TaskStatus::Completed => taskchampion::Status::Completed,
            TaskStatus::Deleted => taskchampion::Status::Deleted,
            TaskStatus::Recurring => taskchampion::Status::Recurring,
        }
    }
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct UdaPair {
    pub key: String,
    pub value: String,
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct Annotation {
    pub entry: String,
    pub description: String,
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct TaskData {
    pub uuid: String,
    pub description: String,
    pub status: TaskStatus,
    pub tags: Vec<String>,
    pub due: Option<String>,
    pub entry: Option<String>,
    pub project: Option<String>,
    pub wait: Option<String>,
    pub scheduled: Option<String>,
    pub start: Option<String>,
    pub priority: Option<String>,
    pub urgency: f32,
    pub is_blocked: bool,
    pub is_blocking: bool,
    pub dependencies: Vec<String>,
    pub udas: Vec<UdaPair>,
    pub annotations: Vec<Annotation>,
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct TaskUpdateProps {
    pub uuid: String,
    pub description: String,
    pub status: TaskStatus,
    pub project: Option<String>,
    pub tags: Vec<String>,
    pub due: Option<String>,
    pub wait: Option<String>,
    pub scheduled: Option<String>,
    pub start: Option<String>,
    pub priority: Option<String>,
    pub dependencies: Vec<String>,
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct TaskAddProps {
    pub description: String,
    pub project: Option<String>,
    pub tags: Vec<String>,
    pub due: Option<String>,
    pub wait: Option<String>,
    pub scheduled: Option<String>,
    pub start: Option<String>,
    pub priority: Option<String>,
    pub dependencies: Vec<String>,
}

pub enum DynStorage {
    Sqlite(SqliteStorage),
    InMemory(InMemoryStorage),
}

#[async_trait::async_trait]
impl Storage for DynStorage {
    async fn txn<'a>(
        &'a mut self,
    ) -> std::result::Result<
        Box<dyn taskchampion::storage::StorageTxn + Send + 'a>,
        taskchampion::Error,
    > {
        match self {
            DynStorage::Sqlite(s) => s.txn().await,
            DynStorage::InMemory(s) => s.txn().await,
        }
    }
}

#[derive(uniffi::Object)]
pub struct ReplicaWrapper {
    inner: Arc<Mutex<Replica<DynStorage>>>,
    rt: Arc<Runtime>,
}

#[uniffi::export]
impl ReplicaWrapper {
    #[uniffi::constructor]
    pub fn new_on_disk(path: String) -> Result<Arc<Self>> {
        let rt = Arc::new(Runtime::new().map_err(|e| TaskError::Internal(e.to_string()))?);
        let storage = rt
            .block_on(async { SqliteStorage::new(path, AccessMode::ReadWrite, true).await })
            .map_err(|e| TaskError::Internal(e.to_string()))?;
        let replica = Replica::new(DynStorage::Sqlite(storage));
        Ok(Arc::new(Self {
            inner: Arc::new(Mutex::new(replica)),
            rt,
        }))
    }

    #[uniffi::constructor]
    pub fn new_in_memory() -> Result<Arc<Self>> {
        let rt = Arc::new(Runtime::new().map_err(|e| TaskError::Internal(e.to_string()))?);
        let storage = InMemoryStorage::new();
        let replica = Replica::new(DynStorage::InMemory(storage));
        Ok(Arc::new(Self {
            inner: Arc::new(Mutex::new(replica)),
            rt,
        }))
    }

    pub fn all_task_data(&self) -> Result<Vec<TaskData>> {
        let mut replica = self.inner.lock().unwrap();
        let tasks = self.rt.block_on(replica.all_tasks())?;

        let mut results = Vec::with_capacity(tasks.len());
        for task in tasks.values() {
            let is_blocked = task.is_blocked();
            let is_blocking = task.is_blocking();
            results.push(map_task(task.clone(), is_blocked, is_blocking));
        }
        Ok(results)
    }

    pub fn get_task(&self, uuid: String) -> Result<Option<TaskData>> {
        let mut replica = self.inner.lock().unwrap();
        let uuid =
            Uuid::parse_str(&uuid).map_err(|_| TaskError::Internal("Invalid UUID".into()))?;
        let task = self.rt.block_on(replica.get_task(uuid))?;
        Ok(task.map(|t| {
            let is_blocked = t.is_blocked();
            let is_blocking = t.is_blocking();
            map_task(t, is_blocked, is_blocking)
        }))
    }

    pub fn add_task(&self, props: TaskAddProps) -> Result<TaskData> {
        let mut replica = self.inner.lock().unwrap();
        let mut ops = Operations::new();
        let uuid = Uuid::new_v4();
        let mut task = self.rt.block_on(replica.create_task(uuid, &mut ops))?;
        task.set_description(props.description, &mut ops)?;
        task.set_status(taskchampion::Status::Pending, &mut ops)?;
        task.set_entry(Some(chrono::Utc::now()), &mut ops)?;

        task.set_value(String::from("project"), props.project, &mut ops)?;
        task.set_value(String::from("priority"), props.priority, &mut ops)?;

        for tag_str in props.tags {
            let tag = Tag::from_str(&tag_str).map_err(|e| TaskError::Internal(e.to_string()))?;
            if tag.is_user() {
                task.add_tag(&tag, &mut ops)?;
            }
        }

        for dep_str in props.dependencies {
            let dep = Uuid::parse_str(&dep_str).map_err(|e| TaskError::Internal(e.to_string()))?;
            task.add_dependency(dep, &mut ops)?;
        }

        task.set_wait(parse_rfc3339(props.wait)?, &mut ops)?;
        task.set_due(parse_rfc3339(props.due)?, &mut ops)?;
        task.set_timestamp("scheduled", parse_rfc3339(props.scheduled)?, &mut ops)?;
        task.set_timestamp("start", parse_rfc3339(props.start)?, &mut ops)?;

        self.rt.block_on(replica.commit_operations(ops))?;
        let is_blocked = task.is_blocked();
        let is_blocking = task.is_blocking();
        Ok(map_task(task, is_blocked, is_blocking))
    }

    pub fn update_task_status(&self, uuid: String, status: TaskStatus) -> Result<()> {
        let mut replica = self.inner.lock().unwrap();
        let uuid =
            Uuid::parse_str(&uuid).map_err(|_| TaskError::Internal("Invalid UUID".into()))?;
        let mut ops = Operations::new();
        if let Some(mut task) = self.rt.block_on(replica.get_task(uuid))? {
            task.set_status(status.into(), &mut ops)?;
            self.rt.block_on(replica.commit_operations(ops))?;
        }
        Ok(())
    }

    pub fn update_task(&self, props: TaskUpdateProps) -> Result<()> {
        let mut replica = self.inner.lock().unwrap();
        let uuid =
            Uuid::parse_str(&props.uuid).map_err(|_| TaskError::Internal("Invalid UUID".into()))?;
        let mut ops = Operations::new();

        if let Some(mut task) = self.rt.block_on(replica.get_task(uuid))? {
            task.set_description(props.description, &mut ops)?;
            task.set_status(props.status.into(), &mut ops)?;

            // Handle project and priority
            task.set_value(String::from("project"), props.project, &mut ops)?;
            task.set_value(String::from("priority"), props.priority, &mut ops)?;

            // Handle tags
            let current_tags: Vec<Tag> = task.get_tags().collect();
            for tag in current_tags {
                if tag.is_user() {
                    task.remove_tag(&tag, &mut ops)?;
                }
            }
            for tag_str in props.tags {
                let tag =
                    Tag::from_str(&tag_str).map_err(|e| TaskError::Internal(e.to_string()))?;
                if tag.is_user() {
                    task.add_tag(&tag, &mut ops)?;
                }
            }

            // Handle dependencies
            let current_deps: Vec<Uuid> = task.get_dependencies().collect();
            for dep in current_deps {
                task.remove_dependency(dep, &mut ops)?;
            }
            for dep_str in props.dependencies {
                let dep =
                    Uuid::parse_str(&dep_str).map_err(|e| TaskError::Internal(e.to_string()))?;
                task.add_dependency(dep, &mut ops)?;
            }

            task.set_due(parse_rfc3339(props.due)?, &mut ops)?;
            task.set_wait(parse_rfc3339(props.wait)?, &mut ops)?;
            task.set_timestamp("scheduled", parse_rfc3339(props.scheduled)?, &mut ops)?;
            task.set_timestamp("start", parse_rfc3339(props.start)?, &mut ops)?;

            self.rt.block_on(replica.commit_operations(ops))?;
        }
        Ok(())
    }

    pub fn add_annotation(&self, uuid: String, description: String) -> Result<Annotation> {
        let mut replica = self.inner.lock().unwrap();
        let uuid =
            Uuid::parse_str(&uuid).map_err(|_| TaskError::Internal("Invalid UUID".into()))?;
        let mut ops = Operations::new();
        if let Some(mut task) = self.rt.block_on(replica.get_task(uuid))? {
            let mut entry = chrono::Utc::now();
            // taskchampion keys on seconds — ensure unique timestamps
            let existing_timestamps: Vec<i64> = task
                .get_annotations()
                .map(|a| a.entry.timestamp())
                .collect();
            while existing_timestamps.contains(&entry.timestamp()) {
                entry += chrono::Duration::seconds(1);
            }
            let ann = taskchampion::Annotation {
                entry,
                description: description.clone(),
            };
            task.add_annotation(ann, &mut ops)?;
            self.rt.block_on(replica.commit_operations(ops))?;
            Ok(Annotation {
                entry: entry.to_rfc3339(),
                description,
            })
        } else {
            Err(TaskError::Internal("Task not found".into()))
        }
    }

    pub fn remove_annotation(&self, uuid: String, entry: String) -> Result<()> {
        let mut replica = self.inner.lock().unwrap();
        let uuid =
            Uuid::parse_str(&uuid).map_err(|_| TaskError::Internal("Invalid UUID".into()))?;
        let entry_dt = DateTime::parse_from_rfc3339(&entry)
            .map_err(|e| TaskError::Internal(e.to_string()))?
            .with_timezone(&Utc);
        let mut ops = Operations::new();
        if let Some(mut task) = self.rt.block_on(replica.get_task(uuid))? {
            task.remove_annotation(entry_dt, &mut ops)?;
            self.rt.block_on(replica.commit_operations(ops))?;
            Ok(())
        } else {
            Err(TaskError::Internal("Task not found".into()))
        }
    }

    pub fn sync(&self, server_url: String, client_id: String, secret: String) -> Result<()> {
        let mut replica = self.inner.lock().unwrap();
        let client_id = Uuid::parse_str(&client_id)
            .map_err(|_| TaskError::Internal("Invalid Client ID".into()))?;
        let config = ServerConfig::Remote {
            url: server_url,
            client_id,
            encryption_secret: secret.into_bytes(),
        };
        self.rt.block_on(async {
            let mut server = config.into_server().await?;
            replica.sync(&mut server, false).await
        })?;
        Ok(())
    }

    /// Sync with an AWS S3 or S3-compatible storage bucket (e.g. minio, Backblaze B2,
    /// Garage). This mirrors taskchampion's `ServerConfig::Aws` cloud backend.
    ///
    /// `region` and `endpoint_url` are optional: leave `region` empty to use the AWS
    /// default region, and set `endpoint_url` to the hostname of an S3-compatible
    /// service. When an `endpoint_url` is provided the client switches to path-style
    /// addressing, which is what most self-hosted S3-compatible services expect.
    #[allow(clippy::too_many_arguments)]
    pub fn sync_aws(
        &self,
        bucket: String,
        region: Option<String>,
        endpoint_url: Option<String>,
        access_key_id: String,
        secret_access_key: String,
        encryption_secret: String,
    ) -> Result<()> {
        let settings = AwsSettings::validate(
            bucket,
            region,
            endpoint_url,
            access_key_id,
            secret_access_key,
            encryption_secret,
        )?;

        let mut replica = self.inner.lock().unwrap();

        let config = ServerConfig::Aws {
            // S3-compatible endpoints generally require path-style addressing; real
            // AWS S3 (no custom endpoint) keeps the default virtual-hosted style.
            force_path_style: settings.endpoint_url.is_some(),
            region: settings.region,
            bucket: settings.bucket,
            endpoint_url: settings.endpoint_url,
            credentials: AwsCredentials::AccessKey {
                access_key_id: settings.access_key_id,
                secret_access_key: settings.secret_access_key,
            },
            encryption_secret: settings.encryption_secret.into_bytes(),
        };
        self.rt
            .block_on(async {
                let mut server = config.into_server().await?;
                replica.sync(&mut server, false).await
            })
            .map_err(|e| TaskError::Internal(explain_aws_error(&e.to_string())))?;
        Ok(())
    }
}

/// S3 settings that have been normalized and checked for the mistakes that would
/// otherwise surface as an opaque S3 error at sync time.
struct AwsSettings {
    bucket: String,
    region: Option<String>,
    endpoint_url: Option<String>,
    access_key_id: String,
    secret_access_key: String,
    encryption_secret: String,
}

impl AwsSettings {
    /// Validate the settings for an S3 sync.
    ///
    /// The values arrive from a text form on a phone, where a stray space, a
    /// newline from a paste or a mistyped region are easy to produce and
    /// impossible to see. S3 answers all of those with the same opaque
    /// `SignatureDoesNotMatch` (or a connection timeout), so the mistakes that
    /// can be recognized without talking to the server are rejected here with a
    /// message naming the field.
    ///
    /// Checks that only hold for real AWS (key shapes, region names) are applied
    /// only when no custom endpoint is set: S3-compatible services such as minio
    /// or Garage use credentials and regions of their own choosing.
    fn validate(
        bucket: String,
        region: Option<String>,
        endpoint_url: Option<String>,
        access_key_id: String,
        secret_access_key: String,
        encryption_secret: String,
    ) -> Result<Self> {
        // Normalize: surrounding whitespace and empty optionals coming across the
        // FFI boundary are never meaningful.
        let bucket = bucket.trim().to_string();
        let access_key_id = access_key_id.trim().to_string();
        let secret_access_key = secret_access_key.trim().to_string();
        let encryption_secret = encryption_secret.trim().to_string();
        let region = region
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty());
        let endpoint_url = endpoint_url
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty());

        if bucket.is_empty() {
            return Err(invalid("Bucket is required"));
        }
        if access_key_id.is_empty() {
            return Err(invalid("Access key ID is required"));
        }
        if secret_access_key.is_empty() {
            return Err(invalid("Secret access key is required"));
        }
        if encryption_secret.is_empty() {
            return Err(invalid("Encryption secret is required"));
        }

        validate_bucket_name(&bucket)?;

        // Interior whitespace in a key is always a mistake, and one that the
        // server can only report as a signature mismatch.
        if access_key_id.chars().any(char::is_whitespace) {
            return Err(invalid(
                "Access key ID must not contain spaces or line breaks",
            ));
        }
        if secret_access_key.chars().any(char::is_whitespace) {
            return Err(invalid(
                "Secret access key must not contain spaces or line breaks",
            ));
        }

        if let Some(url) = &endpoint_url {
            if !url.starts_with("http://") && !url.starts_with("https://") {
                return Err(invalid(
                    "Endpoint URL must start with http:// or https:// (e.g. https://minio.example.com)",
                ));
            }
        } else {
            // No endpoint means real AWS, where the credential and region formats
            // are fixed and worth checking.
            validate_aws_access_key_id(&access_key_id)?;
            validate_aws_secret_access_key(&secret_access_key)?;
            if let Some(region) = &region {
                validate_aws_region(region)?;
            }
        }

        Ok(Self {
            bucket,
            region,
            endpoint_url,
            access_key_id,
            secret_access_key,
            encryption_secret,
        })
    }
}

fn invalid(message: &str) -> TaskError {
    TaskError::Internal(message.into())
}

/// Check a bucket name against the S3 bucket naming rules, which minio and
/// Garage share with AWS.
fn validate_bucket_name(bucket: &str) -> Result<()> {
    const RULES: &str =
        "Bucket names must be 3-63 characters of lowercase letters, digits, dots and hyphens, \
         and start and end with a letter or digit";
    let valid_chars = bucket
        .chars()
        .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-' || c == '.');
    let valid_edges = bucket
        .chars()
        .next()
        .is_some_and(|c| c.is_ascii_lowercase() || c.is_ascii_digit())
        && bucket
            .chars()
            .next_back()
            .is_some_and(|c| c.is_ascii_lowercase() || c.is_ascii_digit());
    if !(3..=63).contains(&bucket.len()) || !valid_chars || !valid_edges || bucket.contains("..") {
        return Err(invalid(&format!("Invalid bucket name '{bucket}'. {RULES}")));
    }
    Ok(())
}

/// AWS access key IDs are 16-128 upper-case alphanumeric characters, in practice
/// `AKIA` (long-lived) or `ASIA` (temporary) followed by 16 characters.
fn validate_aws_access_key_id(access_key_id: &str) -> Result<()> {
    if !(16..=128).contains(&access_key_id.len())
        || !access_key_id
            .chars()
            .all(|c| c.is_ascii_uppercase() || c.is_ascii_digit())
    {
        return Err(invalid(
            "Access key ID does not look like an AWS key: it should be 16-128 upper-case letters \
             and digits, such as AKIAIOSFODNN7EXAMPLE. Check for stray quotes, backslashes or \
             characters added while typing",
        ));
    }
    if access_key_id.starts_with("ASIA") {
        return Err(invalid(
            "This is a temporary AWS access key (ASIA...), which also requires a session token. \
             Use a long-lived IAM user key (AKIA...) instead",
        ));
    }
    Ok(())
}

/// AWS secret access keys are exactly 40 base64 characters.
fn validate_aws_secret_access_key(secret_access_key: &str) -> Result<()> {
    let valid_chars = secret_access_key
        .chars()
        .all(|c| c.is_ascii_alphanumeric() || c == '+' || c == '/' || c == '=');
    if secret_access_key.len() != 40 || !valid_chars {
        return Err(invalid(
            "Secret access key does not look like an AWS secret: it should be exactly 40 \
             characters of letters, digits, '+', '/' and '='. Backslashes and quotes are not part \
             of the key and must not be escaped or included",
        ));
    }
    Ok(())
}

/// AWS region names are `<area>-<direction>-<number>`, e.g. `eu-west-2` or
/// `us-gov-east-1`. A region that is merely misspelled resolves to nothing and
/// surfaces as a connection failure after a long wait.
fn validate_aws_region(region: &str) -> Result<()> {
    let parts: Vec<&str> = region.split('-').collect();
    let Some((number, words)) = parts.split_last() else {
        return Err(invalid_region(region));
    };
    let words_ok = words.len() >= 2
        && words
            .iter()
            .all(|w| !w.is_empty() && w.chars().all(|c| c.is_ascii_lowercase()));
    let number_ok = !number.is_empty() && number.chars().all(|c| c.is_ascii_digit());
    if !words_ok || !number_ok {
        return Err(invalid_region(region));
    }
    Ok(())
}

fn invalid_region(region: &str) -> TaskError {
    invalid(&format!(
        "'{region}' is not an AWS region name. Regions look like eu-west-2 or us-east-1. Leave the \
         field empty to use us-east-1, or set an endpoint URL when using an S3-compatible service"
    ))
}

/// Turn the S3 error codes users actually hit into an explanation of what to
/// check, keeping the original message for reference.
fn explain_aws_error(message: &str) -> String {
    let hint = if message.contains("SignatureDoesNotMatch") {
        Some(
            "S3 rejected the request signature. The access key ID and secret access key must be \
             copied exactly, with no missing, extra or auto-corrected characters",
        )
    } else if message.contains("InvalidAccessKeyId") {
        Some("S3 does not know this access key ID. Check the key, and that it belongs to the same account as the bucket")
    } else if message.contains("AccessDenied") {
        Some(
            "The credentials are valid but not allowed to use this bucket. The key needs \
             s3:GetObject, s3:PutObject, s3:DeleteObject and s3:ListBucket on the bucket",
        )
    } else if message.contains("NoSuchBucket") {
        Some("The bucket does not exist. Check the bucket name, and create it if needed")
    } else if message.contains("PermanentRedirect")
        || message.contains("AuthorizationHeaderMalformed")
        || message.contains("IllegalLocationConstraint")
    {
        Some("The bucket lives in a different region than the one configured. Set the region the bucket was created in")
    } else if message.contains("RequestTimeTooSkewed") {
        Some("The device clock is too far from the real time for S3 to accept the request. Enable automatic date and time")
    } else if message.contains("dispatch failure") || message.contains("error trying to connect") {
        Some("Could not reach the S3 endpoint. Check the region, the endpoint URL and the network connection")
    } else {
        None
    };
    match hint {
        Some(hint) => format!("{hint} (S3 said: {message})"),
        None => message.to_string(),
    }
}

fn compute_task_urgency(task: &Task, is_blocked: bool, is_blocking: bool) -> f32 {
    // Reference coefficients: https://taskwarrior.org/docs/urgency/
    // This implementation follows the default weights of TaskWarrior.
    let mut urgency = 0.0;
    let now = Utc::now();

    // Next tag (+15.0)
    if task.get_tags().any(|t| t.to_string() == "next") {
        urgency += 15.0;
    }

    // Due date (+12.0 coefficient)
    // Reference: https://taskwarrior.org/docs/urgency/
    if let Some(due) = task.get_due() {
        let diff = (due - now).num_days();
        if diff < 0 {
            // Overdue tasks get the full coefficient
            urgency += 12.0;
        } else if diff < 7 {
            // Scaling Period: 7 days (TaskWarrior default 'urgency.due' period)
            // If due in 0 days (today), it gets +12.0.
            // If due in 7 days, it gets +12.0 * 0.2 = +2.4.
            // Formula: coeff * (1.0 - (0.8 * (days / period)))
            urgency += 12.0 * (1.0 - (0.8 * (diff as f32 / 7.0)));
        } else {
            // Beyond 7 days, it gets a constant 20% of the coefficient
            urgency += 2.4;
        }
    }

    // Blocking others (+8.0)
    if is_blocking {
        urgency += 8.0;
    }

    // Priority (Coefficient matches TaskWarrior's default multiplier)
    // Reference: https://taskwarrior.org/docs/priority/
    if let Some(priority) = task.get_value("priority") {
        match priority {
            "H" => urgency += 6.0,
            "M" => urgency += 3.9,
            "L" => urgency += 1.8,
            _ => {}
        }
    }

    // Scheduled (+5.0)
    if let Some(scheduled) = task.get_timestamp("scheduled") {
        if scheduled <= now {
            urgency += 5.0;
        }
    }

    // Active task (+4.0)
    if task.get_timestamp("start").is_some() {
        urgency += 4.0;
    }

    // Age (+2.0 coefficient)
    // Scales linearly to 1.0 (full coefficient) at 365 days
    if let Some(entry) = task.get_entry() {
        let age_days = (now - entry).num_days();
        urgency += 2.0 * (age_days.min(365) as f32 / 365.0);
    }

    // User tags (+1.0 if any exist)
    let tag_count = task.get_tags().filter(|t| t.is_user()).count();
    if tag_count > 0 {
        urgency += 1.0;
    }

    // Project presence (+1.0)
    if task.get_value("project").is_some() {
        urgency += 1.0;
    }

    // Waiting status (-3.0)
    if let Some(wait) = task.get_wait() {
        if wait > now {
            urgency -= 3.0;
        }
    }

    // Blocked by others (-5.0)
    if is_blocked {
        urgency -= 5.0;
    }

    urgency
}

fn map_task(task: Task, is_blocked: bool, is_blocking: bool) -> TaskData {
    let mut udas = Vec::new();
    for (key, value) in task.get_user_defined_attributes() {
        // Filter out keys that have dedicated fields in TaskData
        // and internal or redundant fields
        match key {
            "project" | "priority" | "description" | "status" | "wait" | "due" | "scheduled"
            | "start" | "entry" | "tags" | "dependencies" | "modified" | "end" => {}
            _ => {
                udas.push(UdaPair {
                    key: key.to_string(),
                    value: value.to_string(),
                });
            }
        }
    }

    let mut annotations: Vec<_> = task.get_annotations().collect();
    annotations.sort_by_key(|a| a.entry);
    let annotations: Vec<Annotation> = annotations
        .into_iter()
        .map(|a| Annotation {
            entry: a.entry.to_rfc3339(),
            description: a.description.to_string(),
        })
        .collect();

    TaskData {
        uuid: task.get_uuid().to_string(),
        description: task.get_description().to_string(),
        status: task.get_status().into(),
        tags: task.get_tags().map(|t| t.to_string()).collect(),
        due: task.get_due().map(|d| d.to_rfc3339()),
        entry: task.get_entry().map(|d| d.to_rfc3339()),
        project: task
            .get_value("project")
            .map(|p| p.to_string())
            .filter(|p| !p.is_empty()),
        wait: task.get_wait().map(|d| d.to_rfc3339()),
        scheduled: task.get_timestamp("scheduled").map(|d| d.to_rfc3339()),
        start: task.get_timestamp("start").map(|d| d.to_rfc3339()),
        priority: task.get_value("priority").map(|p| p.to_string()),
        urgency: compute_task_urgency(&task, is_blocked, is_blocking),
        is_blocked,
        is_blocking,
        dependencies: task.get_dependencies().map(|u| u.to_string()).collect(),
        udas,
        annotations,
    }
}

#[uniffi::export]
pub fn hello() -> String {
    "Hello TaskChampion".into()
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn test_in_memory_replica() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper
            .add_task(TaskAddProps {
                description: "Test task".into(),
                project: None,
                tags: vec![],
                wait: None,
                due: None,
                scheduled: None,
                start: None,
                priority: None,
                dependencies: vec![],
            })
            .unwrap();
        assert_eq!(task.description, "Test task");
        assert_eq!(task.status, TaskStatus::Pending);

        let tasks = wrapper.all_task_data().unwrap();
        assert_eq!(tasks.len(), 1);
        assert_eq!(tasks[0].description, "Test task");
    }

    #[test]
    fn test_on_disk_replica() {
        let tmp_dir = tempdir().unwrap();
        let db_path = tmp_dir
            .path()
            .join("tasks.db")
            .to_str()
            .unwrap()
            .to_string();

        {
            let wrapper = ReplicaWrapper::new_on_disk(db_path.clone()).unwrap();
            wrapper
                .add_task(TaskAddProps {
                    description: "Disk task".into(),
                    project: None,
                    tags: vec![],
                    wait: None,
                    due: None,
                    scheduled: None,
                    start: None,
                    priority: None,
                    dependencies: vec![],
                })
                .unwrap();
        }

        {
            let wrapper = ReplicaWrapper::new_on_disk(db_path).unwrap();
            let tasks = wrapper.all_task_data().unwrap();
            assert_eq!(tasks.len(), 1);
            assert_eq!(tasks[0].description, "Disk task");
        }
    }

    #[test]
    fn test_update_status() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper
            .add_task(TaskAddProps {
                description: "To complete".into(),
                project: None,
                tags: vec![],
                wait: None,
                due: None,
                scheduled: None,
                start: None,
                priority: None,
                dependencies: vec![],
            })
            .unwrap();
        wrapper
            .update_task_status(task.uuid.clone(), TaskStatus::Completed)
            .unwrap();

        let tasks = wrapper.all_task_data().unwrap();
        let updated_task = tasks.iter().find(|t| t.uuid == task.uuid).unwrap();
        assert_eq!(updated_task.status, TaskStatus::Completed);
    }

    #[test]
    fn test_priority_and_urgency() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();

        // High priority task
        let task_h = wrapper
            .add_task(TaskAddProps {
                description: "High".into(),
                project: None,
                tags: vec![],
                wait: None,
                due: None,
                scheduled: None,
                start: None,
                priority: Some("H".into()),
                dependencies: vec![],
            })
            .unwrap();
        assert_eq!(task_h.priority, Some("H".into()));
        assert!(task_h.urgency >= 6.0);

        // Low priority task
        let task_l = wrapper
            .add_task(TaskAddProps {
                description: "Low".into(),
                project: None,
                tags: vec![],
                wait: None,
                due: None,
                scheduled: None,
                start: None,
                priority: Some("L".into()),
                dependencies: vec![],
            })
            .unwrap();
        assert_eq!(task_l.priority, Some("L".into()));
        assert!(task_l.urgency >= 1.8);
        assert!(task_h.urgency > task_l.urgency);
    }

    #[test]
    fn test_update_task() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper
            .add_task(TaskAddProps {
                description: "Original description".into(),
                project: None,
                tags: vec![],
                wait: None,
                due: None,
                scheduled: None,
                start: None,
                priority: None,
                dependencies: vec![],
            })
            .unwrap();

        wrapper
            .update_task(TaskUpdateProps {
                uuid: task.uuid.clone(),
                description: "Updated description".into(),
                status: TaskStatus::Pending,
                project: Some("NewProject".into()),
                tags: vec!["tag1".into(), "tag2".into()],
                due: Some("2026-12-25T12:00:00Z".into()),
                wait: Some("2026-12-25T12:00:00Z".into()),
                scheduled: Some("2026-12-01T12:00:00Z".into()),
                start: Some("2026-12-15T12:00:00Z".into()),
                priority: None,
                dependencies: vec![],
            })
            .unwrap();

        let updated_task = wrapper.get_task(task.uuid).unwrap().unwrap();
        assert_eq!(updated_task.description, "Updated description");
        assert_eq!(updated_task.project, Some("NewProject".into()));
        assert!(updated_task.tags.contains(&"tag1".into()));
        assert!(updated_task.tags.contains(&"tag2".into()));
        assert!(updated_task.tags.contains(&"PENDING".into()));
        assert!(updated_task.due.is_some());
        assert!(updated_task.wait.is_some());
        assert!(updated_task.scheduled.is_some());
    }

    #[test]
    fn test_start_timestamp() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let start_time = "2026-04-17T12:00:00Z";
        let expected_time = "2026-04-17T12:00:00+00:00";
        let task = wrapper
            .add_task(TaskAddProps {
                description: "Active task".into(),
                project: None,
                tags: vec![],
                wait: None,
                due: None,
                scheduled: None,
                start: Some(start_time.into()),
                priority: None,
                dependencies: vec![],
            })
            .unwrap();
        assert_eq!(task.start, Some(expected_time.into()));

        // Update to clear start
        wrapper
            .update_task(TaskUpdateProps {
                uuid: task.uuid.clone(),
                description: task.description.clone(),
                status: task.status,
                project: task.project.clone(),
                tags: task.tags.clone(),
                due: task.due.clone(),
                wait: task.wait.clone(),
                scheduled: task.scheduled.clone(),
                start: None,
                priority: None,
                dependencies: vec![],
            })
            .unwrap();
        let updated_task = wrapper.get_task(task.uuid).unwrap().unwrap();
        assert_eq!(updated_task.start, None);
    }

    #[test]
    fn test_invalid_date_format() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let result = wrapper.add_task(TaskAddProps {
            description: "Task with bad date".into(),
            project: None,
            tags: vec![],
            wait: None,
            due: Some("invalid-date".into()),
            scheduled: None,
            start: None,
            priority: None,
            dependencies: vec![],
        });
        assert!(result.is_err());
    }

    #[test]
    fn test_special_characters() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let description = "Emoji description: 🚀🔥";
        let project = "Project/Sub: 🏢";
        let task = wrapper
            .add_task(TaskAddProps {
                description: description.into(),
                project: Some(project.into()),
                tags: vec!["tag_✨".into()],
                wait: None,
                due: None,
                scheduled: None,
                start: None,
                priority: None,
                dependencies: vec![],
            })
            .unwrap();
        assert_eq!(task.description, description);
        assert_eq!(task.project, Some(project.into()));
        assert!(task.tags.contains(&"tag_✨".into()));
    }

    #[test]
    fn test_empty_description() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper
            .add_task(TaskAddProps {
                description: "".into(),
                project: None,
                tags: vec![],
                wait: None,
                due: None,
                scheduled: None,
                start: None,
                priority: None,
                dependencies: vec![],
            })
            .unwrap();
        assert_eq!(task.description, "");
    }

    #[test]
    fn test_long_description() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let long_desc = "a".repeat(1000);
        let task = wrapper
            .add_task(TaskAddProps {
                description: long_desc.clone(),
                project: None,
                tags: vec![],
                wait: None,
                due: None,
                scheduled: None,
                start: None,
                priority: None,
                dependencies: vec![],
            })
            .unwrap();
        assert_eq!(task.description, long_desc);
    }

    #[test]
    fn test_invalid_tag_format() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        // Tags cannot have spaces
        let result = wrapper.add_task(TaskAddProps {
            description: "Task with bad tag".into(),
            project: None,
            tags: vec!["tag with space".into()],
            wait: None,
            due: None,
            scheduled: None,
            start: None,
            priority: None,
            dependencies: vec![],
        });
        assert!(result.is_err());
    }

    #[test]
    fn test_get_invalid_uuid() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let result = wrapper.get_task("not-a-uuid".into());
        assert!(result.is_err());
    }

    #[test]
    fn test_remove_project() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper
            .add_task(TaskAddProps {
                description: "Task with project".into(),
                project: Some("InitialProject".into()),
                tags: vec![],
                wait: None,
                due: None,
                scheduled: None,
                start: None,
                priority: None,
                dependencies: vec![],
            })
            .unwrap();
        assert_eq!(task.project, Some("InitialProject".into()));

        // Update to remove project
        wrapper
            .update_task(TaskUpdateProps {
                uuid: task.uuid.clone(),
                description: task.description.clone(),
                status: task.status,
                project: None,
                tags: task.tags.clone(),
                due: task.due.clone(),
                wait: task.wait.clone(),
                scheduled: task.scheduled.clone(),
                start: task.start.clone(),
                priority: None,
                dependencies: vec![],
            })
            .unwrap();

        let updated_task = wrapper.get_task(task.uuid).unwrap().unwrap();
        assert_eq!(updated_task.project, None);
    }

    #[test]
    fn test_date_parsing_edge_cases() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();

        // Valid UTC date
        let task = wrapper
            .add_task(TaskAddProps {
                description: "Date test".into(),
                project: None,
                tags: vec![],
                wait: None,
                due: Some("2026-04-20T10:00:00Z".into()),
                scheduled: None,
                start: None,
                priority: None,
                dependencies: vec![],
            })
            .unwrap();
        assert_eq!(task.due, Some("2026-04-20T10:00:00+00:00".into()));

        // Valid offset date (should be converted to UTC)
        let task2 = wrapper
            .add_task(TaskAddProps {
                description: "Offset test".into(),
                project: None,
                tags: vec![],
                wait: None,
                due: Some("2026-04-20T10:00:00+02:00".into()),
                scheduled: None,
                start: None,
                priority: None,
                dependencies: vec![],
            })
            .unwrap();
        assert_eq!(task2.due, Some("2026-04-20T08:00:00+00:00".into()));

        // Invalid date should fail
        let result = wrapper.add_task(TaskAddProps {
            description: "Bad date".into(),
            project: None,
            tags: vec![],
            wait: None,
            due: Some("2026-13-45T00:00:00Z".into()),
            scheduled: None,
            start: None,
            priority: None,
            dependencies: vec![],
        });
        assert!(result.is_err());
    }

    #[test]
    fn test_udas_filtering() {
        let tmp_dir = tempdir().unwrap();
        let db_path = tmp_dir
            .path()
            .join("tasks_uda.db")
            .to_str()
            .unwrap()
            .to_string();
        let wrapper = ReplicaWrapper::new_on_disk(db_path).unwrap();

        let task = wrapper
            .add_task(TaskAddProps {
                description: "UDA test".into(),
                project: None,
                tags: vec!["tag1".into()],
                wait: None,
                due: None,
                scheduled: None,
                start: None,
                priority: None,
                dependencies: vec![],
            })
            .unwrap();

        // Check that tag1 is in tags, but not in UDAs
        assert!(task.tags.contains(&"tag1".into()));

        // Test that a custom UDA is included
        let mut replica = wrapper.inner.lock().unwrap();
        let mut ops = Operations::new();
        let uuid = Uuid::new_v4();
        let mut rust_task = wrapper
            .rt
            .block_on(replica.create_task(uuid, &mut ops))
            .unwrap();
        rust_task
            .set_value(
                "custom_uda".to_string(),
                Some("custom_value".to_string()),
                &mut ops,
            )
            .unwrap();
        wrapper.rt.block_on(replica.commit_operations(ops)).unwrap();

        let task_data = map_task(rust_task, false, false);
        let has_custom = task_data
            .udas
            .iter()
            .any(|u| u.key == "custom_uda" && u.value == "custom_value");
        assert!(
            has_custom,
            "UDAs should contain 'custom_uda': {:?}",
            task_data.udas
        );
    }

    #[test]
    fn test_sync_aws_invalid_bucket_errors_gracefully() {
        // We can't reach a real bucket in unit tests, but syncing against a
        // bogus endpoint must surface a TaskError rather than panic, exercising
        // the ServerConfig::Aws construction path.
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let result = wrapper.sync_aws(
            "fatto-nonexistent-bucket".into(),
            None,
            Some("http://127.0.0.1:1".into()),
            "AKIAEXAMPLE".into(),
            "secretkeyexample".into(),
            "encryption-secret".into(),
        );
        assert!(result.is_err());
    }

    /// Build a set of settings for a real AWS bucket, with `mutate` applied.
    fn aws_settings(mutate: impl FnOnce(&mut AwsSettings)) -> Result<AwsSettings> {
        let mut settings = AwsSettings {
            bucket: "fatto-tasks".into(),
            region: Some("eu-west-2".into()),
            endpoint_url: None,
            access_key_id: "AKIAIOSFODNN7EXAMPLE".into(),
            secret_access_key: "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY".into(),
            encryption_secret: "encryption-secret".into(),
        };
        mutate(&mut settings);
        AwsSettings::validate(
            settings.bucket,
            settings.region,
            settings.endpoint_url,
            settings.access_key_id,
            settings.secret_access_key,
            settings.encryption_secret,
        )
    }

    #[test]
    fn test_aws_settings_accepts_valid_aws_config() {
        let settings = aws_settings(|_| {}).unwrap();
        assert_eq!(settings.region.as_deref(), Some("eu-west-2"));
        assert_eq!(settings.endpoint_url, None);
    }

    #[test]
    fn test_aws_settings_trims_and_normalizes() {
        let settings = aws_settings(|s| {
            s.bucket = "  fatto-tasks\n".into();
            s.region = Some("  ".into());
            s.endpoint_url = Some(String::new());
            s.access_key_id = " AKIAIOSFODNN7EXAMPLE\n".into();
            s.secret_access_key = "\twJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY ".into();
        })
        .unwrap();
        assert_eq!(settings.bucket, "fatto-tasks");
        assert_eq!(settings.access_key_id, "AKIAIOSFODNN7EXAMPLE");
        assert_eq!(
            settings.secret_access_key,
            "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
        );
        assert_eq!(settings.region, None);
        assert_eq!(settings.endpoint_url, None);
    }

    #[test]
    fn test_aws_settings_rejects_missing_fields() {
        assert!(aws_settings(|s| s.bucket = " ".into()).is_err(), "bucket");
        assert!(
            aws_settings(|s| s.access_key_id = String::new()).is_err(),
            "access key id"
        );
        assert!(
            aws_settings(|s| s.secret_access_key = String::new()).is_err(),
            "secret access key"
        );
        assert!(
            aws_settings(|s| s.encryption_secret = " ".into()).is_err(),
            "encryption secret"
        );
    }

    #[test]
    fn test_aws_settings_rejects_whitespace_inside_credentials() {
        assert!(aws_settings(|s| s.access_key_id = "AKIAIOSF ODNN7EXAMPL".into()).is_err());
        assert!(aws_settings(
            |s| s.secret_access_key = "wJalrXUtnFEMI/K7MDENG/bPxRfiCY EXAMPLEKE".into()
        )
        .is_err());
    }

    #[test]
    fn test_aws_settings_rejects_malformed_aws_credentials() {
        // Escaped or quoted keys, the shapes users produce when they assume the
        // field needs shell-style quoting.
        assert!(aws_settings(|s| s.access_key_id = "\"AKIAIOSFODNN7EXAMPLE\"".into()).is_err());
        assert!(aws_settings(
            |s| s.secret_access_key = "wJalrXUtnFEMI\\/K7MDENG\\/bPxRfiCYEXAMPLEKEY".into()
        )
        .is_err());
        // Temporary credentials need a session token, which is not supported.
        assert!(aws_settings(|s| s.access_key_id = "ASIAIOSFODNN7EXAMPLE".into()).is_err());
    }

    #[test]
    fn test_aws_settings_checks_key_shape_only_for_aws() {
        // minio's default credentials are neither 20 nor 40 characters.
        let settings = aws_settings(|s| {
            s.endpoint_url = Some("http://localhost:9000".into());
            s.region = None;
            s.access_key_id = "minioadmin".into();
            s.secret_access_key = "minioadmin".into();
        })
        .unwrap();
        assert_eq!(
            settings.endpoint_url.as_deref(),
            Some("http://localhost:9000")
        );
    }

    #[test]
    fn test_aws_settings_requires_endpoint_scheme() {
        assert!(aws_settings(|s| s.endpoint_url = Some("minio.example.com".into())).is_err());
        assert!(
            aws_settings(|s| s.endpoint_url = Some("https://minio.example.com".into())).is_ok()
        );
    }

    #[test]
    fn test_aws_settings_validates_region_names() {
        for region in ["us-east-1", "eu-west-2", "us-gov-east-1", "cn-northwest-1"] {
            assert!(
                aws_settings(|s| s.region = Some(region.into())).is_ok(),
                "{region} should be accepted"
            );
        }
        // The mistake from the bug report: a region without its number.
        for region in ["eu-west", "EU-WEST-2", "eu_west_2", "eu-west-", "europe"] {
            assert!(
                aws_settings(|s| s.region = Some(region.into())).is_err(),
                "{region} should be rejected"
            );
        }
        // S3-compatible services pick their own region names.
        assert!(aws_settings(|s| {
            s.endpoint_url = Some("http://localhost:9000".into());
            s.region = Some("garage".into());
        })
        .is_ok());
    }

    #[test]
    fn test_aws_settings_validates_bucket_names() {
        assert!(aws_settings(|s| s.bucket = "My.Tasks".into()).is_err());
        assert!(aws_settings(|s| s.bucket = "ab".into()).is_err());
        assert!(aws_settings(|s| s.bucket = "-tasks".into()).is_err());
        assert!(aws_settings(|s| s.bucket = "tasks..backup".into()).is_err());
        assert!(aws_settings(|s| s.bucket = "tasks.backup-1".into()).is_ok());
    }

    #[test]
    fn test_explain_aws_error_adds_hints() {
        let explained = explain_aws_error("unhandled error (SignatureDoesNotMatch)");
        assert!(explained.contains("access key ID and secret access key"));
        assert!(explained.contains("SignatureDoesNotMatch"));

        // Unknown errors are passed through untouched.
        assert_eq!(
            explain_aws_error("some other failure"),
            "some other failure"
        );
    }

    #[test]
    fn test_add_annotation() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper
            .add_task(TaskAddProps {
                description: "Annotated task".into(),
                project: None,
                tags: vec![],
                wait: None,
                due: None,
                scheduled: None,
                start: None,
                priority: None,
                dependencies: vec![],
            })
            .unwrap();

        let annotation = wrapper
            .add_annotation(task.uuid.clone(), "First note".into())
            .unwrap();
        assert_eq!(annotation.description, "First note");
        assert!(!annotation.entry.is_empty());

        let updated = wrapper.get_task(task.uuid).unwrap().unwrap();
        assert_eq!(updated.annotations.len(), 1);
        assert_eq!(updated.annotations[0].description, "First note");
    }

    #[test]
    fn test_remove_annotation() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper
            .add_task(TaskAddProps {
                description: "Task with notes".into(),
                project: None,
                tags: vec![],
                wait: None,
                due: None,
                scheduled: None,
                start: None,
                priority: None,
                dependencies: vec![],
            })
            .unwrap();

        let ann = wrapper
            .add_annotation(task.uuid.clone(), "Note to remove".into())
            .unwrap();
        assert_eq!(
            wrapper
                .get_task(task.uuid.clone())
                .unwrap()
                .unwrap()
                .annotations
                .len(),
            1
        );

        wrapper
            .remove_annotation(task.uuid.clone(), ann.entry.clone())
            .unwrap();
        let updated = wrapper.get_task(task.uuid).unwrap().unwrap();
        assert_eq!(updated.annotations.len(), 0);
    }

    #[test]
    fn test_annotations_in_task_data() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper
            .add_task(TaskAddProps {
                description: "Multi-note task".into(),
                project: None,
                tags: vec![],
                wait: None,
                due: None,
                scheduled: None,
                start: None,
                priority: None,
                dependencies: vec![],
            })
            .unwrap();

        wrapper
            .add_annotation(task.uuid.clone(), "Note 1".into())
            .unwrap();

        let after_first = wrapper.get_task(task.uuid.clone()).unwrap().unwrap();
        assert_eq!(after_first.annotations.len(), 1);

        wrapper
            .add_annotation(task.uuid.clone(), "Note 2".into())
            .unwrap();

        let after_second = wrapper.get_task(task.uuid.clone()).unwrap().unwrap();
        assert_eq!(after_second.annotations.len(), 2);
        let descriptions: Vec<&str> = after_second
            .annotations
            .iter()
            .map(|a| a.description.as_str())
            .collect();
        assert!(descriptions.contains(&"Note 1"));
        assert!(descriptions.contains(&"Note 2"));
    }
}
