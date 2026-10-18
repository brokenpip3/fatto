use super::*;

fn props() -> TaskAddProps {
    TaskAddProps {
        description: "Date display fixture".into(),
        project: None,
        tags: vec![],
        due: Some("2030-01-02T00:00:00Z".into()),
        wait: None,
        scheduled: None,
        start: None,
        priority: None,
        dependencies: vec![],
        date_modes: DateDisplayModes::default(),
    }
}

fn patch(task: &TaskData) -> TaskEditPatch {
    TaskEditPatch {
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
        date_modes: DateDisplayModesPatch::default(),
    }
}

fn raw(wrapper: &ReplicaWrapper, uuid: &str, key: &str, value: &str) {
    let mut replica = wrapper.inner.lock().unwrap();
    wrapper.rt.block_on(async {
        let mut task = replica
            .get_task(Uuid::parse_str(uuid).unwrap())
            .await
            .unwrap()
            .unwrap();
        let mut ops = Operations::new();
        task.set_value(key, Some(value.to_owned()), &mut ops)
            .unwrap();
        replica.commit_operations(ops).await.unwrap();
    });
}

fn uda(task: &TaskData, key: &str) -> Option<String> {
    task.udas
        .iter()
        .find(|p| p.key == key)
        .map(|p| p.value.clone())
}

#[test]
fn date_display_unmarked_and_unknown_values_are_legacy_and_survive_other_edits() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let task = wrapper.add_task(props()).unwrap();
    assert_eq!(task.date_modes.due, DateDisplayMode::CalendarDate);
    assert_eq!(uda(&task, "fatto_display_due"), None);
    raw(&wrapper, &task.uuid, "fatto_display_due", "v99:keep");
    let task = wrapper.get_task(task.uuid).unwrap().unwrap();
    let mut edit = patch(&task);
    edit.description = StringEdit::Replace {
        expected: Some(task.description.clone()),
        value: Some("Changed title".into()),
    };
    wrapper.update_task(edit).unwrap();
    let result = wrapper.get_task(task.uuid).unwrap().unwrap();
    assert_eq!(result.date_modes.due, DateDisplayMode::CalendarDate);
    assert_eq!(
        uda(&result, "fatto_display_due").as_deref(),
        Some("v99:keep")
    );
    assert_eq!(result.due, task.due);
}

#[test]
fn date_display_explicit_midnight_survives_creation_and_equal_instant_mode_edits() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let mut input = props();
    input.date_modes.due = DateDisplayMode::LocalDateTime;
    let task = wrapper.add_task(input).unwrap();
    assert_eq!(task.date_modes.due, DateDisplayMode::LocalDateTime);
    assert_eq!(
        uda(&task, "fatto_display_due").as_deref(),
        Some("v1:local-datetime")
    );
    let mut edit = patch(&task);
    edit.due = StringEdit::Replace {
        expected: task.due.clone(),
        value: task.due.clone(),
    };
    edit.date_modes.due = DateModeEdit::Replace {
        expected: uda(&task, "fatto_display_due"),
        value: DateDisplayMode::CalendarDate,
    };
    wrapper.update_task(edit).unwrap();
    let result = wrapper.get_task(task.uuid).unwrap().unwrap();
    assert_eq!(result.due, task.due);
    assert_eq!(result.date_modes.due, DateDisplayMode::CalendarDate);
    assert_eq!(uda(&result, "fatto_display_due"), None);
}

#[test]
fn date_display_stale_marker_check_conflicts_before_any_timestamp_or_title_write() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let task = wrapper.add_task(props()).unwrap();
    let mut edit = patch(&task);
    edit.description = StringEdit::Replace {
        expected: Some(task.description.clone()),
        value: Some("Must not commit".into()),
    };
    edit.due = StringEdit::Replace {
        expected: task.due.clone(),
        value: Some("2030-01-03T00:00:00Z".into()),
    };
    edit.date_modes.due = DateModeEdit::Check { expected: None };
    raw(
        &wrapper,
        &task.uuid,
        "fatto_display_due",
        "v1:local-datetime",
    );
    assert!(
        matches!(wrapper.update_task(edit), Err(TaskError::EditConflict { fields, .. }) if fields.contains(&"due".into()))
    );
    let result = wrapper.get_task(task.uuid).unwrap().unwrap();
    assert_eq!(result.due, task.due);
    assert_eq!(result.description, task.description);
    assert_eq!(result.date_modes.due, DateDisplayMode::LocalDateTime);
}

#[test]
fn date_display_check_only_preserves_an_unknown_marker_during_a_date_edit() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let task = wrapper.add_task(props()).unwrap();
    raw(&wrapper, &task.uuid, "fatto_display_due", "v99:keep");
    let task = wrapper.get_task(task.uuid).unwrap().unwrap();
    let mut edit = patch(&task);
    edit.due = StringEdit::Replace {
        expected: task.due.clone(),
        value: Some("2030-01-03T00:00:00Z".into()),
    };
    edit.date_modes.due = DateModeEdit::Check {
        expected: Some("v99:keep".into()),
    };
    wrapper.update_task(edit).unwrap();
    let result = wrapper.get_task(task.uuid).unwrap().unwrap();
    assert_eq!(result.due.as_deref(), Some("2030-01-03T00:00:00+00:00"));
    assert_eq!(
        uda(&result, "fatto_display_due").as_deref(),
        Some("v99:keep")
    );
}

#[test]
fn date_display_mode_only_changes_need_a_checked_current_timestamp() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let task = wrapper.add_task(props()).unwrap();
    let mut edit = patch(&task);
    edit.date_modes.due = DateModeEdit::Replace {
        expected: None,
        value: DateDisplayMode::LocalDateTime,
    };
    assert!(wrapper.update_task(edit).is_err());
    let mut edit = patch(&task);
    edit.date_modes.due = DateModeEdit::Replace {
        expected: None,
        value: DateDisplayMode::LocalDateTime,
    };
    edit.due = StringEdit::Replace {
        expected: task.due.clone(),
        value: task.due.clone(),
    };
    raw(&wrapper, &task.uuid, "due", "1893628800");
    assert!(matches!(
        wrapper.update_task(edit),
        Err(TaskError::EditConflict { .. })
    ));
    assert_eq!(
        uda(
            &wrapper.get_task(task.uuid).unwrap().unwrap(),
            "fatto_display_due"
        ),
        None
    );
}

#[test]
fn date_display_rejects_timed_missing_dates_and_clears_markers_with_the_field() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let mut input = props();
    input.date_modes.wait = DateDisplayMode::LocalDateTime;
    assert!(wrapper.add_task(input).is_err());
    assert!(wrapper.all_task_data().unwrap().is_empty());
    let mut input = props();
    input.date_modes.due = DateDisplayMode::LocalDateTime;
    let task = wrapper.add_task(input).unwrap();
    let mut edit = patch(&task);
    edit.due = StringEdit::Replace {
        expected: task.due.clone(),
        value: None,
    };
    edit.date_modes.due = DateModeEdit::Replace {
        expected: uda(&task, "fatto_display_due"),
        value: DateDisplayMode::CalendarDate,
    };
    wrapper.update_task(edit).unwrap();
    let result = wrapper.get_task(task.uuid).unwrap().unwrap();
    assert!(result.due.is_none());
    assert_eq!(uda(&result, "fatto_display_due"), None);
}

#[test]
fn date_display_mixed_generated_series_modes_are_independent_and_replacement_preserves_children() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let mut input = props();
    input.wait = Some("2029-12-29T12:30:45Z".into());
    input.scheduled = Some("2030-01-01T00:00:00Z".into());
    input.date_modes.due = DateDisplayMode::LocalDateTime;
    input.date_modes.wait = DateDisplayMode::LocalDateTime;
    let parent = wrapper
        .add_recurring_task(
            input,
            RecurrenceRule {
                interval: "daily".into(),
                until: None,
            },
        )
        .unwrap();
    raw(&wrapper, &parent.uuid, "mask", "-");
    raw(
        &wrapper,
        &parent.uuid,
        "fatto_display_scheduled",
        "v99:keep",
    );
    let child = wrapper.add_task(props()).unwrap();
    raw(&wrapper, &child.uuid, "parent", &parent.uuid);
    raw(&wrapper, &child.uuid, "imask", "0");
    let original_child = wrapper.get_task(child.uuid.clone()).unwrap().unwrap();
    let parent = wrapper.get_task(parent.uuid).unwrap().unwrap();
    let mut edit = patch(&parent);
    edit.due = StringEdit::Replace {
        expected: parent.due.clone(),
        value: parent.due.clone(),
    };
    edit.date_modes.due = DateModeEdit::Replace {
        expected: uda(&parent, "fatto_display_due"),
        value: DateDisplayMode::CalendarDate,
    };
    let rule = || RecurrenceRulePatch {
        interval: StringEdit::Unchanged,
        until: StringEdit::Unchanged,
    };
    wrapper.update_recurrence_template(edit, rule()).unwrap();
    let updated = wrapper.get_task(parent.uuid.clone()).unwrap().unwrap();
    assert_eq!(updated.date_modes.due, DateDisplayMode::CalendarDate);
    assert_eq!(updated.date_modes.wait, DateDisplayMode::LocalDateTime);
    assert_eq!(updated.due, parent.due);
    assert_eq!(
        uda(&updated, "fatto_display_scheduled").as_deref(),
        Some("v99:keep")
    );
    let replacement = wrapper
        .replace_recurrence(parent.uuid, patch(&updated), rule())
        .unwrap();
    assert_eq!(replacement.date_modes.due, DateDisplayMode::CalendarDate);
    assert_eq!(replacement.date_modes.wait, DateDisplayMode::LocalDateTime);
    assert_eq!(
        uda(&replacement, "fatto_display_scheduled").as_deref(),
        Some("v99:keep")
    );
    let child = wrapper.get_task(child.uuid).unwrap().unwrap();
    assert_eq!(child.due, original_child.due);
    assert_eq!(
        child
            .native_dates
            .iter()
            .map(|p| (&p.key, &p.value))
            .collect::<Vec<_>>(),
        original_child
            .native_dates
            .iter()
            .map(|p| (&p.key, &p.value))
            .collect::<Vec<_>>()
    );
    assert_eq!(child.date_modes.due, DateDisplayMode::CalendarDate);
    assert_eq!(uda(&child, "fatto_display_due"), None);
    assert_eq!(
        wrapper.all_task_data().unwrap().len(),
        3,
        "Fatto never generates occurrences"
    );
}

#[test]
fn date_display_ungenerated_schedule_edits_and_review_status_transitions_keep_modes() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let parent = wrapper
        .add_recurring_task(
            props(),
            RecurrenceRule {
                interval: "daily".into(),
                until: None,
            },
        )
        .unwrap();
    let mut edit = patch(&parent);
    edit.due = StringEdit::Replace {
        expected: parent.due.clone(),
        value: Some("2030-01-03T12:30:45Z".into()),
    };
    edit.date_modes.due = DateModeEdit::Replace {
        expected: None,
        value: DateDisplayMode::LocalDateTime,
    };
    wrapper
        .update_recurrence_template(
            edit,
            RecurrenceRulePatch {
                interval: StringEdit::Unchanged,
                until: StringEdit::Unchanged,
            },
        )
        .unwrap();
    let current = wrapper.get_task(parent.uuid).unwrap().unwrap();
    assert_eq!(current.date_modes.due, DateDisplayMode::LocalDateTime);
    assert_eq!(
        parse_rfc3339(current.due).unwrap().unwrap().timestamp(),
        1893673845
    );

    let mut input = props();
    input.date_modes.due = DateDisplayMode::LocalDateTime;
    let task = wrapper.add_task(input).unwrap();
    let mut edit = patch(&task);
    edit.description = StringEdit::Replace {
        expected: Some(task.description.clone()),
        value: Some("Reviewed title".into()),
    };
    wrapper.update_task_for_review(edit).unwrap();
    let current = wrapper.get_task(task.uuid.clone()).unwrap().unwrap();
    assert!(uda(&current, "reviewed").is_some());
    assert_eq!(current.date_modes.due, DateDisplayMode::LocalDateTime);
    for status in [TaskStatus::Completed, TaskStatus::Deleted] {
        wrapper
            .update_task_status(task.uuid.clone(), status)
            .unwrap();
        let current = wrapper.get_task(task.uuid.clone()).unwrap().unwrap();
        assert_eq!(current.date_modes.due, DateDisplayMode::LocalDateTime);
        assert_eq!(current.due, task.due);
        assert_eq!(
            uda(&current, "fatto_display_due"),
            uda(&task, "fatto_display_due")
        );
    }
}

#[test]
fn date_display_reverse_dependency_review_cannot_change_modes_or_partially_stamp_review() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let reviewed = wrapper.add_task(props()).unwrap();
    let dependent = wrapper.add_task(props()).unwrap();
    let mut edit = patch(&dependent);
    edit.dependencies = StringSetEdit::Replace {
        expected: vec![],
        value: vec![reviewed.uuid.clone()],
    };
    edit.due = StringEdit::Replace {
        expected: dependent.due.clone(),
        value: dependent.due.clone(),
    };
    edit.date_modes.due = DateModeEdit::Replace {
        expected: None,
        value: DateDisplayMode::LocalDateTime,
    };
    assert!(wrapper
        .update_dependencies_for_review(reviewed.uuid.clone(), edit)
        .is_err());
    let dependent = wrapper.get_task(dependent.uuid).unwrap().unwrap();
    assert!(dependent.dependencies.is_empty());
    assert_eq!(uda(&dependent, "fatto_display_due"), None);
    assert_eq!(
        uda(
            &wrapper.get_task(reviewed.uuid).unwrap().unwrap(),
            "reviewed"
        ),
        None
    );
}

#[test]
fn date_display_review_mode_change_is_atomic_with_timestamp_and_review_stamp() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let task = wrapper.add_task(props()).unwrap();
    let mut edit = patch(&task);
    edit.due = StringEdit::Replace {
        expected: task.due.clone(),
        value: task.due.clone(),
    };
    edit.date_modes.due = DateModeEdit::Replace {
        expected: None,
        value: DateDisplayMode::LocalDateTime,
    };
    raw(&wrapper, &task.uuid, "fatto_display_due", "v99:concurrent");
    assert!(
        matches!(wrapper.update_task_for_review(edit), Err(TaskError::EditConflict { fields, .. }) if fields == vec!["due"])
    );
    let current = wrapper.get_task(task.uuid.clone()).unwrap().unwrap();
    assert_eq!(uda(&current, "reviewed"), None);
    assert_eq!(current.due, task.due);
    let mut edit = patch(&current);
    edit.due = StringEdit::Replace {
        expected: current.due.clone(),
        value: current.due.clone(),
    };
    edit.date_modes.due = DateModeEdit::Replace {
        expected: uda(&current, "fatto_display_due"),
        value: DateDisplayMode::LocalDateTime,
    };
    wrapper.update_task_for_review(edit).unwrap();
    let updated = wrapper.get_task(task.uuid).unwrap().unwrap();
    assert_eq!(updated.due, task.due);
    assert_eq!(updated.date_modes.due, DateDisplayMode::LocalDateTime);
    assert!(uda(&updated, "reviewed").is_some());
}

#[test]
fn date_display_check_only_source_preserves_raw_dates_and_guards_concurrent_changes() {
    for stored in [
        None,
        Some("malformed-scheduled"),
        Some("2030-01-03T00:00:45Z"),
    ] {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper.add_task(props()).unwrap();
        if let Some(stored) = stored {
            raw(&wrapper, &task.uuid, "scheduled", stored);
        }
        raw(
            &wrapper,
            &task.uuid,
            "fatto_display_scheduled",
            "v99:future",
        );
        let baseline = wrapper.get_task(task.uuid.clone()).unwrap().unwrap();
        let mut edit = patch(&baseline);
        edit.due = StringEdit::Replace {
            expected: baseline.due.clone(),
            value: Some("2030-02-01T00:00:00Z".into()),
        };
        edit.date_modes.due = DateModeEdit::Check { expected: None };
        edit.scheduled = StringEdit::Check {
            expected: stored.map(str::to_owned),
        };
        edit.date_modes.scheduled = DateModeEdit::Check {
            expected: Some("v99:future".into()),
        };
        wrapper.update_task(edit).unwrap();
        let kept = wrapper.get_task(task.uuid.clone()).unwrap().unwrap();
        assert!(kept.scheduled.is_none());
        assert_eq!(
            kept.native_dates
                .iter()
                .find(|pair| pair.key == "scheduled")
                .map(|pair| pair.value.as_str()),
            stored
        );
        assert_eq!(
            uda(&kept, "fatto_display_scheduled").as_deref(),
            Some("v99:future")
        );
        let mut stale = patch(&kept);
        stale.due = StringEdit::Replace {
            expected: kept.due.clone(),
            value: Some("2030-03-01T00:00:00Z".into()),
        };
        stale.scheduled = StringEdit::Check {
            expected: stored.map(str::to_owned),
        };
        stale.date_modes.scheduled = DateModeEdit::Check {
            expected: Some("v99:future".into()),
        };
        raw(&wrapper, &task.uuid, "scheduled", "changed-remotely");
        assert!(
            matches!(wrapper.update_task(stale), Err(TaskError::EditConflict { fields, .. }) if fields.contains(&"scheduled".into()))
        );
        assert_eq!(wrapper.get_task(task.uuid).unwrap().unwrap().due, kept.due);
    }
}

fn display_baseline(task: &TaskData, field: &str) -> Option<String> {
    uda(task, &format!("fatto_display_{field}"))
}

#[test]
fn date_display_new_tasks_use_canonical_names_only() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let mut input = props();
    input.scheduled = input.due.clone();
    input.wait = input.due.clone();
    input.date_modes = DateDisplayModes {
        due: DateDisplayMode::LocalDateTime,
        scheduled: DateDisplayMode::LocalDateTime,
        wait: DateDisplayMode::LocalDateTime,
    };
    let task = wrapper.add_task(input).unwrap();
    for field in ["due", "scheduled", "wait"] {
        assert_eq!(
            uda(&task, &format!("fatto_display_{field}")).as_deref(),
            Some("v1:local-datetime")
        );
        assert_eq!(uda(&task, &format!("fatto_{field}_display")), None);
    }
}

#[test]
fn date_display_only_recognizes_exact_canonical_values() {
    for canonical in [
        None,
        Some("v1:local-datetime"),
        Some("v99:future"),
        Some(""),
    ] {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper.add_task(props()).unwrap();
        raw(&wrapper, &task.uuid, "external_custom", "v1:local-datetime");
        if let Some(value) = canonical {
            raw(&wrapper, &task.uuid, "fatto_display_due", value);
        }
        let before = wrapper.get_task(task.uuid.clone()).unwrap().unwrap();
        let expected = if canonical == Some("v1:local-datetime") {
            DateDisplayMode::LocalDateTime
        } else {
            DateDisplayMode::CalendarDate
        };
        assert_eq!(before.date_modes.due, expected);
        let mut edit = patch(&before);
        edit.description = StringEdit::Replace {
            expected: Some(before.description.clone()),
            value: Some("Unrelated edit".into()),
        };
        wrapper.update_task(edit).unwrap();
        let after = wrapper.get_task(task.uuid).unwrap().unwrap();
        assert_eq!(uda(&after, "fatto_display_due").as_deref(), canonical);
        assert_eq!(
            uda(&after, "external_custom").as_deref(),
            Some("v1:local-datetime")
        );
        assert_eq!(after.due, before.due);
    }
}

#[test]
fn date_display_guard_checks_canonical_key_before_any_writes() {
    {
        let changed_key = "fatto_display_due";
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper.add_task(props()).unwrap();
        raw(&wrapper, &task.uuid, "external_custom", "v1:local-datetime");
        let task = wrapper.get_task(task.uuid).unwrap().unwrap();
        let mut edit = patch(&task);
        edit.description = StringEdit::Replace {
            expected: Some(task.description.clone()),
            value: Some("Must not save".into()),
        };
        edit.due = StringEdit::Replace {
            expected: task.due.clone(),
            value: Some("2030-01-03T00:00:00Z".into()),
        };
        edit.date_modes.due = DateModeEdit::Check {
            expected: display_baseline(&task, "due"),
        };
        raw(&wrapper, &task.uuid, changed_key, "v99:concurrent");
        assert!(
            matches!(wrapper.update_task(edit), Err(TaskError::EditConflict { fields, .. }) if fields.contains(&"due".into()))
        );
        let after = wrapper.get_task(task.uuid).unwrap().unwrap();
        assert_eq!(after.description, task.description);
        assert_eq!(after.due, task.due);
        assert_eq!(uda(&after, changed_key).as_deref(), Some("v99:concurrent"));
    }
}

#[test]
fn date_display_edit_and_clear_preserve_unrelated_metadata() {
    for timed in [true, false] {
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper.add_task(props()).unwrap();
        raw(&wrapper, &task.uuid, "external_custom", "v1:local-datetime");
        let task = wrapper.get_task(task.uuid).unwrap().unwrap();
        let mut edit = patch(&task);
        edit.due = StringEdit::Replace {
            expected: task.due.clone(),
            value: if timed { task.due.clone() } else { None },
        };
        edit.date_modes.due = DateModeEdit::Replace {
            expected: display_baseline(&task, "due"),
            value: if timed {
                DateDisplayMode::LocalDateTime
            } else {
                DateDisplayMode::CalendarDate
            },
        };
        wrapper.update_task(edit).unwrap();
        let after = wrapper.get_task(task.uuid).unwrap().unwrap();
        assert_eq!(
            uda(&after, "external_custom").as_deref(),
            Some("v1:local-datetime")
        );
        assert_eq!(
            uda(&after, "fatto_display_due").as_deref(),
            timed.then_some("v1:local-datetime")
        );
        assert_eq!(after.due, if timed { task.due } else { None });
    }
}

#[test]
fn date_display_removing_canonical_key_rejects_a_stale_patch() {
    {
        let removed = "fatto_display_due";
        let wrapper = ReplicaWrapper::new_in_memory().unwrap();
        let task = wrapper.add_task(props()).unwrap();
        raw(
            &wrapper,
            &task.uuid,
            "fatto_display_due",
            "v1:local-datetime",
        );
        raw(&wrapper, &task.uuid, "external_custom", "v1:local-datetime");
        let task = wrapper.get_task(task.uuid).unwrap().unwrap();
        let mut edit = patch(&task);
        edit.description = StringEdit::Replace {
            expected: Some(task.description.clone()),
            value: Some("Must not save".into()),
        };
        edit.due = StringEdit::Replace {
            expected: task.due.clone(),
            value: task.due.clone(),
        };
        edit.date_modes.due = DateModeEdit::Replace {
            expected: display_baseline(&task, "due"),
            value: DateDisplayMode::CalendarDate,
        };
        let mut replica = wrapper.inner.lock().unwrap();
        wrapper.rt.block_on(async {
            let mut current = replica
                .get_task(Uuid::parse_str(&task.uuid).unwrap())
                .await
                .unwrap()
                .unwrap();
            let mut operations = Operations::new();
            current.set_value(removed, None, &mut operations).unwrap();
            replica.commit_operations(operations).await.unwrap();
        });
        drop(replica);
        assert!(matches!(
            wrapper.update_task(edit),
            Err(TaskError::EditConflict { .. })
        ));
        let after = wrapper.get_task(task.uuid).unwrap().unwrap();
        assert_eq!(after.description, task.description);
        assert_eq!(after.due, task.due);
        assert_eq!(uda(&after, removed), None);
        let retained = "external_custom";
        assert_eq!(uda(&after, retained).as_deref(), Some("v1:local-datetime"));
    }
}

#[test]
fn obsolete_display_keys_are_ordinary_udas_not_time_markers() {
    let wrapper = ReplicaWrapper::new_in_memory().unwrap();
    let task = wrapper.add_task(props()).unwrap();
    raw(
        &wrapper,
        &task.uuid,
        "fatto_due_display",
        "v1:local-datetime",
    );
    let task = wrapper.get_task(task.uuid).unwrap().unwrap();
    assert_eq!(task.date_modes.due, DateDisplayMode::CalendarDate);
}
