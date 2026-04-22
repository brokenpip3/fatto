use std::env;
use taskchampion_android::{ReplicaWrapper, TaskStatus};

#[test]
fn test_sync_integration() {
    let sync_url =
        env::var("TASKCHAMPION_SYNC_URL").unwrap_or_else(|_| "http://localhost:8080".into());
    let client_id = uuid::Uuid::new_v4().to_string();
    let sync_secret = env::var("TASKCHAMPION_SYNC_SECRET").unwrap_or_else(|_| "ciao".into());

    // 1. Create first replica and add a task
    let rep1 = ReplicaWrapper::new_in_memory().unwrap();
    let task1 = rep1
        .add_task(
            "Task from replica 1".into(),
            None,
            vec![],
            None,
            None,
            None,
            None,
            None,
            vec![],
        )
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
        .add_task(
            "To be deleted".into(),
            None,
            vec![],
            None,
            None,
            None,
            None,
            None,
            vec![],
        )
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
        .add_task(
            "Conflict task".into(),
            None,
            vec![],
            None,
            None,
            None,
            None,
            None,
            vec![],
        )
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

#[test]
fn test_task_properties_integration() {
    let rep = ReplicaWrapper::new_in_memory().unwrap();

    // 1. Test Priority & Urgency
    let task_h = rep
        .add_task(
            "High priority".into(),
            None,
            vec![],
            None,
            None,
            None,
            None,
            Some("H".into()),
            vec![],
        )
        .unwrap();
    assert_eq!(task_h.priority, Some("H".into()));
    assert!(task_h.urgency >= 6.0); // 6.0 (priority H) + some age

    // 2. Test Dependencies & Blocking
    let task_blocking = rep
        .add_task(
            "Blocking task".into(),
            None,
            vec![],
            None,
            None,
            None,
            None,
            None,
            vec![],
        )
        .unwrap();

    let task_blocked = rep
        .add_task(
            "Blocked task".into(),
            None,
            vec![],
            None,
            None,
            None,
            None,
            None,
            vec![],
        )
        .unwrap();

    // Link them: blocked depends on blocking
    rep.update_task(
        task_blocked.uuid.clone(),
        task_blocked.description.clone(),
        task_blocked.status,
        None,
        vec![],
        None,
        None,
        None,
        None,
        None,
        vec![task_blocking.uuid.clone()],
    )
    .unwrap();

    let tasks = rep.all_task_data().unwrap();
    let updated_blocked = tasks.iter().find(|t| t.uuid == task_blocked.uuid).unwrap();
    assert!(updated_blocked.is_blocked);
    assert_eq!(updated_blocked.dependencies, vec![task_blocking.uuid]);
}
