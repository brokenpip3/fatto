#[path = "support/recurrence_fixtures.rs"]
#[allow(dead_code)]
mod support;
use support::*;
use taskchampion_android::{StringEdit, StringSetEdit, TaskError, TaskStatus};

#[test]
fn completion_delete_restore_and_wait_repair_only_existing_slots() {
    let dir = tempfile::tempdir().unwrap();
    seed(dir.path(), &series("--"));
    let r = wrapper(dir.path());
    let ids: Vec<_> = r
        .all_task_data()
        .unwrap()
        .iter()
        .map(|t| t.uuid.clone())
        .collect();
    r.transition_occurrence(CHILD0.into(), TaskStatus::Completed, true)
        .unwrap();
    assert_eq!(properties(dir.path(), PARENT)["mask"], "+-");
    let count = operation_count(dir.path());
    let end = properties(dir.path(), CHILD0)["end"].clone();
    r.transition_occurrence(CHILD0.into(), TaskStatus::Completed, true)
        .unwrap();
    assert_eq!(operation_count(dir.path()), count);
    assert_eq!(properties(dir.path(), CHILD0)["end"], end);
    r.update_task_status(CHILD1.into(), TaskStatus::Deleted)
        .unwrap();
    assert_eq!(properties(dir.path(), PARENT)["mask"], "+X");
    r.update_task_status(CHILD0.into(), TaskStatus::Pending)
        .unwrap();
    r.update_task_status(CHILD1.into(), TaskStatus::Pending)
        .unwrap();
    assert_eq!(properties(dir.path(), PARENT)["mask"], "--");
    assert!(!properties(dir.path(), CHILD0).contains_key("end"));
    let task = r.get_task(CHILD1.into()).unwrap().unwrap();
    let mut edit = patch(&task);
    edit.wait = StringEdit::Replace {
        expected: None,
        value: Some("2099-01-01T00:00:00Z".into()),
    };
    r.update_task(edit).unwrap();
    assert_eq!(properties(dir.path(), PARENT)["mask"], "-W");
    assert_eq!(r.all_task_data().unwrap().len(), ids.len());
    assert_eq!(properties(dir.path(), PARENT)["status"], "recurring");
    assert_eq!(
        properties(dir.path(), CHILD0)["annotation_1700000000"],
        "keep this note"
    );
    assert_eq!(properties(dir.path(), CHILD0)["parent"], PARENT);
}

#[test]
fn stale_status_or_edited_field_rejects_the_entire_patch_without_operations() {
    let dir = tempfile::tempdir().unwrap();
    seed(dir.path(), &series("--"));
    let r = wrapper(dir.path());
    let original = r.get_task(CHILD0.into()).unwrap().unwrap();
    let mut edit = patch(&original);
    edit.description = StringEdit::Replace {
        expected: Some(original.description),
        value: Some("draft".into()),
    };
    r.update_task_status(CHILD0.into(), TaskStatus::Completed)
        .unwrap();
    let before = properties(dir.path(), CHILD0);
    let count = operation_count(dir.path());
    assert!(
        matches!(r.update_task(edit), Err(TaskError::EditConflict { fields, .. }) if fields.contains(&"status".into()))
    );
    assert_eq!(properties(dir.path(), CHILD0), before);
    assert_eq!(operation_count(dir.path()), count);
    let current = r.get_task(CHILD1.into()).unwrap().unwrap();
    let mut edit = patch(&current);
    edit.description = StringEdit::Replace {
        expected: Some("stale".into()),
        value: Some("draft".into()),
    };
    edit.tags = StringSetEdit::Replace {
        expected: vec![],
        value: vec!["new".into()],
    };
    assert!(matches!(
        r.update_task(edit),
        Err(TaskError::EditConflict { .. })
    ));
    assert!(!properties(dir.path(), CHILD1).contains_key("tag_new"));
    assert_eq!(operation_count(dir.path()), count);
}

#[test]
fn description_only_patch_preserves_synced_dates_sets_and_unknown_fields() {
    let dir = tempfile::tempdir().unwrap();
    seed(dir.path(), &series("--"));
    let r = wrapper(dir.path());
    let original = r.get_task(CHILD0.into()).unwrap().unwrap();
    seed(
        dir.path(),
        &[(
            CHILD0,
            vec![
                ("due", "1893542400"),
                ("wait", "4070908800"),
                ("tag_synced", ""),
                ("dep_10000000-0000-4000-8000-000000000003", ""),
                ("new_uda", "received"),
            ],
        )],
    );
    let before = properties(dir.path(), CHILD0);
    let mut edit = patch(&original);
    edit.description = StringEdit::Replace {
        expected: Some(original.description),
        value: Some("edited".into()),
    };
    r.update_task(edit).unwrap();
    let after = properties(dir.path(), CHILD0);
    for (key, value) in before
        .iter()
        .filter(|(k, _)| !["description", "modified"].contains(&k.as_str()))
    {
        assert_eq!(after.get(key), Some(value), "{key}");
    }
    assert_eq!(properties(dir.path(), PARENT)["mask"], "W-");
}

#[test]
fn template_schedule_and_lifecycle_are_guarded_including_retired_and_unknown() {
    for status in ["recurring", "deleted", "completed", "paused-import"] {
        let dir = tempfile::tempdir().unwrap();
        seed(dir.path(), &series("--"));
        seed(dir.path(), &[(PARENT, vec![("status", status)])]);
        let r = wrapper(dir.path());
        let task = r.get_task(PARENT.into()).unwrap().unwrap();
        let before = properties(dir.path(), PARENT);
        for desired in [
            TaskStatus::Pending,
            TaskStatus::Completed,
            TaskStatus::Deleted,
        ] {
            assert!(r.update_task_status(PARENT.into(), desired).is_err());
        }
        for field in ["due", "wait", "scheduled", "start"] {
            let mut edit = patch(&task);
            let value = StringEdit::Replace {
                expected: if field == "due" {
                    task.due.clone()
                } else {
                    None
                },
                value: Some("2030-01-04T00:00:00Z".into()),
            };
            match field {
                "due" => edit.due = value,
                "wait" => edit.wait = value,
                "scheduled" => edit.scheduled = value,
                _ => edit.start = value,
            }
            assert!(r.update_task(edit).is_err());
        }
        assert_eq!(properties(dir.path(), PARENT), before);
    }
}

#[test]
fn ambiguous_or_out_of_range_or_missing_parent_never_changes_a_mask_prefix() {
    for mode in [
        "duplicate",
        "out_of_range",
        "invalid_index",
        "invalid_mask",
        "missing_parent",
        "retired_parent",
    ] {
        let dir = tempfile::tempdir().unwrap();
        let mut records = series("--");
        match mode {
            "duplicate" => records.push((
                "10000000-0000-4000-8000-000000000004",
                vec![("status", "pending"), ("parent", PARENT), ("imask", "0")],
            )),
            "out_of_range" => records[1].1.push(("imask", "9")),
            "invalid_index" => records[1].1.push(("imask", "-1")),
            "invalid_mask" => records[0].1.push(("mask", "é-")),
            "missing_parent" => records[1]
                .1
                .push(("parent", "20000000-0000-4000-8000-000000000001")),
            _ => records[0].1.push(("status", "deleted")),
        }
        seed(dir.path(), &records);
        let r = wrapper(dir.path());
        let mask = properties(dir.path(), PARENT)["mask"].clone();
        r.update_task_status(CHILD0.into(), TaskStatus::Completed)
            .unwrap();
        assert_eq!(properties(dir.path(), PARENT)["mask"], mask, "{mode}");
        assert_eq!(
            r.get_task(CHILD0.into()).unwrap().unwrap().status,
            TaskStatus::Completed
        );
        let code = match mode {
            "duplicate" => "duplicate_index",
            "out_of_range" => "mask_index_out_of_range",
            "invalid_index" => "invalid_index",
            "invalid_mask" => "invalid_mask",
            "missing_parent" => "missing_parent",
            _ => "unsupported_type",
        };
        assert!(
            r.recurrence_diagnostics()
                .unwrap()
                .iter()
                .any(|d| d.code == code),
            "{mode}"
        );
    }
}

#[test]
fn no_op_patch_does_not_reconcile_or_rewrite_a_stale_mask() {
    let dir = tempfile::tempdir().unwrap();
    seed(dir.path(), &series("??"));
    let r = wrapper(dir.path());
    let task = r.get_task(CHILD0.into()).unwrap().unwrap();
    let count = operation_count(dir.path());
    r.update_task(patch(&task)).unwrap();
    assert_eq!(operation_count(dir.path()), count);
    assert_eq!(properties(dir.path(), PARENT)["mask"], "??");
}

#[test]
fn equal_timestamp_checks_preserve_noncanonical_raw_values_and_detect_stale_sources() {
    let dir = tempfile::tempdir().unwrap();
    seed(dir.path(), &series("--"));
    seed(
        dir.path(),
        &[(
            CHILD0,
            vec![("due", "01893456000"), ("scheduled", "1893542400")],
        )],
    );
    let r = wrapper(dir.path());
    let task = r.get_task(CHILD0.into()).unwrap().unwrap();
    let count = operation_count(dir.path());
    let mut edit = patch(&task);
    edit.due = StringEdit::Replace {
        expected: task.due.clone(),
        value: task.due.clone(),
    };
    r.update_task(edit).unwrap();
    assert_eq!(operation_count(dir.path()), count);
    assert_eq!(properties(dir.path(), CHILD0)["due"], "01893456000");
    let mut derived = patch(&task);
    derived.due = StringEdit::Replace {
        expected: task.due.clone(),
        value: Some("2030-01-20T00:00:00Z".into()),
    };
    derived.scheduled = StringEdit::Replace {
        expected: task.scheduled.clone(),
        value: task.scheduled.clone(),
    };
    derived.wait = StringEdit::Replace {
        expected: None,
        value: Some("2030-01-03T00:00:00Z".into()),
    };
    seed(dir.path(), &[(CHILD0, vec![("scheduled", "1893628800")])]);
    let before = properties(dir.path(), CHILD0);
    assert!(
        matches!(r.update_task(derived),Err(TaskError::EditConflict{fields,..}) if fields.contains(&"scheduled".into()))
    );
    assert_eq!(properties(dir.path(), CHILD0), before);
}

#[test]
fn mask_limits_and_missing_positions_are_preserved() {
    for mask in ["".to_string(), "-?X".into(), "-".repeat(1_000_001)] {
        let dir = tempfile::tempdir().unwrap();
        seed(dir.path(), &series(&mask));
        let r = wrapper(dir.path());
        r.update_task_status(CHILD0.into(), TaskStatus::Completed)
            .unwrap();
        let actual = properties(dir.path(), PARENT)["mask"].clone();
        if mask == "-?X" {
            assert_eq!(actual, "+-X");
            assert!(r
                .recurrence_diagnostics()
                .unwrap()
                .iter()
                .any(|d| d.code == "missing_occurrence"));
        } else {
            assert_eq!(actual, mask);
            assert!(!r.recurrence_diagnostics().unwrap().is_empty());
        }
    }
}
