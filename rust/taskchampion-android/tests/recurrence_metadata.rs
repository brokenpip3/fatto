#[path = "support/recurrence_fixtures.rs"]
#[allow(dead_code)]
mod support;
use support::*;
use taskchampion_android::{StringEdit, StringSetEdit, TaskStatus};

#[test]
fn ordinary_occurrence_edits_preserve_all_native_recurrence_and_unknown_properties() {
    let dir = tempfile::tempdir().unwrap();
    seed(dir.path(), &series("--"));
    seed(
        dir.path(),
        &[(
            CHILD0,
            vec![
                ("until", "1900000000"),
                ("imask", "0000"),
                ("future_inheritance", "opaque"),
            ],
        )],
    );
    let before = properties(dir.path(), CHILD0);
    let r = wrapper(dir.path());
    let task = r.get_task(CHILD0.into()).unwrap().unwrap();
    let mut edit = patch(&task);
    edit.description = StringEdit::Replace {
        expected: Some(task.description),
        value: Some("Edited occurrence".into()),
    };
    edit.tags = StringSetEdit::Replace {
        expected: vec!["home".into()],
        value: vec!["office".into()],
    };
    edit.dependencies = StringSetEdit::Replace {
        expected: vec![],
        value: vec![CHILD1.into()],
    };
    edit.due = StringEdit::Replace {
        expected: Some("2030-01-01T00:00:00Z".into()),
        value: Some("2030-01-02T00:00:00Z".into()),
    };
    edit.wait = StringEdit::Replace {
        expected: None,
        value: Some("2030-01-01T12:00:00Z".into()),
    };
    edit.scheduled = StringEdit::Replace {
        expected: None,
        value: Some("2030-01-01T18:00:00Z".into()),
    };
    r.update_task(edit).unwrap();
    let annotation = r
        .add_annotation(CHILD0.into(), "Temporary note".into())
        .unwrap();
    r.remove_annotation(CHILD0.into(), annotation.entry)
        .unwrap();
    let task = r.get_task(CHILD0.into()).unwrap().unwrap();
    let mut start = patch(&task);
    start.start = StringEdit::Replace {
        expected: None,
        value: Some("2026-10-11T10:00:00Z".into()),
    };
    r.update_task(start).unwrap();
    let task = r.get_task(CHILD0.into()).unwrap().unwrap();
    let mut stop = patch(&task);
    stop.start = StringEdit::Replace {
        expected: task.start,
        value: None,
    };
    r.update_task(stop).unwrap();
    let after = properties(dir.path(), CHILD0);
    for key in [
        "status",
        "parent",
        "imask",
        "recur",
        "rtype",
        "until",
        "custom",
        "future_inheritance",
        "annotation_1700000000",
    ] {
        assert_eq!(after.get(key), before.get(key), "{key}");
    }
    assert_eq!(after["description"], "Edited occurrence");
    assert!(!after.contains_key("tag_home"));
    assert!(after.contains_key("tag_office"));
    assert!(after.contains_key(&format!("dep_{CHILD1}")));
    assert!(!after.contains_key("start"));
    assert_eq!(properties(dir.path(), PARENT)["mask"], "W-");
    assert_eq!(r.all_task_data().unwrap().len(), 3);
}

#[test]
fn raw_metadata_unknown_status_and_reads_never_change_native_records() {
    let dir = tempfile::tempdir().unwrap();
    seed(dir.path(), &series("--"));
    seed(
        dir.path(),
        &[(
            CHILD0,
            vec![
                ("status", "paused-import"),
                ("until", "invalid date"),
                ("imask", "0000"),
                ("last", "legacy value"),
            ],
        )],
    );
    let before = properties(dir.path(), CHILD0);
    let count = operation_count(dir.path());
    let r = wrapper(dir.path());
    let task = r.get_task(CHILD0.into()).unwrap().unwrap();
    assert_eq!(
        task.status,
        TaskStatus::Unknown {
            value: "paused-import".into()
        }
    );
    let meta = task.recurrence.as_ref().unwrap();
    assert_eq!(meta.imask.as_deref(), Some("0000"));
    assert_eq!(meta.until_raw.as_deref(), Some("invalid date"));
    assert_eq!(meta.until, None);
    assert_eq!(meta.last.as_deref(), Some("legacy value"));
    assert!(!task
        .udas
        .iter()
        .any(|u| ["until", "parent", "recur", "imask", "last"].contains(&u.key.as_str())));
    assert!(task
        .udas
        .iter()
        .any(|u| u.key == "custom" && u.value == "child UDA"));
    assert_eq!(r.all_task_data().unwrap().len(), 3);
    assert!(r
        .recurrence_diagnostics()
        .unwrap()
        .iter()
        .any(|d| d.task_uuid == CHILD0 && d.code == "invalid_until"));
    assert_eq!(operation_count(dir.path()), count);
    assert_eq!(properties(dir.path(), CHILD0), before);
    let mut edit = patch(&task);
    edit.description = StringEdit::Replace {
        expected: Some(task.description),
        value: Some("Content edit".into()),
    };
    r.update_task(edit).unwrap();
    let after = properties(dir.path(), CHILD0);
    for key in [
        "status",
        "recur",
        "rtype",
        "parent",
        "imask",
        "until",
        "last",
        "custom",
        "annotation_1700000000",
        "tag_home",
    ] {
        assert_eq!(after.get(key), before.get(key), "{key}");
    }
}

#[test]
fn until_extremes_are_safe_and_valid_epoch_decodes_without_normalization() {
    for raw in [
        "broken",
        "-9223372036854775808",
        "9223372036854775807",
        "999999999999999999999999",
    ] {
        let dir = tempfile::tempdir().unwrap();
        seed(dir.path(), &series("--"));
        seed(dir.path(), &[(PARENT, vec![("until", raw)])]);
        let r = wrapper(dir.path());
        let metadata = r
            .get_task(PARENT.into())
            .unwrap()
            .unwrap()
            .recurrence
            .unwrap();
        assert_eq!(metadata.until_raw.as_deref(), Some(raw));
        assert_eq!(metadata.until, None);
        assert!(r
            .recurrence_diagnostics()
            .unwrap()
            .iter()
            .any(|d| d.code == "invalid_until"));
        assert_eq!(properties(dir.path(), PARENT)["until"], raw);
    }
    let dir = tempfile::tempdir().unwrap();
    seed(
        dir.path(),
        &[(
            PARENT,
            vec![
                ("description", "Until"),
                ("status", "recurring"),
                ("recur", "daily"),
                ("due", "1893456000"),
                ("until", "1893542400"),
            ],
        )],
    );
    let meta = wrapper(dir.path())
        .get_task(PARENT.into())
        .unwrap()
        .unwrap()
        .recurrence
        .unwrap();
    assert_eq!(meta.until.as_deref(), Some("2030-01-02T00:00:00+00:00"));
    assert_eq!(properties(dir.path(), PARENT)["until"], "1893542400");
}

#[test]
fn unreadable_wait_and_unknown_occurrence_status_are_diagnostic_not_silent() {
    for (key, value) in [("status", "paused-import"), ("wait", "invalid")] {
        let dir = tempfile::tempdir().unwrap();
        seed(dir.path(), &series("--"));
        seed(dir.path(), &[(CHILD0, vec![(key, value)])]);
        let r = wrapper(dir.path());
        assert!(
            r.recurrence_diagnostics()
                .unwrap()
                .iter()
                .any(|d| d.task_uuid == CHILD0 && d.code == "unsupported_type"),
            "{key}"
        );
        assert_eq!(properties(dir.path(), PARENT)["mask"], "--");
    }
}

#[test]
fn absent_children_are_diagnosed_without_read_side_effects() {
    let dir = tempfile::tempdir().unwrap();
    let mut records = series("--?");
    records.remove(2);
    seed(dir.path(), &records);
    let r = wrapper(dir.path());
    let count = operation_count(dir.path());
    assert!(r
        .recurrence_diagnostics()
        .unwrap()
        .iter()
        .any(|d| d.task_uuid == PARENT && d.code == "missing_occurrence"));
    assert_eq!(properties(dir.path(), PARENT)["mask"], "--?");
    assert_eq!(operation_count(dir.path()), count);
}

#[test]
fn invalid_imported_intervals_and_relationships_remain_raw_and_diagnostic() {
    for raw in [
        "",
        "0days",
        "-1days",
        "999999999999999999999years",
        "unknown-period",
    ] {
        let dir = tempfile::tempdir().unwrap();
        seed(dir.path(), &series("--"));
        seed(dir.path(), &[(PARENT, vec![("recur", raw)])]);
        let r = wrapper(dir.path());
        let before = properties(dir.path(), PARENT);
        assert!(
            r.recurrence_diagnostics()
                .unwrap()
                .iter()
                .any(|d| d.task_uuid == PARENT && d.code == "invalid_interval"),
            "{raw}"
        );
        r.update_task_status(CHILD0.into(), TaskStatus::Completed)
            .unwrap();
        let after = properties(dir.path(), PARENT);
        assert_eq!(after, before);
    }
    let dir = tempfile::tempdir().unwrap();
    seed(dir.path(), &series("--"));
    seed(dir.path(), &[(CHILD0, vec![("parent", "invalid-uuid")])]);
    let r = wrapper(dir.path());
    assert!(r
        .recurrence_diagnostics()
        .unwrap()
        .iter()
        .any(|d| d.task_uuid == CHILD0));
    r.update_task_status(CHILD0.into(), TaskStatus::Completed)
        .unwrap();
    assert_eq!(properties(dir.path(), CHILD0)["parent"], "invalid-uuid");
    assert_eq!(properties(dir.path(), PARENT)["mask"], "--");
}

#[test]
fn captured_cli_series_are_read_without_generation_or_property_loss() {
    let records: serde_json::Value =
        serde_json::from_str(include_str!("fixtures/recurrence/taskwarrior-3.5.0.json")).unwrap();
    let mut owned = Vec::new();
    for record in records.as_array().unwrap() {
        let mut props = Vec::<(String, String)>::new();
        for (key, value) in record.as_object().unwrap() {
            match key.as_str() {
                "uuid" => {}
                "annotations" => {
                    for ann in value.as_array().unwrap() {
                        props.push((
                            "annotation_1577836800".into(),
                            ann["description"].as_str().unwrap().into(),
                        ));
                    }
                }
                "tags" => {
                    for tag in value.as_array().unwrap() {
                        props.push((format!("tag_{}", tag.as_str().unwrap()), "x".into()));
                    }
                }
                "depends" => {
                    for dep in value.as_array().unwrap() {
                        props.push((format!("dep_{}", dep.as_str().unwrap()), "x".into()));
                    }
                }
                _ => {
                    let raw = value
                        .as_str()
                        .map(str::to_owned)
                        .unwrap_or_else(|| value.to_string());
                    let raw = if ["due", "wait", "scheduled"].contains(&key.as_str()) {
                        chrono::NaiveDateTime::parse_from_str(&raw, "%Y%m%dT%H%M%SZ")
                            .unwrap()
                            .and_utc()
                            .timestamp()
                            .to_string()
                    } else {
                        raw
                    };
                    props.push((key.clone(), raw));
                }
            }
        }
        owned.push((record["uuid"].as_str().unwrap().to_owned(), props));
    }
    let borrowed: Vec<_> = owned
        .iter()
        .map(|(id, p)| {
            (
                id.as_str(),
                p.iter().map(|(k, v)| (k.as_str(), v.as_str())).collect(),
            )
        })
        .collect();
    let dir = tempfile::tempdir().unwrap();
    seed(dir.path(), &borrowed);
    let before = operation_count(dir.path());
    let r = wrapper(dir.path());
    let data = r.all_task_data().unwrap();
    assert_eq!(data.len(), 21);
    assert_eq!(
        data.iter()
            .filter(|t| t.status == TaskStatus::Recurring)
            .count(),
        5
    );
    assert_eq!(
        data.iter()
            .filter(|t| t.recurrence.as_ref().is_some_and(|m| m.parent.is_some()))
            .count(),
        15
    );
    assert!(r.recurrence_diagnostics().unwrap().is_empty());
    for (id, props) in &owned {
        let stored = properties(dir.path(), id);
        for (k, v) in props {
            assert_eq!(stored.get(k), Some(v), "{id}:{k}");
        }
    }
    assert_eq!(operation_count(dir.path()), before);
}

#[test]
fn extreme_wait_is_readable_and_preserved_without_synthetic_tag_panics() {
    let dir = tempfile::tempdir().unwrap();
    seed(dir.path(), &series("--"));
    seed(
        dir.path(),
        &[(CHILD0, vec![("wait", "9223372036854775807")])],
    );
    let r = wrapper(dir.path());
    let tasks = r.all_task_data().unwrap();
    let child = tasks.iter().find(|t| t.uuid == CHILD0).unwrap();
    assert_eq!(child.wait, None);
    assert!(child
        .native_dates
        .iter()
        .any(|p| p.key == "wait" && p.value == "9223372036854775807"));
    assert!(!child.tags.contains(&"WAITING".into()));
    assert!(r
        .recurrence_diagnostics()
        .unwrap()
        .iter()
        .any(|d| d.task_uuid == CHILD0 && d.code == "unsupported_type"));
    assert_eq!(
        properties(dir.path(), CHILD0)["wait"],
        "9223372036854775807"
    );
}
