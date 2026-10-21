#[path = "support/recurrence_fixtures.rs"]
#[allow(dead_code)]
mod support;
use support::*;
use taskchampion_android::{RecurrenceRule, StringEdit, TaskError, TaskStatus};

fn rule() -> RecurrenceRule {
    RecurrenceRule {
        interval: "daily".into(),
        until: None,
    }
}
fn ordinary() -> Vec<(&'static str, &'static str)> {
    vec![
        ("status", "pending"),
        ("description", "Original"),
        ("entry", "1700000000"),
        ("due", "1893456000"),
        ("start", "1700000000"),
        ("project", "home"),
        ("tag_home", ""),
        ("annotation_1700000000", "note"),
        ("custom", "UDA"),
        ("dep_10000000-0000-4000-8000-000000000003", ""),
    ]
}
#[test]
fn conversion_preserves_identity_and_content_and_applies_draft_before_converting() {
    let dir = tempfile::tempdir().unwrap();
    seed(dir.path(), &[(CHILD0, ordinary())]);
    let r = wrapper(dir.path());
    let before = properties(dir.path(), CHILD0);
    let mut edit = patch(&r.get_task(CHILD0.into()).unwrap().unwrap());
    edit.description = StringEdit::Replace {
        expected: Some("Original".into()),
        value: Some("Draft".into()),
    };
    r.convert_task_to_recurring(edit, rule()).unwrap();
    let mut expected = before;
    expected.insert("description".into(), "Draft".into());
    expected.insert("status".into(), "recurring".into());
    expected.insert("rtype".into(), "periodic".into());
    expected.insert("recur".into(), "daily".into());
    expected.remove("start");
    let mut after = properties(dir.path(), CHILD0);
    expected.remove("modified");
    after.remove("modified");
    assert_eq!(after, expected);
    assert_eq!(r.all_task_data().unwrap().len(), 1);
    assert_eq!(
        r.get_task(CHILD0.into()).unwrap().unwrap().status,
        TaskStatus::Recurring
    );
}
#[test]
fn conversion_validation_failures_leave_no_partial_operations() {
    for failure in ["interval", "due", "until", "conflict"] {
        let dir = tempfile::tempdir().unwrap();
        let mut props = ordinary();
        if failure == "due" {
            props.retain(|(k, _)| *k != "due");
        }
        seed(dir.path(), &[(CHILD0, props)]);
        let r = wrapper(dir.path());
        let before = properties(dir.path(), CHILD0);
        let count = operation_count(dir.path());
        let mut edit = patch(&r.get_task(CHILD0.into()).unwrap().unwrap());
        edit.description = StringEdit::Replace {
            expected: Some(
                if failure == "conflict" {
                    "Stale"
                } else {
                    "Original"
                }
                .into(),
            ),
            value: Some("Draft".into()),
        };
        let mut rule = rule();
        if failure == "interval" {
            rule.interval = "0days".into();
        }
        if failure == "until" {
            rule.until = Some("2020-01-01T00:00:00Z".into());
        }
        let result = r.convert_task_to_recurring(edit, rule);
        if failure == "conflict" {
            assert!(matches!(result, Err(TaskError::EditConflict { .. })));
        } else {
            assert!(result.is_err(), "{failure}");
        }
        assert_eq!(properties(dir.path(), CHILD0), before);
        assert_eq!(operation_count(dir.path()), count);
    }
}
#[test]
fn conversion_rejects_any_recurrence_metadata_and_nonpending_tasks() {
    for key in [
        "parent",
        "imask",
        "recur",
        "rtype",
        "mask",
        "until",
        "last",
        "template",
        "completed",
        "deleted",
        "recurring",
    ] {
        let dir = tempfile::tempdir().unwrap();
        let mut props = ordinary();
        if ["completed", "deleted", "recurring"].contains(&key) {
            props.retain(|(k, _)| *k != "status");
            props.push(("status", key));
        } else {
            props.push((key, ""));
        }
        seed(dir.path(), &[(CHILD0, props)]);
        let r = wrapper(dir.path());
        let before = properties(dir.path(), CHILD0);
        let count = operation_count(dir.path());
        let edit = patch(&r.get_task(CHILD0.into()).unwrap().unwrap());
        assert!(r.convert_task_to_recurring(edit, rule()).is_err(), "{key}");
        assert_eq!(properties(dir.path(), CHILD0), before);
        assert_eq!(operation_count(dir.path()), count);
    }
}
