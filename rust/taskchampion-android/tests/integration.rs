use std::env;
use taskchampion_android::{ReplicaWrapper, TaskAddProps, TaskStatus, TaskUpdateProps};

#[test]
fn test_sync_integration() {
    let sync_url =
        env::var("TASKCHAMPION_SYNC_URL").unwrap_or_else(|_| "http://localhost:8080".into());
    let client_id = uuid::Uuid::new_v4().to_string();
    let sync_secret = env::var("TASKCHAMPION_SYNC_SECRET").unwrap_or_else(|_| "ciao".into());

    // 1. Create first replica and add a task
    let rep1 = ReplicaWrapper::new_in_memory().unwrap();
    let task1 = rep1
        .add_task(TaskAddProps {
            description: "Task from replica 1".into(),
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

    // 2. Sync first replica
    rep1.sync(sync_url.clone(), client_id.clone(), sync_secret.clone())
        .unwrap();

    // 3. Create second replica and sync
    let rep2 = ReplicaWrapper::new_in_memory().unwrap();
    rep2.sync(sync_url.clone(), client_id.clone(), sync_secret.clone())
        .unwrap();

    // 4. Assert task is visible in second replica
    let tasks2 = rep2.all_task_data().unwrap();
    assert!(tasks2
        .iter()
        .any(|t| t.description == "Task from replica 1"));

    // 5. Complete task in second replica and sync
    let task2_uuid = tasks2
        .iter()
        .find(|t| t.description == "Task from replica 1")
        .unwrap()
        .uuid
        .clone();
    rep2.update_task_status(task2_uuid, TaskStatus::Completed)
        .unwrap();
    rep2.sync(sync_url.clone(), client_id.clone(), sync_secret.clone())
        .unwrap();

    // 6. Sync first replica and assert task is completed
    rep1.sync(sync_url.clone(), client_id.clone(), sync_secret.clone())
        .unwrap();
    let tasks1 = rep1.all_task_data().unwrap();
    let task1_updated = tasks1.iter().find(|t| t.uuid == task1.uuid).unwrap();
    assert_eq!(task1_updated.status, TaskStatus::Completed);
}

#[test]
fn test_sync_deletion_integration() {
    let sync_url =
        env::var("TASKCHAMPION_SYNC_URL").unwrap_or_else(|_| "http://localhost:8080".into());
    let client_id = uuid::Uuid::new_v4().to_string();
    let sync_secret = env::var("TASKCHAMPION_SYNC_SECRET").unwrap_or_else(|_| "ciao".into());

    let rep1 = ReplicaWrapper::new_in_memory().unwrap();
    let task1 = rep1
        .add_task(TaskAddProps {
            description: "To be deleted".into(),
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
    rep1.sync(sync_url.clone(), client_id.clone(), sync_secret.clone())
        .unwrap();

    let rep2 = ReplicaWrapper::new_in_memory().unwrap();
    rep2.sync(sync_url.clone(), client_id.clone(), sync_secret.clone())
        .unwrap();

    rep1.update_task_status(task1.uuid.clone(), TaskStatus::Deleted)
        .unwrap();
    rep1.sync(sync_url.clone(), client_id.clone(), sync_secret.clone())
        .unwrap();

    rep2.sync(sync_url, client_id, sync_secret).unwrap();
    let tasks2 = rep2.all_task_data().unwrap();
    assert!(!tasks2
        .iter()
        .any(|t| t.uuid == task1.uuid && t.status != TaskStatus::Deleted));
}

#[test]
fn test_sync_conflict_integration() {
    let sync_url =
        env::var("TASKCHAMPION_SYNC_URL").unwrap_or_else(|_| "http://localhost:8080".into());
    let client_id = uuid::Uuid::new_v4().to_string();
    let sync_secret = env::var("TASKCHAMPION_SYNC_SECRET").unwrap_or_else(|_| "ciao".into());

    let rep1 = ReplicaWrapper::new_in_memory().unwrap();
    let task1 = rep1
        .add_task(TaskAddProps {
            description: "Conflict task".into(),
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
    rep1.sync(sync_url.clone(), client_id.clone(), sync_secret.clone())
        .unwrap();

    let rep2 = ReplicaWrapper::new_in_memory().unwrap();
    rep2.sync(sync_url.clone(), client_id.clone(), sync_secret.clone())
        .unwrap();

    // Concurrent edits
    rep1.update_task_status(task1.uuid.clone(), TaskStatus::Completed)
        .unwrap();
    rep2.update_task_status(task1.uuid.clone(), TaskStatus::Pending)
        .unwrap();

    rep1.sync(sync_url.clone(), client_id.clone(), sync_secret.clone())
        .unwrap();
    rep2.sync(sync_url.clone(), client_id.clone(), sync_secret.clone())
        .unwrap();

    // Sync again to converge
    rep1.sync(sync_url, client_id, sync_secret).unwrap();

    let tasks1 = rep1.all_task_data().unwrap();
    let tasks2 = rep2.all_task_data().unwrap();
    assert_eq!(tasks1.len(), tasks2.len());
}

/// Connection details for the local S3-compatible endpoint (the `minio`
/// service in docker-compose.yml). Returns (endpoint, bucket, access key id,
/// secret access key, encryption secret).
fn s3_test_config() -> (String, String, String, String, String) {
    (
        env::var("TASKCHAMPION_S3_ENDPOINT").unwrap_or_else(|_| "http://localhost:9000".into()),
        env::var("TASKCHAMPION_S3_BUCKET").unwrap_or_else(|_| "fatto-tasks".into()),
        env::var("TASKCHAMPION_S3_ACCESS_KEY_ID").unwrap_or_else(|_| "minioadmin".into()),
        env::var("TASKCHAMPION_S3_SECRET_ACCESS_KEY").unwrap_or_else(|_| "minioadmin".into()),
        env::var("TASKCHAMPION_S3_ENCRYPTION_SECRET")
            .unwrap_or_else(|_| "s3-integration-secret".into()),
    )
}

fn sync_aws_with_test_config(rep: &ReplicaWrapper) {
    let (endpoint, bucket, access_key_id, secret_access_key, encryption_secret) = s3_test_config();
    rep.sync_aws(
        bucket,
        None,
        Some(endpoint),
        access_key_id,
        secret_access_key,
        encryption_secret,
    )
    .unwrap();
}

// The whole S3 roundtrip lives in a single test: all replicas share one
// bucket, so concurrently running tests would race on the same version chain.
#[test]
fn test_sync_aws_integration() {
    // The bucket persists across test runs within a compose session, so tag
    // the task with a unique marker.
    let description = format!("S3 task {}", uuid::Uuid::new_v4());

    // 1. Create first replica, add a task and push it to the bucket
    let rep1 = ReplicaWrapper::new_in_memory().unwrap();
    let task1 = rep1
        .add_task(TaskAddProps {
            description: description.clone(),
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
    sync_aws_with_test_config(&rep1);

    // 2. A fresh replica syncing against the same bucket sees the task
    let rep2 = ReplicaWrapper::new_in_memory().unwrap();
    sync_aws_with_test_config(&rep2);
    let tasks2 = rep2.all_task_data().unwrap();
    assert!(tasks2.iter().any(|t| t.description == description));

    // 3. Complete the task in the second replica and propagate it back
    rep2.update_task_status(task1.uuid.clone(), TaskStatus::Completed)
        .unwrap();
    sync_aws_with_test_config(&rep2);
    sync_aws_with_test_config(&rep1);
    let tasks1 = rep1.all_task_data().unwrap();
    let task1_updated = tasks1.iter().find(|t| t.uuid == task1.uuid).unwrap();
    assert_eq!(task1_updated.status, TaskStatus::Completed);
}

#[test]
fn test_task_properties_integration() {
    let rep = ReplicaWrapper::new_in_memory().unwrap();

    // 1. Test Priority & Urgency
    let task_h = rep
        .add_task(TaskAddProps {
            description: "High priority".into(),
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
    assert!(task_h.urgency >= 6.0); // 6.0 (priority H) + some age

    // 2. Test Dependencies & Blocking
    let task_blocking = rep
        .add_task(TaskAddProps {
            description: "Blocking task".into(),
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

    let task_blocked = rep
        .add_task(TaskAddProps {
            description: "Blocked task".into(),
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

    // Link them: blocked depends on blocking
    rep.update_task(TaskUpdateProps {
        uuid: task_blocked.uuid.clone(),
        description: task_blocked.description.clone(),
        status: task_blocked.status,
        project: None,
        tags: vec![],
        wait: None,
        due: None,
        scheduled: None,
        start: None,
        priority: None,
        dependencies: vec![task_blocking.uuid.clone()],
    })
    .unwrap();

    let tasks = rep.all_task_data().unwrap();
    let updated_blocked = tasks.iter().find(|t| t.uuid == task_blocked.uuid).unwrap();
    assert!(updated_blocked.is_blocked);
    assert_eq!(updated_blocked.dependencies, vec![task_blocking.uuid]);
}

#[test]
fn test_annotation_integration() {
    let rep = ReplicaWrapper::new_in_memory().unwrap();

    // 1. Create task with no annotations
    let task = rep
        .add_task(TaskAddProps {
            description: "Annotation test task".into(),
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
    assert!(task.annotations.is_empty());

    // 2. Add annotations
    rep.add_annotation(task.uuid.clone(), "First note".into())
        .unwrap();
    rep.add_annotation(task.uuid.clone(), "Second note".into())
        .unwrap();

    // 3. Verify via all_task_data (fresh fetch from storage)
    let tasks = rep.all_task_data().unwrap();
    let updated = tasks.iter().find(|t| t.uuid == task.uuid).unwrap();
    assert_eq!(updated.annotations.len(), 2);
    let descriptions: Vec<&str> = updated
        .annotations
        .iter()
        .map(|a| a.description.as_str())
        .collect();
    assert!(descriptions.contains(&"First note"));
    assert!(descriptions.contains(&"Second note"));

    // 4. Remove an annotation and verify
    let entry = updated.annotations[0].entry.clone();
    rep.remove_annotation(task.uuid.clone(), entry).unwrap();

    let tasks_after = rep.all_task_data().unwrap();
    let after_remove = tasks_after.iter().find(|t| t.uuid == task.uuid).unwrap();
    assert_eq!(after_remove.annotations.len(), 1);
    assert_eq!(after_remove.annotations[0].description, "Second note");
}
