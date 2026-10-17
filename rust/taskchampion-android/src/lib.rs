mod recurrence;
mod review;
#[cfg(test)]
mod review_tests;
use chrono::{DateTime, Utc};
pub use recurrence::{RecurrenceData, RecurrenceDiagnostic, RecurrenceRule, RecurrenceRulePatch};
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
    #[error("Task {uuid} changed: {fields:?}")]
    EditConflict { uuid: String, fields: Vec<String> },
}

impl From<taskchampion::Error> for TaskError {
    fn from(err: taskchampion::Error) -> Self {
        TaskError::Internal(err.to_string())
    }
}

#[derive(uniffi::Enum, PartialEq, Debug, Clone)]
pub enum TaskStatus {
    Pending,
    Completed,
    Deleted,
    Recurring,
    Unknown { value: String },
}

impl From<taskchampion::Status> for TaskStatus {
    fn from(status: taskchampion::Status) -> Self {
        match status {
            taskchampion::Status::Pending => TaskStatus::Pending,
            taskchampion::Status::Completed => TaskStatus::Completed,
            taskchampion::Status::Deleted => TaskStatus::Deleted,
            taskchampion::Status::Recurring => TaskStatus::Recurring,
            taskchampion::Status::Unknown(value) => TaskStatus::Unknown { value },
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
            TaskStatus::Unknown { value } => taskchampion::Status::Unknown(value),
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
    pub recurrence: Option<RecurrenceData>,
    pub native_dates: Vec<UdaPair>,
    pub modified: Option<String>,
}

#[derive(uniffi::Enum, Debug, Clone)]
pub enum StringEdit {
    Unchanged,
    Replace {
        expected: Option<String>,
        value: Option<String>,
    },
}

#[derive(uniffi::Enum, Debug, Clone)]
pub enum StringSetEdit {
    Unchanged,
    Replace {
        expected: Vec<String>,
        value: Vec<String>,
    },
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct TaskEditPatch {
    pub uuid: String,
    pub expected_status: TaskStatus,
    pub description: StringEdit,
    pub project: StringEdit,
    pub priority: StringEdit,
    pub due: StringEdit,
    pub wait: StringEdit,
    pub scheduled: StringEdit,
    pub start: StringEdit,
    pub tags: StringSetEdit,
    pub dependencies: StringSetEdit,
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

    pub fn add_recurring_task(
        &self,
        props: TaskAddProps,
        rule: RecurrenceRule,
    ) -> Result<TaskData> {
        if !recurrence::creation_interval(&rule.interval) {
            return Err(TaskError::Internal(
                "Use a supported positive repeat interval".into(),
            ));
        }
        if props.start.is_some() {
            return Err(TaskError::Internal(
                "Series templates cannot be started".into(),
            ));
        }
        let mut replica = self.inner.lock().unwrap();
        let mut ops = Operations::new();
        let mut task = self
            .rt
            .block_on(replica.create_task(Uuid::new_v4(), &mut ops))?;
        task.set_description(props.description, &mut ops)?;
        task.set_status(taskchampion::Status::Recurring, &mut ops)?;
        task.set_entry(Some(Utc::now()), &mut ops)?;
        task.set_value("rtype", Some("periodic".into()), &mut ops)?;
        task.set_value("recur", Some(rule.interval), &mut ops)?;
        task.set_value(
            "until",
            parse_rfc3339(rule.until)?.map(|d| d.timestamp().to_string()),
            &mut ops,
        )?;
        task.set_value("project", props.project, &mut ops)?;
        task.set_value("priority", props.priority, &mut ops)?;
        for raw in props.tags {
            let tag = Tag::from_str(&raw).map_err(|e| TaskError::Internal(e.to_string()))?;
            if tag.is_user() {
                task.add_tag(&tag, &mut ops)?;
            }
        }
        for raw in props.dependencies {
            task.add_dependency(
                Uuid::parse_str(&raw).map_err(|e| TaskError::Internal(e.to_string()))?,
                &mut ops,
            )?;
        }
        task.set_due(parse_rfc3339(props.due)?, &mut ops)?;
        task.set_wait(parse_rfc3339(props.wait)?, &mut ops)?;
        task.set_timestamp("scheduled", parse_rfc3339(props.scheduled)?, &mut ops)?;
        recurrence::validate_schedule(&task)?;
        self.rt.block_on(replica.commit_operations(ops))?;
        let blocked = task.is_blocked();
        let blocking = task.is_blocking();
        Ok(map_task(task, blocked, blocking))
    }

    pub fn update_recurrence_template(
        &self,
        patch: TaskEditPatch,
        rule_patch: RecurrenceRulePatch,
    ) -> Result<()> {
        let mut replica = self.inner.lock().unwrap();
        let id = Uuid::parse_str(&patch.uuid).map_err(|e| TaskError::Internal(e.to_string()))?;
        let tasks = self.rt.block_on(replica.all_tasks())?;
        let mut task = tasks
            .get(&id)
            .cloned()
            .ok_or_else(|| TaskError::Internal("Series not found".into()))?;
        recurrence::template_source(&task)?;
        if !matches!(patch.start, StringEdit::Unchanged) {
            return Err(TaskError::Internal(
                "Series templates cannot be started".into(),
            ));
        }
        let before: Vec<_> = ["recur", "until", "due", "wait", "scheduled"]
            .iter()
            .map(|k| task.get_value(*k).map(str::to_owned))
            .collect();
        let mut ops = Operations::new();
        apply_edit_patch(&mut task, patch, true, &mut ops)?;
        recurrence::apply_rule_patch(&mut task, rule_patch, &mut ops)?;
        let after: Vec<_> = ["recur", "until", "due", "wait", "scheduled"]
            .iter()
            .map(|k| task.get_value(*k).map(str::to_owned))
            .collect();
        if before != after {
            if tasks[&id].get_value("mask").is_some() || tasks.values().any(|child| {
                child.get_value("parent").is_some_and(|p| Uuid::parse_str(p).ok()==Some(id))
                // An unlinked indexed record makes the generation state uncertain.
                || (child.get_value("imask").is_some() && child.get_value("parent").is_none_or(|p| Uuid::parse_str(p).is_err()))
            }) {return Err(TaskError::Internal("This series has generated occurrences or uncertain generation metadata; use Replace schedule".into()));}
            recurrence::validate_schedule(&task)?;
        }
        if !ops.is_empty() {
            self.rt.block_on(replica.commit_operations(ops))?;
        }
        Ok(())
    }

    pub fn stop_recurrence(&self, template_uuid: String) -> Result<()> {
        let mut replica = self.inner.lock().unwrap();
        let id = Uuid::parse_str(&template_uuid).map_err(|e| TaskError::Internal(e.to_string()))?;
        let mut task = self
            .rt
            .block_on(replica.get_task(id))?
            .ok_or_else(|| TaskError::Internal("Series not found".into()))?;
        recurrence::template_source(&task)?;
        let mut ops = Operations::new();
        task.set_status(taskchampion::Status::Deleted, &mut ops)?;
        self.rt.block_on(replica.commit_operations(ops))?;
        Ok(())
    }

    pub fn replace_recurrence(
        &self,
        old_template_uuid: String,
        patch: TaskEditPatch,
        rule_patch: RecurrenceRulePatch,
    ) -> Result<TaskData> {
        if patch.uuid != old_template_uuid {
            return Err(TaskError::Internal(
                "Replacement source does not match the edited series".into(),
            ));
        }
        let mut replica = self.inner.lock().unwrap();
        let id =
            Uuid::parse_str(&old_template_uuid).map_err(|e| TaskError::Internal(e.to_string()))?;
        let mut source = self
            .rt
            .block_on(replica.get_task(id))?
            .ok_or_else(|| TaskError::Internal("Series not found".into()))?;
        recurrence::template_source(&source)?;
        if !matches!(patch.start, StringEdit::Unchanged) {
            return Err(TaskError::Internal(
                "Series templates cannot be started".into(),
            ));
        }
        let mut draft = source.clone();
        let mut validation_ops = Operations::new();
        apply_edit_patch(&mut draft, patch, true, &mut validation_ops)?;
        recurrence::apply_rule_patch(&mut draft, rule_patch, &mut validation_ops)?;
        recurrence::validate_schedule(&draft)?;
        let properties: Vec<_> = draft
            .into_task_data()
            .iter()
            .map(|(k, v)| (k.clone(), v.clone()))
            .collect();
        let mut ops = Operations::new();
        let mut replacement = self
            .rt
            .block_on(replica.create_task(Uuid::new_v4(), &mut ops))?;
        for (key, value) in properties {
            if ![
                "status", "entry", "modified", "end", "start", "mask", "parent", "imask", "last",
                "template",
            ]
            .contains(&key.as_str())
            {
                replacement.set_value(key, Some(value), &mut ops)?;
            }
        }
        replacement.set_status(taskchampion::Status::Recurring, &mut ops)?;
        replacement.set_value("rtype", Some("periodic".into()), &mut ops)?;
        let now = Utc::now();
        replacement.set_entry(Some(now), &mut ops)?;
        replacement.set_modified(now, &mut ops)?;
        source.set_status(taskchampion::Status::Deleted, &mut ops)?;
        self.rt.block_on(replica.commit_operations(ops))?;
        let blocked = replacement.is_blocked();
        let blocking = replacement.is_blocking();
        Ok(map_task(replacement, blocked, blocking))
    }

    pub fn recurrence_diagnostics(&self) -> Result<Vec<RecurrenceDiagnostic>> {
        let mut replica = self.inner.lock().unwrap();
        let tasks = self.rt.block_on(replica.all_tasks())?;
        Ok(recurrence::diagnostics(&tasks, Utc::now()))
    }

    pub fn update_task_status(&self, uuid: String, status: TaskStatus) -> Result<()> {
        self.transition_occurrence(uuid, status, false)
    }

    pub fn transition_occurrence(
        &self,
        uuid: String,
        status: TaskStatus,
        stop_active: bool,
    ) -> Result<()> {
        if matches!(status, TaskStatus::Recurring | TaskStatus::Unknown { .. }) {
            return Err(TaskError::Internal(
                "Ordinary transitions cannot create a recurring or unknown status".into(),
            ));
        }
        let mut replica = self.inner.lock().unwrap();
        let id = Uuid::parse_str(&uuid).map_err(|_| TaskError::Internal("Invalid UUID".into()))?;
        let mut tasks = self.rt.block_on(replica.all_tasks())?;
        let task = tasks
            .get_mut(&id)
            .ok_or_else(|| TaskError::Internal("Task not found".into()))?;
        if recurrence::is_template(task)
            || matches!(task.get_status(), taskchampion::Status::Unknown(_))
        {
            return Err(TaskError::Internal(
                "Series templates and unknown statuses cannot use ordinary task actions".into(),
            ));
        }
        let mut ops = Operations::new();
        if TaskStatus::from(task.get_status()) != status {
            task.set_status(status.into(), &mut ops)?;
        }
        if stop_active && task.get_value("start").is_some() {
            task.set_timestamp("start", None, &mut ops)?;
        }
        recurrence::append_repairs(&tasks, Utc::now(), &mut ops)?;
        if !ops.is_empty() {
            self.rt.block_on(replica.commit_operations(ops))?;
        }
        Ok(())
    }

    pub fn update_task(&self, patch: TaskEditPatch) -> Result<()> {
        let mut replica = self.inner.lock().unwrap();
        let id =
            Uuid::parse_str(&patch.uuid).map_err(|_| TaskError::Internal("Invalid UUID".into()))?;
        let mut tasks = self.rt.block_on(replica.all_tasks())?;
        let task = tasks
            .get_mut(&id)
            .ok_or_else(|| TaskError::Internal("Task not found".into()))?;
        let mut ops = Operations::new();
        apply_edit_patch(task, patch, false, &mut ops)?;
        // A content-only no-op must not create repair or modified operations.
        if !ops.is_empty() {
            recurrence::append_repairs(&tasks, Utc::now(), &mut ops)?;
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
            replica.sync(&mut server, false).await?;
            if recurrence::reconcile_masks(&mut replica, Utc::now()).await? {
                replica.sync(&mut server, false).await?;
            }
            Ok::<(), TaskError>(())
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
        let mut replica = self.inner.lock().unwrap();

        // Normalize empty optional strings coming across the FFI boundary to `None`.
        let region = region.filter(|s| !s.is_empty());
        let endpoint_url = endpoint_url.filter(|s| !s.is_empty());

        let config = ServerConfig::Aws {
            // S3-compatible endpoints generally require path-style addressing; real
            // AWS S3 (no custom endpoint) keeps the default virtual-hosted style.
            force_path_style: endpoint_url.is_some(),
            region,
            bucket,
            endpoint_url,
            credentials: AwsCredentials::AccessKey {
                access_key_id,
                secret_access_key,
            },
            encryption_secret: encryption_secret.into_bytes(),
        };
        self.rt.block_on(async {
            let mut server = config.into_server().await?;
            replica.sync(&mut server, false).await?;
            if recurrence::reconcile_masks(&mut replica, Utc::now()).await? {
                replica.sync(&mut server, false).await?;
            }
            Ok::<(), TaskError>(())
        })?;
        Ok(())
    }
}

fn apply_edit_patch(
    task: &mut Task,
    patch: TaskEditPatch,
    allow_template_dates: bool,
    ops: &mut Operations,
) -> Result<()> {
    let mut conflicts = Vec::new();
    if TaskStatus::from(task.get_status()) != patch.expected_status {
        conflicts.push("status".into());
    }
    let fields = [
        ("description", &patch.description),
        ("project", &patch.project),
        ("priority", &patch.priority),
        ("due", &patch.due),
        ("wait", &patch.wait),
        ("scheduled", &patch.scheduled),
        ("start", &patch.start),
    ];
    let mut scalar_changes = Vec::new();
    for (key, edit) in fields {
        if let StringEdit::Replace { expected, value } = edit {
            let date = matches!(key, "due" | "wait" | "scheduled" | "start");
            if date && recurrence::is_template(task) && !allow_template_dates {
                return Err(TaskError::Internal(
                    "Series schedules are read-only in this version".into(),
                ));
            }
            if key == "description" && value.is_none() {
                return Err(TaskError::Internal("Description cannot be cleared".into()));
            }
            let current = task.get_value(key).map(str::to_owned);
            let desired = if date {
                parse_rfc3339(value.clone())?.map(|d| d.timestamp().to_string())
            } else {
                value.clone()
            };
            let matches = if date {
                match (current.as_deref(), expected.as_deref()) {
                    (None, None) => true,
                    (Some(raw), Some(expected)) => {
                        raw == expected
                            || recurrence::timestamp(task, key)
                                .zip(parse_rfc3339(Some(expected.into())).ok().flatten())
                                .is_some_and(|(a, b)| a == b)
                    }
                    _ => false,
                }
            } else if key == "project" {
                // TaskData represents an imported empty project as absent.
                current.as_deref().filter(|v| !v.is_empty())
                    == expected.as_deref().filter(|v| !v.is_empty())
            } else {
                current == *expected
            };
            if !matches {
                conflicts.push(key.into());
            }
            let same_instant = date
                && recurrence::timestamp(task, key)
                    .zip(parse_rfc3339(value.clone())?)
                    .is_some_and(|(current, desired)| current == desired);
            let same_project = key == "project"
                && current.as_deref().filter(|v| !v.is_empty())
                    == desired.as_deref().filter(|v| !v.is_empty());
            if current != desired && !same_instant && !same_project {
                scalar_changes.push((key, desired));
            }
        }
    }
    let current_tags: Vec<String> = stored_tags(task)
        .into_iter()
        .filter(|t| t.is_user())
        .map(|t| t.to_string())
        .collect();
    let current_deps: Vec<String> = task.get_dependencies().map(|d| d.to_string()).collect();
    for (key, edit, current) in [
        ("tags", &patch.tags, &current_tags),
        ("dependencies", &patch.dependencies, &current_deps),
    ] {
        if let StringSetEdit::Replace { expected, value } = edit {
            if normalize_set(expected, key)? != normalize_set(current, key)? {
                conflicts.push(key.into());
            }
            normalize_set(value, key)?;
        }
    }
    if !conflicts.is_empty() {
        return Err(TaskError::EditConflict {
            uuid: patch.uuid,
            fields: conflicts,
        });
    }
    for (key, value) in scalar_changes {
        task.set_value(key, value, ops)?;
    }
    if let StringSetEdit::Replace { value, .. } = patch.tags {
        let desired = normalize_set(&value, "tags")?;
        let current = normalize_set(&current_tags, "tags")?;
        for raw in current.difference(&desired) {
            task.remove_tag(
                &Tag::from_str(raw).map_err(|e| TaskError::Internal(e.to_string()))?,
                ops,
            )?;
        }
        for raw in desired.difference(&current) {
            task.add_tag(
                &Tag::from_str(raw).map_err(|e| TaskError::Internal(e.to_string()))?,
                ops,
            )?;
        }
    }
    if let StringSetEdit::Replace { value, .. } = patch.dependencies {
        let desired = normalize_set(&value, "dependencies")?;
        let current = normalize_set(&current_deps, "dependencies")?;
        for raw in current.difference(&desired) {
            task.remove_dependency(Uuid::parse_str(raw).unwrap(), ops)?;
        }
        for raw in desired.difference(&current) {
            task.add_dependency(Uuid::parse_str(raw).unwrap(), ops)?;
        }
    }
    Ok(())
}

fn normalize_set(values: &[String], key: &str) -> Result<std::collections::BTreeSet<String>> {
    values
        .iter()
        .filter_map(|raw| {
            if key == "tags" {
                match Tag::from_str(raw) {
                    Ok(tag) if tag.is_user() => Some(Ok(tag.to_string())),
                    Ok(_) => None,
                    Err(e) => Some(Err(TaskError::Internal(e.to_string()))),
                }
            } else {
                Some(
                    Uuid::parse_str(raw)
                        .map(|u| u.to_string())
                        .map_err(|e| TaskError::Internal(e.to_string())),
                )
            }
        })
        .collect()
}

fn compute_task_urgency(task: &Task, is_blocked: bool, is_blocking: bool) -> f32 {
    // Reference coefficients: https://taskwarrior.org/docs/urgency/
    // This implementation follows the default weights of TaskWarrior.
    let mut urgency = 0.0;
    let now = Utc::now();

    // Next tag (+15.0)
    if stored_tags(task).iter().any(|t| t.to_string() == "next") {
        urgency += 15.0;
    }

    // Due date (+12.0 coefficient)
    // Reference: https://taskwarrior.org/docs/urgency/
    if let Some(due) = recurrence::timestamp(task, "due") {
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
    if let Some(scheduled) = recurrence::timestamp(task, "scheduled") {
        if scheduled <= now {
            urgency += 5.0;
        }
    }

    // Active task (+4.0)
    if recurrence::timestamp(task, "start").is_some() {
        urgency += 4.0;
    }

    // Age (+2.0 coefficient)
    // Scales linearly to 1.0 (full coefficient) at 365 days
    if let Some(entry) = recurrence::timestamp(task, "entry") {
        let age_days = (now - entry).num_days();
        urgency += 2.0 * (age_days.min(365) as f32 / 365.0);
    }

    // User tags (+1.0 if any exist)
    let tag_count = stored_tags(task).iter().filter(|t| t.is_user()).count();
    if tag_count > 0 {
        urgency += 1.0;
    }

    // Project presence (+1.0)
    if task.get_value("project").is_some() {
        urgency += 1.0;
    }

    // Waiting status (-3.0)
    if let Some(wait) = recurrence::timestamp(task, "wait") {
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

fn stored_tags(task: &Task) -> Vec<Tag> {
    task.clone()
        .into_task_data()
        .iter()
        .filter_map(|(key, _)| {
            key.strip_prefix("tag_")
                .and_then(|tag| Tag::from_str(tag).ok())
        })
        .collect()
}

fn safe_tags(task: &Task) -> Vec<Tag> {
    let mut tags = stored_tags(task);
    for raw in [
        "WAITING",
        "ACTIVE",
        "PENDING",
        "COMPLETED",
        "DELETED",
        "BLOCKED",
        "UNBLOCKED",
        "BLOCKING",
    ] {
        let tag = Tag::from_str(raw).unwrap();
        let present = if raw == "WAITING" {
            recurrence::timestamp(task, "wait").is_some_and(|wait| wait > Utc::now())
        } else {
            task.has_tag(&tag)
        };
        if present {
            tags.push(tag);
        }
    }
    tags
}

fn map_task(task: Task, is_blocked: bool, is_blocking: bool) -> TaskData {
    let mut udas = Vec::new();
    for (key, value) in task.get_user_defined_attributes() {
        // Filter out keys that have dedicated fields in TaskData
        // and internal or redundant fields
        match key {
            "project" | "priority" | "description" | "status" | "wait" | "due" | "scheduled"
            | "start" | "entry" | "tags" | "dependencies" | "modified" | "end" | "recur"
            | "rtype" | "parent" | "imask" | "mask" | "until" | "last" | "template" => {}
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
        tags: safe_tags(&task).iter().map(|t| t.to_string()).collect(),
        due: recurrence::timestamp(&task, "due").map(|d| d.to_rfc3339()),
        entry: recurrence::timestamp(&task, "entry").map(|d| d.to_rfc3339()),
        project: task
            .get_value("project")
            .map(|p| p.to_string())
            .filter(|p| !p.is_empty()),
        wait: recurrence::timestamp(&task, "wait").map(|d| d.to_rfc3339()),
        scheduled: recurrence::timestamp(&task, "scheduled").map(|d| d.to_rfc3339()),
        start: recurrence::timestamp(&task, "start").map(|d| d.to_rfc3339()),
        priority: task.get_value("priority").map(|p| p.to_string()),
        urgency: compute_task_urgency(&task, is_blocked, is_blocking),
        is_blocked,
        is_blocking,
        dependencies: task.get_dependencies().map(|u| u.to_string()).collect(),
        udas,
        annotations,
        recurrence: recurrence::read_recurrence(&task),
        modified: recurrence::timestamp(&task, "modified").map(|d| d.to_rfc3339()),
        native_dates: ["due", "wait", "scheduled", "start"]
            .iter()
            .filter_map(|key| {
                task.get_value(*key).map(|value| UdaPair {
                    key: (*key).into(),
                    value: value.into(),
                })
            })
            .collect(),
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
            .update_task({
                let current = wrapper.get_task(task.uuid.clone()).unwrap().unwrap();
                TaskEditPatch {
                    uuid: task.uuid.clone(),
                    expected_status: current.status.clone(),
                    description: StringEdit::Replace {
                        expected: Some(current.description.clone()),
                        value: Some("Updated description".into()),
                    },
                    project: StringEdit::Replace {
                        expected: current.project.clone(),
                        value: Some("NewProject".into()),
                    },
                    priority: StringEdit::Replace {
                        expected: current.priority.clone(),
                        value: None,
                    },
                    due: StringEdit::Replace {
                        expected: current.due.clone(),
                        value: Some("2026-12-25T12:00:00Z".into()),
                    },
                    wait: StringEdit::Replace {
                        expected: current.wait.clone(),
                        value: Some("2026-12-25T12:00:00Z".into()),
                    },
                    scheduled: StringEdit::Replace {
                        expected: current.scheduled.clone(),
                        value: Some("2026-12-01T12:00:00Z".into()),
                    },
                    start: StringEdit::Replace {
                        expected: current.start.clone(),
                        value: Some("2026-12-15T12:00:00Z".into()),
                    },
                    tags: StringSetEdit::Replace {
                        expected: current.tags.clone(),
                        value: vec!["tag1".into(), "tag2".into()],
                    },
                    dependencies: StringSetEdit::Replace {
                        expected: current.dependencies.clone(),
                        value: vec![],
                    },
                }
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
            .update_task({
                let current = wrapper.get_task(task.uuid.clone()).unwrap().unwrap();
                TaskEditPatch {
                    uuid: task.uuid.clone(),
                    expected_status: current.status.clone(),
                    description: StringEdit::Replace {
                        expected: Some(current.description.clone()),
                        value: Some(task.description.clone()),
                    },
                    project: StringEdit::Replace {
                        expected: current.project.clone(),
                        value: task.project.clone(),
                    },
                    priority: StringEdit::Replace {
                        expected: current.priority.clone(),
                        value: None,
                    },
                    due: StringEdit::Replace {
                        expected: current.due.clone(),
                        value: task.due.clone(),
                    },
                    wait: StringEdit::Replace {
                        expected: current.wait.clone(),
                        value: task.wait.clone(),
                    },
                    scheduled: StringEdit::Replace {
                        expected: current.scheduled.clone(),
                        value: task.scheduled.clone(),
                    },
                    start: StringEdit::Replace {
                        expected: current.start.clone(),
                        value: None,
                    },
                    tags: StringSetEdit::Replace {
                        expected: current.tags.clone(),
                        value: task.tags.clone(),
                    },
                    dependencies: StringSetEdit::Replace {
                        expected: current.dependencies.clone(),
                        value: vec![],
                    },
                }
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
            .update_task({
                let current = wrapper.get_task(task.uuid.clone()).unwrap().unwrap();
                TaskEditPatch {
                    uuid: task.uuid.clone(),
                    expected_status: current.status.clone(),
                    description: StringEdit::Replace {
                        expected: Some(current.description.clone()),
                        value: Some(task.description.clone()),
                    },
                    project: StringEdit::Replace {
                        expected: current.project.clone(),
                        value: None,
                    },
                    priority: StringEdit::Replace {
                        expected: current.priority.clone(),
                        value: None,
                    },
                    due: StringEdit::Replace {
                        expected: current.due.clone(),
                        value: task.due.clone(),
                    },
                    wait: StringEdit::Replace {
                        expected: current.wait.clone(),
                        value: task.wait.clone(),
                    },
                    scheduled: StringEdit::Replace {
                        expected: current.scheduled.clone(),
                        value: task.scheduled.clone(),
                    },
                    start: StringEdit::Replace {
                        expected: current.start.clone(),
                        value: task.start.clone(),
                    },
                    tags: StringSetEdit::Replace {
                        expected: current.tags.clone(),
                        value: task.tags.clone(),
                    },
                    dependencies: StringSetEdit::Replace {
                        expected: current.dependencies.clone(),
                        value: vec![],
                    },
                }
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
    #[test]
    fn test_dependency_at_creation() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let blocker = wrapper
            .add_task(TaskAddProps {
                description: "Blocker".into(),
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
        let blocked = wrapper
            .add_task(TaskAddProps {
                description: "Blocked task".into(),
                project: None,
                tags: vec![],
                wait: None,
                due: None,
                scheduled: None,
                start: None,
                priority: None,
                dependencies: vec![blocker.uuid.clone()],
            })
            .unwrap();

        // NOTE: re-read from the replica — the TaskData returned by add_task
        // carries a stale depmap (see Global Constraints).
        let blocked = wrapper.get_task(blocked.uuid.clone()).unwrap().unwrap();
        let blocker = wrapper.get_task(blocker.uuid.clone()).unwrap().unwrap();
        assert_eq!(blocked.dependencies, vec![blocker.uuid.clone()]);
        assert!(blocked.is_blocked);
        assert!(blocker.is_blocking);
    }

    #[test]
    fn test_add_and_remove_dependency() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task_a = wrapper
            .add_task(TaskAddProps {
                description: "A".into(),
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
        let task_b = wrapper
            .add_task(TaskAddProps {
                description: "B".into(),
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

        // A blocked by B
        wrapper
            .update_task({
                let current = wrapper.get_task(task_a.uuid.clone()).unwrap().unwrap();
                TaskEditPatch {
                    uuid: task_a.uuid.clone(),
                    expected_status: current.status.clone(),
                    description: StringEdit::Replace {
                        expected: Some(current.description.clone()),
                        value: Some(task_a.description.clone()),
                    },
                    project: StringEdit::Replace {
                        expected: current.project.clone(),
                        value: task_a.project.clone(),
                    },
                    priority: StringEdit::Replace {
                        expected: current.priority.clone(),
                        value: task_a.priority.clone(),
                    },
                    due: StringEdit::Replace {
                        expected: current.due.clone(),
                        value: task_a.due.clone(),
                    },
                    wait: StringEdit::Replace {
                        expected: current.wait.clone(),
                        value: task_a.wait.clone(),
                    },
                    scheduled: StringEdit::Replace {
                        expected: current.scheduled.clone(),
                        value: task_a.scheduled.clone(),
                    },
                    start: StringEdit::Replace {
                        expected: current.start.clone(),
                        value: task_a.start.clone(),
                    },
                    tags: StringSetEdit::Replace {
                        expected: current.tags.clone(),
                        value: task_a.tags.clone(),
                    },
                    dependencies: StringSetEdit::Replace {
                        expected: current.dependencies.clone(),
                        value: vec![task_b.uuid.clone()],
                    },
                }
            })
            .unwrap();

        let a = wrapper.get_task(task_a.uuid.clone()).unwrap().unwrap();
        let b = wrapper.get_task(task_b.uuid.clone()).unwrap().unwrap();
        assert_eq!(a.dependencies, vec![task_b.uuid.clone()]);
        assert!(a.is_blocked);
        assert!(b.is_blocking);

        // Remove the dependency
        wrapper
            .update_task({
                let current = wrapper.get_task(task_a.uuid.clone()).unwrap().unwrap();
                TaskEditPatch {
                    uuid: task_a.uuid.clone(),
                    expected_status: current.status.clone(),
                    description: StringEdit::Replace {
                        expected: Some(current.description.clone()),
                        value: Some(task_a.description.clone()),
                    },
                    project: StringEdit::Replace {
                        expected: current.project.clone(),
                        value: task_a.project.clone(),
                    },
                    priority: StringEdit::Replace {
                        expected: current.priority.clone(),
                        value: task_a.priority.clone(),
                    },
                    due: StringEdit::Replace {
                        expected: current.due.clone(),
                        value: task_a.due.clone(),
                    },
                    wait: StringEdit::Replace {
                        expected: current.wait.clone(),
                        value: task_a.wait.clone(),
                    },
                    scheduled: StringEdit::Replace {
                        expected: current.scheduled.clone(),
                        value: task_a.scheduled.clone(),
                    },
                    start: StringEdit::Replace {
                        expected: current.start.clone(),
                        value: task_a.start.clone(),
                    },
                    tags: StringSetEdit::Replace {
                        expected: current.tags.clone(),
                        value: task_a.tags.clone(),
                    },
                    dependencies: StringSetEdit::Replace {
                        expected: current.dependencies.clone(),
                        value: vec![],
                    },
                }
            })
            .unwrap();

        let a = wrapper.get_task(task_a.uuid).unwrap().unwrap();
        let b = wrapper.get_task(task_b.uuid).unwrap().unwrap();
        assert!(a.dependencies.is_empty());
        assert!(!a.is_blocked);
        assert!(!b.is_blocking);
    }

    #[test]
    fn test_update_dependencies_replaces_list() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let make = |description: &str| {
            wrapper
                .add_task(TaskAddProps {
                    description: description.into(),
                    project: None,
                    tags: vec![],
                    wait: None,
                    due: None,
                    scheduled: None,
                    start: None,
                    priority: None,
                    dependencies: vec![],
                })
                .unwrap()
        };
        let a = make("A");
        let b = make("B");
        let c = make("C");

        // Start with [B], replace with [C]
        for deps in (vec![vec![b.uuid.clone()], vec![c.uuid.clone()]]).into_iter() {
            wrapper
                .update_task({
                    let current = wrapper.get_task(a.uuid.clone()).unwrap().unwrap();
                    TaskEditPatch {
                        uuid: a.uuid.clone(),
                        expected_status: current.status.clone(),
                        description: StringEdit::Replace {
                            expected: Some(current.description.clone()),
                            value: Some(a.description.clone()),
                        },
                        project: StringEdit::Replace {
                            expected: current.project.clone(),
                            value: a.project.clone(),
                        },
                        priority: StringEdit::Replace {
                            expected: current.priority.clone(),
                            value: a.priority.clone(),
                        },
                        due: StringEdit::Replace {
                            expected: current.due.clone(),
                            value: a.due.clone(),
                        },
                        wait: StringEdit::Replace {
                            expected: current.wait.clone(),
                            value: a.wait.clone(),
                        },
                        scheduled: StringEdit::Replace {
                            expected: current.scheduled.clone(),
                            value: a.scheduled.clone(),
                        },
                        start: StringEdit::Replace {
                            expected: current.start.clone(),
                            value: a.start.clone(),
                        },
                        tags: StringSetEdit::Replace {
                            expected: current.tags.clone(),
                            value: a.tags.clone(),
                        },
                        dependencies: StringSetEdit::Replace {
                            expected: current.dependencies.clone(),
                            value: deps,
                        },
                    }
                })
                .unwrap();
        }

        let a = wrapper.get_task(a.uuid).unwrap().unwrap();
        let b = wrapper.get_task(b.uuid).unwrap().unwrap();
        let c = wrapper.get_task(c.uuid).unwrap().unwrap();
        assert_eq!(a.dependencies, vec![c.uuid.clone()]);
        assert!(a.is_blocked);
        assert!(c.is_blocking);
        assert!(!b.is_blocking);
    }

    #[test]
    fn test_completed_dependency_does_not_block() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let blocked = wrapper
            .add_task(TaskAddProps {
                description: "Blocked".into(),
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
        let blocker = wrapper
            .add_task(TaskAddProps {
                description: "Blocker".into(),
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
            .update_task({
                let current = wrapper.get_task(blocked.uuid.clone()).unwrap().unwrap();
                TaskEditPatch {
                    uuid: blocked.uuid.clone(),
                    expected_status: current.status.clone(),
                    description: StringEdit::Replace {
                        expected: Some(current.description.clone()),
                        value: Some(blocked.description.clone()),
                    },
                    project: StringEdit::Replace {
                        expected: current.project.clone(),
                        value: blocked.project.clone(),
                    },
                    priority: StringEdit::Replace {
                        expected: current.priority.clone(),
                        value: blocked.priority.clone(),
                    },
                    due: StringEdit::Replace {
                        expected: current.due.clone(),
                        value: blocked.due.clone(),
                    },
                    wait: StringEdit::Replace {
                        expected: current.wait.clone(),
                        value: blocked.wait.clone(),
                    },
                    scheduled: StringEdit::Replace {
                        expected: current.scheduled.clone(),
                        value: blocked.scheduled.clone(),
                    },
                    start: StringEdit::Replace {
                        expected: current.start.clone(),
                        value: blocked.start.clone(),
                    },
                    tags: StringSetEdit::Replace {
                        expected: current.tags.clone(),
                        value: blocked.tags.clone(),
                    },
                    dependencies: StringSetEdit::Replace {
                        expected: current.dependencies.clone(),
                        value: vec![blocker.uuid.clone()],
                    },
                }
            })
            .unwrap();
        // Complete the blocker
        wrapper
            .update_task_status(blocker.uuid.clone(), TaskStatus::Completed)
            .unwrap();

        let blocked = wrapper.get_task(blocked.uuid).unwrap().unwrap();
        let blocker = wrapper.get_task(blocker.uuid).unwrap().unwrap();
        // Dependency record remains, but no longer blocks
        assert_eq!(blocked.dependencies.len(), 1);
        assert!(!blocked.is_blocked);
        assert!(!blocker.is_blocking);
    }
}
