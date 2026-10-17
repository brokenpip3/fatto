use super::*;

fn pending(wrapper: &ReplicaWrapper) -> TaskData {
    wrapper
        .add_task(TaskAddProps {
            description: "Review me".into(),
            project: None,
            tags: vec![],
            due: None,
            wait: None,
            scheduled: None,
            start: None,
            priority: None,
            dependencies: vec![],
        })
        .unwrap()
}

fn patch(task: &TaskData) -> TaskEditPatch {
    TaskEditPatch {
        uuid: task.uuid.clone(),
        expected_status: TaskStatus::Pending,
        description: StringEdit::Replace {
            expected: Some(task.description.clone()),
            value: Some("Updated".into()),
        },
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

#[test]
fn review_stamps_native_timestamp_and_preserves_udas() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let task = pending(&wrapper);
    {
        let mut replica = wrapper.inner.lock().unwrap();
        let mut native = wrapper
            .rt
            .block_on(replica.get_task(Uuid::parse_str(&task.uuid).unwrap()))
            .unwrap()
            .unwrap();
        let mut ops = Operations::new();
        native
            .set_value("custom", Some("keep me".into()), &mut ops)
            .unwrap();
        native
            .set_modified(DateTime::from_timestamp(1, 0).unwrap(), &mut ops)
            .unwrap();
        wrapper.rt.block_on(replica.commit_operations(ops)).unwrap();
    }
    let before = Utc::now().timestamp();
    wrapper.mark_reviewed(task.uuid.clone()).unwrap();
    let updated = wrapper.get_task(task.uuid).unwrap().unwrap();
    let reviewed: i64 = updated
        .udas
        .iter()
        .find(|u| u.key == "reviewed")
        .unwrap()
        .value
        .parse()
        .unwrap();
    assert!(reviewed >= before && reviewed <= Utc::now().timestamp());
    assert!(updated
        .udas
        .iter()
        .any(|u| u.key == "custom" && u.value == "keep me"));
    assert!(
        DateTime::parse_from_rfc3339(updated.modified.as_ref().unwrap())
            .unwrap()
            .timestamp()
            >= before
    );
}

#[test]
fn review_edit_is_atomic_on_conflict() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let task = pending(&wrapper);
    let mut conflict = patch(&task);
    conflict.description = StringEdit::Replace {
        expected: Some("stale".into()),
        value: Some("Wrong".into()),
    };
    assert!(wrapper.update_task_for_review(conflict).is_err());
    let unchanged = wrapper.get_task(task.uuid.clone()).unwrap().unwrap();
    assert_eq!(unchanged.description, "Review me");
    assert!(!unchanged.udas.iter().any(|u| u.key == "reviewed"));
    wrapper.update_task_for_review(patch(&task)).unwrap();
    let updated = wrapper.get_task(task.uuid).unwrap().unwrap();
    assert_eq!(updated.description, "Updated");
    assert!(updated.udas.iter().any(|u| u.key == "reviewed"));
}

#[test]
fn review_rejects_nonpending_and_missing_tasks() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    for status in [TaskStatus::Completed, TaskStatus::Deleted] {
        let task = pending(&wrapper);
        wrapper
            .update_task_status(task.uuid.clone(), status)
            .unwrap();
        assert!(wrapper.mark_reviewed(task.uuid.clone()).is_err());
        assert!(!wrapper
            .get_task(task.uuid)
            .unwrap()
            .unwrap()
            .udas
            .iter()
            .any(|u| u.key == "reviewed"));
    }
    assert!(wrapper.mark_reviewed(Uuid::new_v4().to_string()).is_err());
    assert!(wrapper.mark_reviewed("invalid".into()).is_err());
}

#[test]
fn review_annotation_updates_stamp_and_preserve_content() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let task = pending(&wrapper);
    let ann = wrapper
        .add_annotation_for_review(task.uuid.clone(), "Review note".into())
        .unwrap();
    let updated = wrapper.get_task(task.uuid.clone()).unwrap().unwrap();
    assert_eq!(updated.annotations[0].description, "Review note");
    assert!(updated.udas.iter().any(|u| u.key == "reviewed"));
    wrapper
        .remove_annotation_for_review(task.uuid.clone(), ann.entry)
        .unwrap();
    assert!(wrapper
        .get_task(task.uuid)
        .unwrap()
        .unwrap()
        .annotations
        .is_empty());
}

#[test]
fn review_persists_across_replica_reopen() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("review").to_str().unwrap().to_string();
    let uuid = {
        let wrapper = ReplicaWrapper::new_on_disk(path.clone()).unwrap();
        let task = pending(&wrapper);
        wrapper.mark_reviewed(task.uuid.clone()).unwrap();
        task.uuid
    };
    let wrapper = ReplicaWrapper::new_on_disk(path).unwrap();
    assert!(wrapper
        .get_task(uuid)
        .unwrap()
        .unwrap()
        .udas
        .iter()
        .any(|u| u.key == "reviewed"));
}

#[test]
fn review_rejects_templates_and_unknown_statuses() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    for status in ["recurring", "unsupported"] {
        let task = pending(&wrapper);
        {
            let mut replica = wrapper.inner.lock().unwrap();
            let mut native = wrapper
                .rt
                .block_on(replica.get_task(Uuid::parse_str(&task.uuid).unwrap()))
                .unwrap()
                .unwrap();
            let mut ops = Operations::new();
            native
                .set_value("status", Some(status.into()), &mut ops)
                .unwrap();
            wrapper.rt.block_on(replica.commit_operations(ops)).unwrap();
        }
        assert!(wrapper.mark_reviewed(task.uuid).is_err());
    }
}

#[test]
fn review_sync_roundtrip_preserves_timestamp() {
    let server_dir = tempfile::tempdir().unwrap();
    let first = ReplicaWrapper::new_in_memory().unwrap();
    let second = ReplicaWrapper::new_in_memory().unwrap();
    let task = pending(&first);
    first.mark_reviewed(task.uuid.clone()).unwrap();
    for wrapper in [&first, &second] {
        let mut replica = wrapper.inner.lock().unwrap();
        wrapper.rt.block_on(async {
            let mut server = ServerConfig::Local {
                server_dir: server_dir.path().into(),
            }
            .into_server()
            .await
            .unwrap();
            replica.sync(&mut server, false).await.unwrap();
        });
    }
    let native = first.get_task(task.uuid.clone()).unwrap().unwrap();
    let synced = second.get_task(task.uuid).unwrap().unwrap();
    assert_eq!(
        native
            .udas
            .iter()
            .find(|u| u.key == "reviewed")
            .unwrap()
            .value,
        synced
            .udas
            .iter()
            .find(|u| u.key == "reviewed")
            .unwrap()
            .value
    );
}

#[test]
fn review_preserves_malformed_timestamp_arriving_after_selection() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let task = pending(&wrapper);
    {
        let mut replica = wrapper.inner.lock().unwrap();
        let mut native = wrapper
            .rt
            .block_on(replica.get_task(Uuid::parse_str(&task.uuid).unwrap()))
            .unwrap()
            .unwrap();
        let mut ops = Operations::new();
        native
            .set_value("reviewed", Some("invalid-date".into()), &mut ops)
            .unwrap();
        wrapper.rt.block_on(replica.commit_operations(ops)).unwrap();
    }
    assert!(wrapper.mark_reviewed(task.uuid.clone()).is_err());
    let current = wrapper.get_task(task.uuid).unwrap().unwrap();
    assert_eq!(
        current
            .udas
            .iter()
            .find(|u| u.key == "reviewed")
            .unwrap()
            .value,
        "invalid-date"
    );
}

#[test]
fn review_dependency_edit_stamps_reviewed_task_not_completed_dependent() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let reviewed = pending(&wrapper);
    let dependent = pending(&wrapper);
    wrapper
        .update_task_status(dependent.uuid.clone(), TaskStatus::Completed)
        .unwrap();
    let mut edit = patch(&dependent);
    edit.expected_status = TaskStatus::Completed;
    edit.description = StringEdit::Unchanged;
    edit.dependencies = StringSetEdit::Replace {
        expected: vec![],
        value: vec![reviewed.uuid.clone()],
    };
    wrapper
        .update_dependencies_for_review(reviewed.uuid.clone(), edit)
        .unwrap();
    let current = wrapper.get_task(reviewed.uuid).unwrap().unwrap();
    let other = wrapper.get_task(dependent.uuid).unwrap().unwrap();
    assert!(current.udas.iter().any(|u| u.key == "reviewed"));
    assert!(!other.udas.iter().any(|u| u.key == "reviewed"));
    assert_eq!(other.status, TaskStatus::Completed);
    assert_eq!(other.dependencies, vec![current.uuid]);
}
