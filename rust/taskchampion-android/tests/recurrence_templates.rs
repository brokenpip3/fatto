#[path = "support/recurrence_fixtures.rs"]
#[allow(dead_code)]
mod support;
use support::*;
use taskchampion_android::{
    RecurrenceRule, RecurrenceRulePatch, StringEdit, TaskAddProps, TaskStatus,
};

fn props() -> TaskAddProps {
    TaskAddProps {
        date_modes: Default::default(),
        description: "New series".into(),
        project: Some("test".into()),
        tags: vec!["home".into()],
        due: Some("2030-01-31T00:00:00Z".into()),
        wait: Some("2030-01-29T00:00:00Z".into()),
        scheduled: Some("2030-01-30T00:00:00Z".into()),
        start: None,
        priority: Some("H".into()),
        dependencies: vec![],
    }
}
fn rule(interval: &str) -> RecurrenceRule {
    RecurrenceRule {
        interval: interval.into(),
        until: None,
    }
}
fn unchanged() -> RecurrenceRulePatch {
    RecurrenceRulePatch {
        interval: StringEdit::Unchanged,
        until: StringEdit::Unchanged,
    }
}
#[test]
fn creation_is_template_only_and_validates_rules_dates_before_commit() {
    for interval in [
        "daily", "weekdays", "weekly", "monthly", "yearly", "2days", "2weeks", "2months", "2years",
    ] {
        let dir = tempfile::tempdir().unwrap();
        let r = wrapper(dir.path());
        let created = r.add_recurring_task(props(), rule(interval)).unwrap();
        assert_eq!(created.status, TaskStatus::Recurring);
        assert_eq!(r.all_task_data().unwrap().len(), 1);
        let raw = properties(dir.path(), &created.uuid);
        assert_eq!(raw["rtype"], "periodic");
        assert_eq!(raw["recur"], interval);
        assert_eq!(raw["project"], "test");
        assert_eq!(raw["priority"], "H");
        assert!(raw.contains_key("tag_home"));
        assert!(!raw.contains_key("mask"));
        assert!(!raw.contains_key("parent"));
    }
    let dir = tempfile::tempdir().unwrap();
    let r = wrapper(dir.path());
    for interval in [
        "",
        "0days",
        "-1days",
        "999999999999999years",
        "fortnightly",
        "2",
    ] {
        assert!(r.add_recurring_task(props(), rule(interval)).is_err());
    }
    let mut no_due = props();
    no_due.due = None;
    assert!(r.add_recurring_task(no_due, rule("daily")).is_err());
    let mut bad = props();
    bad.wait = Some("bad".into());
    assert!(r.add_recurring_task(bad, rule("daily")).is_err());
    assert!(r
        .add_recurring_task(
            props(),
            RecurrenceRule {
                interval: "daily".into(),
                until: Some("2030-01-01T00:00:00Z".into())
            }
        )
        .is_err());
    assert_eq!(r.all_task_data().unwrap().len(), 0);
    assert_eq!(operation_count(dir.path()), 0);
}
#[test]
fn ungenerated_schedule_edits_are_checked_and_until_is_native_or_cleared() {
    let dir = tempfile::tempdir().unwrap();
    let r = wrapper(dir.path());
    let t = r.add_recurring_task(props(), rule("daily")).unwrap();
    let mut p = patch(&t);
    p.due = StringEdit::Replace {
        expected: t.due.clone(),
        value: Some("2030-02-01T00:00:00Z".into()),
    };
    r.update_recurrence_template(
        p,
        RecurrenceRulePatch {
            interval: StringEdit::Replace {
                expected: Some("daily".into()),
                value: Some("weekly".into()),
            },
            until: StringEdit::Replace {
                expected: None,
                value: Some("2030-03-01T00:00:00Z".into()),
            },
        },
    )
    .unwrap();
    assert_eq!(properties(dir.path(), &t.uuid)["until"], "1898553600");
    let current = r.get_task(t.uuid.clone()).unwrap().unwrap();
    r.update_recurrence_template(
        patch(&current),
        RecurrenceRulePatch {
            interval: StringEdit::Unchanged,
            until: StringEdit::Replace {
                expected: Some("1898553600".into()),
                value: None,
            },
        },
    )
    .unwrap();
    assert!(!properties(dir.path(), &t.uuid).contains_key("until"));
    assert_eq!(r.all_task_data().unwrap().len(), 1);
    let count = operation_count(dir.path());
    assert!(r
        .update_recurrence_template(
            patch(&current),
            RecurrenceRulePatch {
                interval: StringEdit::Replace {
                    expected: Some("daily".into()),
                    value: Some("monthly".into())
                },
                until: StringEdit::Unchanged
            }
        )
        .is_err());
    assert_eq!(operation_count(dir.path()), count);
}
#[test]
fn generated_or_uncertain_series_require_replacement_and_stop_keeps_children() {
    for mask in ["--", ""] {
        let dir = tempfile::tempdir().unwrap();
        seed(dir.path(), &series(mask));
        let r = wrapper(dir.path());
        let t = r.get_task(PARENT.into()).unwrap().unwrap();
        let p = patch(&t);
        let change = RecurrenceRulePatch {
            interval: StringEdit::Replace {
                expected: Some("daily".into()),
                value: Some("weekly".into()),
            },
            until: StringEdit::Unchanged,
        };
        let before = operation_count(dir.path());
        assert!(r
            .update_recurrence_template(p.clone(), change.clone())
            .is_err());
        assert_eq!(operation_count(dir.path()), before);
        let replacement = r.replace_recurrence(PARENT.into(), p, change).unwrap();
        assert_ne!(replacement.uuid, PARENT);
        let raw = properties(dir.path(), &replacement.uuid);
        assert_eq!(raw["custom"], "parent UDA");
        assert_eq!(raw["recur"], "weekly");
        for key in [
            "end", "start", "mask", "parent", "imask", "last", "template",
        ] {
            assert!(!raw.contains_key(key), "{key}");
        }
        assert_eq!(properties(dir.path(), PARENT)["status"], "deleted");
        assert_eq!(properties(dir.path(), CHILD0)["parent"], PARENT);
        assert!(r
            .replace_recurrence(PARENT.into(), patch(&t), unchanged())
            .is_err());
        assert_eq!(r.all_task_data().unwrap().len(), 4);
        r.stop_recurrence(replacement.uuid.clone()).unwrap();
        assert_eq!(properties(dir.path(), CHILD0)["status"], "pending");
        assert!(r
            .update_task_status(replacement.uuid, TaskStatus::Pending)
            .is_err());
    }
}
#[test]
fn content_only_preserves_unsupported_raw_rules_and_until_but_schedule_requires_correction() {
    let dir = tempfile::tempdir().unwrap();
    seed(dir.path(), &series("--"));
    seed(
        dir.path(),
        &[(
            PARENT,
            vec![("recur", "imported-rule"), ("until", "broken")],
        )],
    );
    let r = wrapper(dir.path());
    let t = r.get_task(PARENT.into()).unwrap().unwrap();
    let mut p = patch(&t);
    p.description = StringEdit::Replace {
        expected: Some(t.description.clone()),
        value: Some("Edited".into()),
    };
    r.update_recurrence_template(p, unchanged()).unwrap();
    let raw = properties(dir.path(), PARENT);
    assert_eq!(raw["until"], "broken");
    assert_eq!(raw["recur"], "imported-rule");
    let mut p = patch(&r.get_task(PARENT.into()).unwrap().unwrap());
    p.due = StringEdit::Replace {
        expected: t.due,
        value: Some("2030-02-01T00:00:00Z".into()),
    };
    let before = operation_count(dir.path());
    assert!(r
        .replace_recurrence(PARENT.into(), p.clone(), unchanged())
        .is_err());
    assert_eq!(operation_count(dir.path()), before);
    let replacement = r
        .replace_recurrence(
            PARENT.into(),
            p,
            RecurrenceRulePatch {
                interval: StringEdit::Replace {
                    expected: Some("imported-rule".into()),
                    value: Some("daily".into()),
                },
                until: StringEdit::Replace {
                    expected: Some("broken".into()),
                    value: None,
                },
            },
        )
        .unwrap();
    assert_eq!(replacement.status, TaskStatus::Recurring);
    assert!(!properties(dir.path(), &replacement.uuid).contains_key("until"));
}

#[test]
fn replacement_preserves_complete_native_inheritance_and_rejects_unsupported_or_stale_sources() {
    let dir = tempfile::tempdir().unwrap();
    seed(dir.path(), &series("--"));
    seed(
        dir.path(),
        &[(
            PARENT,
            vec![
                ("annotation_1700000000", "parent note"),
                ("tag_home", "x"),
                ("dep_10000000-0000-4000-8000-000000000003", "x"),
                ("future_attribute", "keep verbatim"),
                ("start", "1800000000"),
                ("end", "1800000001"),
            ],
        )],
    );
    let r = wrapper(dir.path());
    let t = r.get_task(PARENT.into()).unwrap().unwrap();
    let mut p = patch(&t);
    p.description = StringEdit::Replace {
        expected: Some(t.description.clone()),
        value: Some("Replacement".into()),
    };
    let fresh = r.replace_recurrence(PARENT.into(), p, unchanged()).unwrap();
    let raw = properties(dir.path(), &fresh.uuid);
    for (key, value) in [
        ("annotation_1700000000", "parent note"),
        ("tag_home", "x"),
        ("dep_10000000-0000-4000-8000-000000000003", "x"),
        ("future_attribute", "keep verbatim"),
    ] {
        assert_eq!(raw[key], value);
    }
    assert_eq!(
        properties(dir.path(), PARENT)["description"],
        "Daily fixture"
    );
    assert_eq!(raw["description"], "Replacement");
    for key in ["start", "end", "mask"] {
        assert!(!raw.contains_key(key));
    }
    let dir = tempfile::tempdir().unwrap();
    seed(dir.path(), &series("--"));
    seed(dir.path(), &[(PARENT, vec![("rtype", "on-completion")])]);
    let r = wrapper(dir.path());
    let t = r.get_task(PARENT.into()).unwrap().unwrap();
    let before = operation_count(dir.path());
    assert!(r
        .replace_recurrence(PARENT.into(), patch(&t), unchanged())
        .is_err());
    assert!(r.stop_recurrence(PARENT.into()).is_err());
    assert_eq!(operation_count(dir.path()), before);
}

#[test]
fn malformed_due_can_be_corrected_using_an_exact_raw_expectation() {
    let dir = tempfile::tempdir().unwrap();
    seed(
        dir.path(),
        &[(
            PARENT,
            vec![
                ("status", "recurring"),
                ("recur", "daily"),
                ("due", "broken"),
                ("description", "Malformed due"),
            ],
        )],
    );
    let r = wrapper(dir.path());
    let t = r.get_task(PARENT.into()).unwrap().unwrap();
    let mut p = patch(&t);
    p.due = StringEdit::Replace {
        expected: Some("broken".into()),
        value: Some("2030-01-01T00:00:00Z".into()),
    };
    r.update_recurrence_template(p, unchanged()).unwrap();
    assert_eq!(properties(dir.path(), PARENT)["due"], "1893456000");
}

#[test]
fn all_generated_schedule_fields_are_locked_even_with_missing_or_empty_masks() {
    for layout in ["child-no-mask", "empty-mask", "purged-children"] {
        let dir = tempfile::tempdir().unwrap();
        let mut records = series("--");
        match layout {
            "child-no-mask" => records[0].1.retain(|(key, _)| *key != "mask"),
            "empty-mask" => {
                records.truncate(1);
                records[0].1.retain(|(key, _)| *key != "mask");
                records[0].1.push(("mask", ""));
            }
            _ => records.truncate(1),
        }
        seed(dir.path(), &records);
        let r = wrapper(dir.path());
        let t = r.get_task(PARENT.into()).unwrap().unwrap();
        let before = operation_count(dir.path());
        for field in ["due", "wait", "scheduled", "recur", "until"] {
            let mut edit = patch(&t);
            let mut rule = unchanged();
            let date = StringEdit::Replace {
                expected: None,
                value: Some("2030-02-01T00:00:00Z".into()),
            };
            match field {
                "due" => {
                    edit.due = StringEdit::Replace {
                        expected: t.due.clone(),
                        value: Some("2030-02-01T00:00:00Z".into()),
                    }
                }
                "wait" => edit.wait = date,
                "scheduled" => edit.scheduled = date,
                "recur" => {
                    rule.interval = StringEdit::Replace {
                        expected: Some("daily".into()),
                        value: Some("weekly".into()),
                    }
                }
                _ => rule.until = date,
            }
            assert!(
                r.update_recurrence_template(edit, rule).is_err(),
                "{layout}: {field}"
            );
            assert_eq!(operation_count(dir.path()), before);
        }
        let mut content = patch(&t);
        content.description = StringEdit::Replace {
            expected: Some(t.description.clone()),
            value: Some("Content only".into()),
        };
        r.update_recurrence_template(content, unchanged()).unwrap();
        assert_eq!(properties(dir.path(), PARENT)["recur"], "daily");
    }
}

#[test]
fn legacy_retired_and_stale_template_mutations_are_atomic_rejections() {
    for (key, value) in [
        ("last", "1893456000"),
        ("template", "legacy"),
        ("rtype", "unsupported"),
        ("status", "deleted"),
    ] {
        let dir = tempfile::tempdir().unwrap();
        seed(dir.path(), &series("--"));
        seed(dir.path(), &[(PARENT, vec![(key, value)])]);
        let r = wrapper(dir.path());
        let t = r.get_task(PARENT.into()).unwrap().unwrap();
        let before = operation_count(dir.path());
        assert!(r
            .update_recurrence_template(patch(&t), unchanged())
            .is_err());
        assert!(r
            .replace_recurrence(PARENT.into(), patch(&t), unchanged())
            .is_err());
        assert!(r.stop_recurrence(PARENT.into()).is_err());
        assert_eq!(operation_count(dir.path()), before);
        assert_eq!(r.all_task_data().unwrap().len(), 3);
    }
    let dir = tempfile::tempdir().unwrap();
    let r = wrapper(dir.path());
    let t = r.add_recurring_task(props(), rule("daily")).unwrap();
    let before = operation_count(dir.path());
    let mut stale = patch(&t);
    stale.description = StringEdit::Replace {
        expected: Some("stale".into()),
        value: Some("new".into()),
    };
    assert!(r
        .replace_recurrence(t.uuid.clone(), stale, unchanged())
        .is_err());
    let mut started = patch(&t);
    started.start = StringEdit::Replace {
        expected: None,
        value: Some("2030-01-01T00:00:00Z".into()),
    };
    assert!(r
        .update_recurrence_template(started.clone(), unchanged())
        .is_err());
    assert!(r
        .replace_recurrence(t.uuid.clone(), started, unchanged())
        .is_err());
    assert!(r
        .replace_recurrence(PARENT.into(), patch(&t), unchanged())
        .is_err());
    assert_eq!(operation_count(dir.path()), before);
    assert_eq!(r.all_task_data().unwrap().len(), 1);
}

#[test]
fn unreadable_offsets_are_preserved_for_content_but_require_explicit_schedule_correction() {
    for key in ["wait", "scheduled"] {
        let dir = tempfile::tempdir().unwrap();
        let r = wrapper(dir.path());
        let t = r.add_recurring_task(props(), rule("daily")).unwrap();
        seed(dir.path(), &[(t.uuid.as_str(), vec![(key, "broken")])]);
        let t = r.get_task(t.uuid).unwrap().unwrap();
        let mut content = patch(&t);
        content.description = StringEdit::Replace {
            expected: Some(t.description.clone()),
            value: Some("Keep imported offset".into()),
        };
        r.update_recurrence_template(content, unchanged()).unwrap();
        assert_eq!(properties(dir.path(), &t.uuid)[key], "broken");
        let current = r.get_task(t.uuid.clone()).unwrap().unwrap();
        let change = RecurrenceRulePatch {
            interval: StringEdit::Replace {
                expected: Some("daily".into()),
                value: Some("weekly".into()),
            },
            until: StringEdit::Unchanged,
        };
        let count = operation_count(dir.path());
        assert!(
            r.update_recurrence_template(patch(&current), change.clone())
                .is_err(),
            "{key}"
        );
        assert!(
            r.replace_recurrence(t.uuid.clone(), patch(&current), change.clone())
                .is_err(),
            "{key}"
        );
        assert_eq!(operation_count(dir.path()), count);
        let mut correct = patch(&current);
        let clear = StringEdit::Replace {
            expected: Some("broken".into()),
            value: None,
        };
        if key == "wait" {
            correct.wait = clear;
        } else {
            correct.scheduled = clear;
        }
        r.update_recurrence_template(correct, change).unwrap();
        assert!(!properties(dir.path(), &t.uuid).contains_key(key));
    }
}
