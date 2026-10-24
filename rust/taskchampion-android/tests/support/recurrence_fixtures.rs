use std::collections::BTreeMap;
use std::path::Path;
use taskchampion::storage::sqlite::SqliteStorage;
use taskchampion::storage::AccessMode;
use taskchampion::{Operations, Replica, Uuid};
use taskchampion_android::{ReplicaWrapper, StringEdit, StringSetEdit, TaskData, TaskEditPatch};
use tokio::runtime::Runtime;

pub const PARENT: &str = "10000000-0000-4000-8000-000000000001";
pub const CHILD0: &str = "10000000-0000-4000-8000-000000000002";
pub const CHILD1: &str = "10000000-0000-4000-8000-000000000003";

pub fn seed(path: &Path, records: &[(&str, Vec<(&str, &str)>)]) {
    let rt = Runtime::new().unwrap();
    rt.block_on(async {
        let storage = SqliteStorage::new(path, AccessMode::ReadWrite, true)
            .await
            .unwrap();
        let mut replica = Replica::new(storage);
        let mut ops = Operations::new();
        for (uuid, props) in records {
            let id = Uuid::parse_str(uuid).unwrap();
            let mut task = match replica.get_task(id).await.unwrap() {
                Some(task) => task,
                None => replica.create_task(id, &mut ops).await.unwrap(),
            };
            for (key, value) in props {
                task.set_value(*key, Some((*value).into()), &mut ops)
                    .unwrap();
            }
        }
        replica.commit_operations(ops).await.unwrap();
    });
}

pub fn series(mask: &str) -> Vec<(&'static str, Vec<(&'static str, &'_ str)>)> {
    vec![
        (
            PARENT,
            vec![
                ("description", "Daily fixture"),
                ("status", "recurring"),
                ("recur", "daily"),
                ("rtype", "periodic"),
                ("due", "1893456000"),
                ("mask", mask),
                ("custom", "parent UDA"),
            ],
        ),
        (
            CHILD0,
            vec![
                ("description", "Daily fixture"),
                ("status", "pending"),
                ("parent", PARENT),
                ("imask", "0"),
                ("recur", "daily"),
                ("rtype", "periodic"),
                ("due", "1893456000"),
                ("custom", "child UDA"),
                ("annotation_1700000000", "keep this note"),
                ("tag_home", ""),
            ],
        ),
        (
            CHILD1,
            vec![
                ("description", "Daily fixture"),
                ("status", "pending"),
                ("parent", PARENT),
                ("imask", "1"),
                ("recur", "daily"),
                ("rtype", "periodic"),
                ("due", "1893542400"),
            ],
        ),
    ]
}

pub fn properties(path: &Path, uuid: &str) -> BTreeMap<String, String> {
    let rt = Runtime::new().unwrap();
    rt.block_on(async {
        let storage = SqliteStorage::new(path, AccessMode::ReadOnly, false)
            .await
            .unwrap();
        let mut replica = Replica::new(storage);
        replica
            .get_task(Uuid::parse_str(uuid).unwrap())
            .await
            .unwrap()
            .unwrap()
            .into_task_data()
            .iter()
            .map(|(k, v)| (k.clone(), v.clone()))
            .collect()
    })
}

pub fn operation_count(path: &Path) -> usize {
    let rt = Runtime::new().unwrap();
    rt.block_on(async {
        let storage = SqliteStorage::new(path, AccessMode::ReadOnly, false)
            .await
            .unwrap();
        Replica::new(storage).num_local_operations().await.unwrap()
    })
}

pub fn patch(task: &TaskData) -> TaskEditPatch {
    TaskEditPatch {
        date_modes: Default::default(),
        uuid: task.uuid.clone(),
        expected_status: task.status.clone(),
        description: StringEdit::Unchanged,
        project: StringEdit::Unchanged,
        priority: StringEdit::Unchanged,
        due: StringEdit::Unchanged,
        wait: StringEdit::Unchanged,
        scheduled: StringEdit::Unchanged,
        start: StringEdit::Unchanged,
        tags: StringSetEdit::Unchanged,
        dependencies: StringSetEdit::Unchanged,
    }
}

pub fn wrapper(path: &Path) -> std::sync::Arc<ReplicaWrapper> {
    ReplicaWrapper::new_on_disk(path.to_string_lossy().into_owned()).unwrap()
}
