use chrono::{DateTime, Utc};
use std::str::FromStr;
use std::sync::{Arc, Mutex};
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

impl From<anyhow::Error> for TaskError {
    fn from(err: anyhow::Error) -> Self {
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
}
