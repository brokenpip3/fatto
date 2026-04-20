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
        Ok(tasks.into_values().map(map_task).collect())
    }

    pub fn get_task(&self, uuid: String) -> Result<Option<TaskData>> {
        let mut replica = self.inner.lock().unwrap();
        let uuid =
            Uuid::parse_str(&uuid).map_err(|_| TaskError::Internal("Invalid UUID".into()))?;
        let task = self.rt.block_on(replica.get_task(uuid))?;
        Ok(task.map(map_task))
    }

    #[allow(clippy::too_many_arguments)]
    pub fn add_task(
        &self,
        description: String,
        project: Option<String>,
        tags: Vec<String>,
        wait: Option<String>,
        due: Option<String>,
        scheduled: Option<String>,
        start: Option<String>,
    ) -> Result<TaskData> {
        let mut replica = self.inner.lock().unwrap();
        let mut ops = Operations::new();
        let uuid = Uuid::new_v4();
        let mut task = self.rt.block_on(replica.create_task(uuid, &mut ops))?;
        task.set_description(description, &mut ops)?;
        task.set_status(taskchampion::Status::Pending, &mut ops)?;
        task.set_entry(Some(chrono::Utc::now()), &mut ops)?;

        task.set_value(String::from("project"), project, &mut ops)?;

        for tag_str in tags {
            let tag = Tag::from_str(&tag_str).map_err(|e| TaskError::Internal(e.to_string()))?;
            if tag.is_user() {
                task.add_tag(&tag, &mut ops)?;
            }
        }

        task.set_wait(parse_rfc3339(wait)?, &mut ops)?;
        task.set_due(parse_rfc3339(due)?, &mut ops)?;
        task.set_timestamp("scheduled", parse_rfc3339(scheduled)?, &mut ops)?;
        task.set_timestamp("start", parse_rfc3339(start)?, &mut ops)?;

        self.rt.block_on(replica.commit_operations(ops))?;
        Ok(map_task(task))
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

    #[allow(clippy::too_many_arguments)]
    pub fn update_task(
        &self,
        uuid: String,
        description: String,
        status: TaskStatus,
        project: Option<String>,
        tags: Vec<String>,
        due: Option<String>,
        wait: Option<String>,
        scheduled: Option<String>,
        start: Option<String>,
    ) -> Result<()> {
        let mut replica = self.inner.lock().unwrap();
        let uuid =
            Uuid::parse_str(&uuid).map_err(|_| TaskError::Internal("Invalid UUID".into()))?;
        let mut ops = Operations::new();

        if let Some(mut task) = self.rt.block_on(replica.get_task(uuid))? {
            task.set_description(description, &mut ops)?;
            task.set_status(status.into(), &mut ops)?;

            // Handle project
            task.set_value(String::from("project"), project, &mut ops)?;

            // Handle tags
            let current_tags: Vec<Tag> = task.get_tags().collect();
            for tag in current_tags {
                if tag.is_user() {
                    task.remove_tag(&tag, &mut ops)?;
                }
            }
            for tag_str in tags {
                let tag =
                    Tag::from_str(&tag_str).map_err(|e| TaskError::Internal(e.to_string()))?;
                if tag.is_user() {
                    task.add_tag(&tag, &mut ops)?;
                }
            }

            task.set_due(parse_rfc3339(due)?, &mut ops)?;
            task.set_wait(parse_rfc3339(wait)?, &mut ops)?;
            task.set_timestamp("scheduled", parse_rfc3339(scheduled)?, &mut ops)?;
            task.set_timestamp("start", parse_rfc3339(start)?, &mut ops)?;

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

fn map_task(task: Task) -> TaskData {
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
            .add_task("Test task".into(), None, vec![], None, None, None, None)
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
                .add_task("Disk task".into(), None, vec![], None, None, None, None)
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
            .add_task("To complete".into(), None, vec![], None, None, None, None)
            .unwrap();
        wrapper
            .update_task_status(task.uuid.clone(), TaskStatus::Completed)
            .unwrap();

        let tasks = wrapper.all_task_data().unwrap();
        let updated_task = tasks.iter().find(|t| t.uuid == task.uuid).unwrap();
        assert_eq!(updated_task.status, TaskStatus::Completed);
    }

    #[test]
    fn test_update_task() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper
            .add_task(
                "Original description".into(),
                None,
                vec![],
                None,
                None,
                None,
                None,
            )
            .unwrap();

        wrapper
            .update_task(
                task.uuid.clone(),
                "Updated description".into(),
                TaskStatus::Pending,
                Some("NewProject".into()),
                vec!["tag1".into(), "tag2".into()],
                Some("2026-12-25T12:00:00Z".into()),
                Some("2026-12-01T12:00:00Z".into()),
                Some("2026-12-15T12:00:00Z".into()),
                None,
            )
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
            .add_task(
                "Active task".into(),
                None,
                vec![],
                None,
                None,
                None,
                Some(start_time.into()),
            )
            .unwrap();
        assert_eq!(task.start, Some(expected_time.into()));

        // Update to clear start
        wrapper
            .update_task(
                task.uuid.clone(),
                task.description,
                task.status,
                task.project,
                task.tags,
                task.due,
                task.wait,
                task.scheduled,
                None,
            )
            .unwrap();
        let updated_task = wrapper.get_task(task.uuid).unwrap().unwrap();
        assert_eq!(updated_task.start, None);
    }

    #[test]
    fn test_invalid_date_format() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let result = wrapper.add_task(
            "Task with bad date".into(),
            None,
            vec![],
            None,
            Some("invalid-date".into()),
            None,
            None,
        );
        assert!(result.is_err());
    }

    #[test]
    fn test_special_characters() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let description = "Emoji description: 🚀🔥";
        let project = "Project/Sub: 🏢";
        let task = wrapper
            .add_task(
                description.into(),
                Some(project.into()),
                vec!["tag_✨".into()],
                None,
                None,
                None,
                None,
            )
            .unwrap();
        assert_eq!(task.description, description);
        assert_eq!(task.project, Some(project.into()));
        assert!(task.tags.contains(&"tag_✨".into()));
    }

    #[test]
    fn test_empty_description() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper
            .add_task("".into(), None, vec![], None, None, None, None)
            .unwrap();
        assert_eq!(task.description, "");
    }

    #[test]
    fn test_long_description() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let long_desc = "a".repeat(1000);
        let task = wrapper
            .add_task(long_desc.clone(), None, vec![], None, None, None, None)
            .unwrap();
        assert_eq!(task.description, long_desc);
    }

    #[test]
    fn test_invalid_tag_format() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        // Tags cannot have spaces
        let result = wrapper.add_task(
            "Task with bad tag".into(),
            None,
            vec!["tag with space".into()],
            None,
            None,
            None,
            None,
        );
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
            .add_task(
                "Task with project".into(),
                Some("InitialProject".into()),
                vec![],
                None,
                None,
                None,
                None,
            )
            .unwrap();
        assert_eq!(task.project, Some("InitialProject".into()));

        // Update to remove project
        wrapper
            .update_task(
                task.uuid.clone(),
                task.description,
                task.status,
                None, // Remove project
                task.tags,
                task.due,
                task.wait,
                task.scheduled,
                task.start,
            )
            .unwrap();

        let updated_task = wrapper.get_task(task.uuid).unwrap().unwrap();
        assert_eq!(updated_task.project, None);
    }

    #[test]
    fn test_date_parsing_edge_cases() {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();

        // Valid UTC date
        let task = wrapper
            .add_task(
                "Date test".into(),
                None,
                vec![],
                None,
                Some("2026-04-20T10:00:00Z".into()),
                None,
                None,
            )
            .unwrap();
        assert_eq!(task.due, Some("2026-04-20T10:00:00+00:00".into()));

        // Valid offset date (should be converted to UTC)
        let task2 = wrapper
            .add_task(
                "Offset test".into(),
                None,
                vec![],
                None,
                Some("2026-04-20T10:00:00+02:00".into()),
                None,
                None,
            )
            .unwrap();
        assert_eq!(task2.due, Some("2026-04-20T08:00:00+00:00".into()));

        // Invalid date should fail
        let result = wrapper.add_task(
            "Bad date".into(),
            None,
            vec![],
            None,
            Some("2026-13-45T00:00:00Z".into()),
            None,
            None,
        );
        assert!(result.is_err());
    }
}
