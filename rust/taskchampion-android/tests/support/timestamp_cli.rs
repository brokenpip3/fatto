//! Marker transport uses private replicas; it never opens the seeded CLI database.
use serde_json::Value;
use std::path::Path;
use std::process::{Command, Output};
use taskchampion_android::{
    RecurrenceRulePatch, ReplicaWrapper, StringEdit, StringSetEdit, TaskEditPatch, TaskStatus,
};

fn docker(args: &[&str]) -> Output {
    Command::new("docker")
        .current_dir(Path::new(env!("CARGO_MANIFEST_DIR")).join("../.."))
        .args(["compose", "exec", "-T", "taskwarrior-client"])
        .args(args)
        .output()
        .expect("Start the local Docker services with just sync-up")
}

fn successful(output: Output) -> String {
    assert!(
        output.status.success(),
        "{}",
        String::from_utf8_lossy(&output.stderr)
    );
    String::from_utf8(output.stdout).unwrap()
}

struct Cli {
    data: String,
    client: String,
}

impl Cli {
    fn new(client: String) -> Self {
        successful(docker(&[
            "sh",
            "-c",
            "for i in $(seq 1 60); do command -v task >/dev/null && exit 0; sleep 1; done; exit 1",
        ]));
        let cli = Self {
            data: format!("/tmp/fatto-date-display-{}", uuid::Uuid::new_v4()),
            client,
        };
        successful(docker(&["mkdir", "-p", &cli.data]));
        cli
    }

    fn task(&self, args: &[&str]) -> String {
        let data = format!("rc.data.location:{}", self.data);
        let client = format!("rc.sync.server.client_id:{}", self.client);
        let mut command = vec![
            "task",
            "rc:/dev/null",
            &data,
            &client,
            "rc.sync.server.url:http://tss:8080",
            "rc.sync.encryption_secret:date-display-test",
            "rc.confirmation:off",
            "rc.hooks:off",
            "rc.recurrence:off",
            "rc.verbose:nothing",
        ];
        command.extend_from_slice(args);
        successful(docker(&command))
    }

    fn marked(&self, args: &[&str]) -> String {
        let mut options = vec![
            "rc.uda.fatto_display_due.type:string",
            "rc.uda.fatto_display_scheduled.type:string",
            "rc.uda.fatto_display_wait.type:string",
        ];
        options.extend_from_slice(args);
        self.task(&options)
    }

    fn export(&self) -> Vec<Value> {
        serde_json::from_str(&self.task(&["export"])).unwrap()
    }
}

impl Drop for Cli {
    fn drop(&mut self) {
        let _ = docker(&["rm", "-rf", &self.data]);
    }
}

fn unchanged(uuid: String, status: TaskStatus) -> TaskEditPatch {
    TaskEditPatch {
        date_modes: Default::default(),
        uuid,
        expected_status: status,
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

#[test]
fn display_markers_survive_plain_cli_sync_recurrence_and_replacement() {
    let client = uuid::Uuid::new_v4().to_string();
    let first = Cli::new(client.clone());
    let plain = Cli::new(client.clone());
    first.marked(&[
        "add",
        "private display fixture",
        "due:2026-11-20T00:00:00Z",
        "scheduled:2026-11-19T14:30:45Z",
        "wait:2026-11-19T12:30:15Z",
        "fatto_display_due:v1:local-datetime",
        "fatto_display_scheduled:v1:local-datetime",
        "fatto_display_wait:v99:preserve-me",
    ]);
    first.task(&["sync"]);
    plain.task(&["sync"]);
    let received = plain.export();
    let task = received
        .iter()
        .find(|t| t["description"] == "private display fixture")
        .unwrap();
    let id = task["uuid"].as_str().unwrap();
    assert_eq!(task["fatto_display_due"], "v1:local-datetime");
    assert_eq!(task["fatto_display_scheduled"], "v1:local-datetime");
    assert_eq!(task["fatto_display_wait"], "v99:preserve-me");
    plain.task(&[id, "modify", "description:plain CLI edit", "due:2026-11-21"]);
    plain.task(&["sync"]);
    first.task(&["sync"]);
    let edited = first.export();
    let edited = edited.iter().find(|t| t["uuid"] == id).unwrap();
    assert_eq!(edited["fatto_display_due"], "v1:local-datetime");
    assert_eq!(edited["fatto_display_wait"], "v99:preserve-me");

    first.marked(&[
        "add",
        "private timed series",
        "due:today",
        "recur:daily",
        "fatto_display_due:v1:local-datetime",
        "fatto_display_wait:v99:preserve-me",
    ]);
    first.task(&["rc.recurrence:on", "rc.recurrence.limit:1", "next"]);
    let generated = first.export();
    let parent = generated
        .iter()
        .find(|t| t["status"] == "recurring")
        .unwrap();
    let parent_id = parent["uuid"].as_str().unwrap();
    let children: Vec<_> = generated
        .iter()
        .filter(|t| t["parent"] == parent_id)
        .collect();
    assert!(!children.is_empty());
    for child in children {
        assert_eq!(child["fatto_display_due"], "v1:local-datetime");
        assert_eq!(child["fatto_display_wait"], "v99:preserve-me");
    }
    first.task(&["sync"]);
    let dir = tempfile::tempdir().unwrap();
    let phone = ReplicaWrapper::new_on_disk(dir.path().to_string_lossy().into_owned()).unwrap();
    let sync = || {
        phone
            .sync(
                "http://localhost:8080".into(),
                client.clone(),
                "date-display-test".into(),
            )
            .unwrap()
    };
    sync();
    let replacement = phone
        .replace_recurrence(
            parent_id.into(),
            unchanged(parent_id.into(), TaskStatus::Recurring),
            RecurrenceRulePatch {
                interval: StringEdit::Unchanged,
                until: StringEdit::Unchanged,
            },
        )
        .unwrap();
    sync();
    plain.task(&["sync"]);
    let tasks = plain.export();
    let replacement = tasks
        .iter()
        .find(|t| t["uuid"] == replacement.uuid)
        .unwrap();
    assert_eq!(replacement["fatto_display_due"], "v1:local-datetime");
    assert_eq!(replacement["fatto_display_wait"], "v99:preserve-me");
}

#[test]
fn display_modes_copy_only_to_new_cli_children_and_cli_opt_out_preserves_the_timestamp() {
    let client = uuid::Uuid::new_v4().to_string();
    let cli = Cli::new(client.clone());
    cli.task(&[
        "add",
        "private late opt-in series",
        "due:today",
        "scheduled:today",
        "wait:yesterday",
        "recur:daily",
        "until:today+7days",
    ]);
    cli.task(&["rc.recurrence:on", "rc.recurrence.limit:1", "next"]);
    let first = cli.export();
    let parent = first.iter().find(|t| t["status"] == "recurring").unwrap();
    let parent_id = parent["uuid"].as_str().unwrap();
    let old_ids: Vec<_> = first
        .iter()
        .filter(|t| t["parent"] == parent_id)
        .map(|t| t["uuid"].as_str().unwrap().to_owned())
        .collect();
    assert!(!old_ids.is_empty());
    cli.marked(&[
        parent_id,
        "modify",
        "fatto_display_due:v1:local-datetime",
        "fatto_display_wait:v1:local-datetime",
    ]);
    cli.task(&["rc.recurrence:on", "rc.recurrence.limit:4", "next"]);
    let generated = cli.export();
    let children: Vec<_> = generated
        .iter()
        .filter(|t| t["parent"] == parent_id)
        .collect();
    assert!(
        children.len() > old_ids.len(),
        "CLI must actually generate later children for this probe"
    );
    for child in &children {
        if old_ids.iter().any(|id| child["uuid"] == *id) {
            assert!(child.get("fatto_display_due").is_none());
            assert!(child.get("fatto_display_wait").is_none());
        } else {
            assert_eq!(child["fatto_display_due"], "v1:local-datetime");
            assert_eq!(child["fatto_display_wait"], "v1:local-datetime");
        }
        assert!(child.get("fatto_display_scheduled").is_none());
    }
    cli.task(&["sync"]);
    let dir = tempfile::tempdir().unwrap();
    let phone = ReplicaWrapper::new_on_disk(dir.path().to_string_lossy().into_owned()).unwrap();
    let sync = || {
        phone
            .sync(
                "http://localhost:8080".into(),
                client.clone(),
                "date-display-test".into(),
            )
            .unwrap()
    };
    sync();
    let parent = phone.get_task(parent_id.into()).unwrap().unwrap();
    assert_eq!(
        parent.date_modes.due,
        taskchampion_android::DateDisplayMode::LocalDateTime
    );
    assert_eq!(
        parent.date_modes.scheduled,
        taskchampion_android::DateDisplayMode::CalendarDate
    );
    assert_eq!(
        parent.date_modes.wait,
        taskchampion_android::DateDisplayMode::LocalDateTime
    );
    for id in &old_ids {
        assert_eq!(
            phone.get_task(id.clone()).unwrap().unwrap().date_modes.due,
            taskchampion_android::DateDisplayMode::CalendarDate
        );
    }
    let due = parent.due;
    cli.marked(&[parent_id, "modify", "fatto_display_due:"]);
    cli.task(&["sync"]);
    sync();
    let opted_out = phone.get_task(parent_id.into()).unwrap().unwrap();
    assert_eq!(
        opted_out.date_modes.due,
        taskchampion_android::DateDisplayMode::CalendarDate
    );
    assert_eq!(opted_out.due, due);
    assert_eq!(
        opted_out.date_modes.wait,
        taskchampion_android::DateDisplayMode::LocalDateTime
    );
}

fn sync_phone(phone: &ReplicaWrapper, client: &str) {
    phone
        .sync(
            "http://localhost:8080".into(),
            client.into(),
            "date-display-test".into(),
        )
        .unwrap();
}

fn epoch(value: &str) -> i64 {
    chrono::DateTime::parse_from_rfc3339(value)
        .map(|date| date.timestamp())
        .unwrap_or_else(|_| {
            chrono::NaiveDateTime::parse_from_str(value, "%Y%m%dT%H%M%SZ")
                .unwrap()
                .and_utc()
                .timestamp()
        })
}

fn property<'a>(task: &'a taskchampion_android::TaskData, key: &str) -> Option<&'a str> {
    task.udas
        .iter()
        .find(|pair| pair.key == key)
        .map(|pair| pair.value.as_str())
}

#[test]
fn display_wrapper_round_trip_preserves_midnight_seconds_unknowns_opt_out_and_clear() {
    use taskchampion_android::{DateDisplayMode::*, DateDisplayModes, DateModeEdit, TaskAddProps};
    let client = uuid::Uuid::new_v4().to_string();
    let cli = Cli::new(client.clone());
    let imported = Cli::new(client.clone());
    let dir = tempfile::tempdir().unwrap();
    let phone = ReplicaWrapper::new_on_disk(dir.path().to_string_lossy().into_owned()).unwrap();
    let created = phone
        .add_task(TaskAddProps {
            description: "private three-field wrapper fixture".into(),
            project: None,
            tags: vec![],
            due: Some("2030-01-02T00:00:00Z".into()),
            scheduled: Some("2030-01-01T14:30:45Z".into()),
            wait: Some("2029-12-31T12:15:25Z".into()),
            start: None,
            priority: None,
            dependencies: vec![],
            date_modes: DateDisplayModes {
                due: LocalDateTime,
                scheduled: LocalDateTime,
                wait: LocalDateTime,
            },
        })
        .unwrap();
    sync_phone(&phone, &client);
    cli.task(&["sync"]);
    let first = cli.export();
    let first = first
        .iter()
        .find(|task| task["uuid"] == created.uuid)
        .unwrap();
    for (field, timestamp) in [
        ("due", created.due.as_deref().unwrap()),
        ("scheduled", created.scheduled.as_deref().unwrap()),
        ("wait", created.wait.as_deref().unwrap()),
    ] {
        assert_eq!(epoch(first[field].as_str().unwrap()), epoch(timestamp));
        assert_eq!(first[format!("fatto_display_{field}")], "v1:local-datetime");
    }
    cli.marked(&[
        "rc.uda.external_custom.type:string",
        &created.uuid,
        "modify",
        "fatto_display_scheduled:v99:preserve-me",
        "external_custom:retain this value",
    ]);
    // A fresh plain CLI imports the exported JSON without any UDA declarations.
    // Arguments, including JSON, remain separate argv values rather than shell code.
    let json = cli.task(&["export"]);
    let file = format!("{}/import.json", imported.data);
    successful(docker(&[
        "sh",
        "-c",
        "printf '%s' \"$1\" > \"$2\"",
        "fatto-private-import",
        &json,
        &file,
    ]));
    imported.task(&["import", &file]);
    let copy = imported.export();
    let copy = copy
        .iter()
        .find(|task| task["uuid"] == created.uuid)
        .unwrap();
    assert_eq!(copy["fatto_display_scheduled"], "v99:preserve-me");
    assert_eq!(copy["external_custom"], "retain this value");
    imported.task(&["sync"]);
    cli.task(&["sync"]);
    sync_phone(&phone, &client);
    let baseline = phone.get_task(created.uuid.clone()).unwrap().unwrap();
    assert_eq!(baseline.date_modes.due, LocalDateTime);
    assert_eq!(baseline.date_modes.scheduled, CalendarDate);
    assert_eq!(baseline.date_modes.wait, LocalDateTime);
    let mut unrelated = unchanged(baseline.uuid.clone(), baseline.status.clone());
    unrelated.description = StringEdit::Replace {
        expected: Some(baseline.description.clone()),
        value: Some("private unrelated phone edit".into()),
    };
    phone.update_task(unrelated).unwrap();
    let retained = phone.get_task(created.uuid.clone()).unwrap().unwrap();
    assert_eq!(retained.due, baseline.due);
    assert_eq!(retained.wait, baseline.wait);
    assert_eq!(retained.scheduled, baseline.scheduled);
    assert_eq!(
        property(&retained, "external_custom"),
        Some("retain this value")
    );
    assert_eq!(
        property(&retained, "fatto_display_scheduled"),
        Some("v99:preserve-me")
    );

    let mut edit = unchanged(baseline.uuid.clone(), baseline.status.clone());
    edit.due = StringEdit::Replace {
        expected: baseline.due.clone(),
        value: baseline.due.clone(),
    };
    edit.date_modes.due = DateModeEdit::Replace {
        expected: Some("v1:local-datetime".into()),
        value: CalendarDate,
    };
    edit.scheduled = StringEdit::Replace {
        expected: baseline.scheduled.clone(),
        value: Some("2030-01-03T14:30:45Z".into()),
    };
    edit.date_modes.scheduled = DateModeEdit::Check {
        expected: Some("v99:preserve-me".into()),
    };
    edit.wait = StringEdit::Replace {
        expected: baseline.wait.clone(),
        value: None,
    };
    edit.date_modes.wait = DateModeEdit::Replace {
        expected: Some("v1:local-datetime".into()),
        value: CalendarDate,
    };
    phone.update_task(edit).unwrap();
    sync_phone(&phone, &client);
    cli.task(&["sync"]);
    let exported = cli.export();
    let exported = exported
        .iter()
        .find(|task| task["uuid"] == created.uuid)
        .unwrap();
    assert_eq!(
        epoch(exported["due"].as_str().unwrap()),
        epoch(created.due.as_deref().unwrap())
    );
    assert!(exported.get("fatto_display_due").is_none());
    assert_eq!(exported["fatto_display_scheduled"], "v99:preserve-me");
    assert_eq!(
        epoch(exported["scheduled"].as_str().unwrap()),
        epoch("2030-01-03T14:30:45Z")
    );
    assert!(exported.get("wait").is_none());
    assert!(exported.get("fatto_display_wait").is_none());
    assert_eq!(exported["external_custom"], "retain this value");

    // Equal-instant opt-in must also survive the reverse CLI -> phone -> CLI path.
    let baseline = phone.get_task(created.uuid.clone()).unwrap().unwrap();
    let mut timed = unchanged(baseline.uuid.clone(), baseline.status.clone());
    timed.due = StringEdit::Replace {
        expected: baseline.due.clone(),
        value: baseline.due.clone(),
    };
    timed.date_modes.due = DateModeEdit::Replace {
        expected: None,
        value: LocalDateTime,
    };
    phone.update_task(timed).unwrap();
    sync_phone(&phone, &client);
    cli.task(&["sync"]);
    let exported = cli.export();
    let exported = exported
        .iter()
        .find(|task| task["uuid"] == created.uuid)
        .unwrap();
    assert_eq!(exported["fatto_display_due"], "v1:local-datetime");
    assert_eq!(
        epoch(exported["due"].as_str().unwrap()),
        epoch(created.due.as_deref().unwrap())
    );
    let baseline = phone.get_task(created.uuid.clone()).unwrap().unwrap();
    let mut clear = unchanged(baseline.uuid.clone(), baseline.status.clone());
    clear.due = StringEdit::Replace {
        expected: baseline.due.clone(),
        value: None,
    };
    clear.date_modes.due = DateModeEdit::Replace {
        expected: Some("v1:local-datetime".into()),
        value: CalendarDate,
    };
    clear.scheduled = StringEdit::Replace {
        expected: baseline.scheduled.clone(),
        value: None,
    };
    clear.date_modes.scheduled = DateModeEdit::Replace {
        expected: Some("v99:preserve-me".into()),
        value: CalendarDate,
    };
    phone.update_task(clear).unwrap();
    sync_phone(&phone, &client);
    cli.task(&["sync"]);
    let exported = cli.export();
    let exported = exported
        .iter()
        .find(|task| task["uuid"] == created.uuid)
        .unwrap();
    for field in ["due", "scheduled", "wait"] {
        assert!(exported.get(field).is_none());
        assert!(exported.get(format!("fatto_display_{field}")).is_none());
    }
    assert_eq!(exported["external_custom"], "retain this value");
}

#[test]
fn display_offline_cli_and_phone_edits_converge_without_cross_property_atomicity() {
    use taskchampion_android::{DateDisplayMode::*, DateModeEdit, TaskError};
    let client = uuid::Uuid::new_v4().to_string();
    let cli = Cli::new(client.clone());
    cli.marked(&[
        "rc.uda.external_custom.type:string",
        "add",
        "private concurrent display fixture",
        "due:2030-01-02T00:00:00Z",
        "fatto_display_wait:v99:retain",
        "external_custom:keep",
    ]);
    cli.task(&["sync"]);
    let dir_a = tempfile::tempdir().unwrap();
    let dir_b = tempfile::tempdir().unwrap();
    let a = ReplicaWrapper::new_on_disk(dir_a.path().to_string_lossy().into_owned()).unwrap();
    let b = ReplicaWrapper::new_on_disk(dir_b.path().to_string_lossy().into_owned()).unwrap();
    sync_phone(&a, &client);
    sync_phone(&b, &client);
    let original = a.all_task_data().unwrap().pop().unwrap();
    let mut phone_edit = unchanged(original.uuid.clone(), original.status.clone());
    phone_edit.due = StringEdit::Replace {
        expected: original.due.clone(),
        value: original.due.clone(),
    };
    phone_edit.date_modes.due = DateModeEdit::Replace {
        expected: None,
        value: LocalDateTime,
    };
    phone_edit.scheduled = StringEdit::Replace {
        expected: None,
        value: Some("2030-01-03T00:00:00Z".into()),
    };
    phone_edit.date_modes.scheduled = DateModeEdit::Replace {
        expected: None,
        value: LocalDateTime,
    };
    a.update_task(phone_edit).unwrap();
    // The CLI edits a timestamp of the SAME logical field while offline. Its
    // timestamp operation and the phone's marker operation merge independently.
    cli.task(&[
        &original.uuid,
        "modify",
        "due:2030-01-04T04:05:06Z",
        "description:private concurrent CLI title",
    ]);
    sync_phone(&a, &client);
    cli.task(&["sync"]);
    sync_phone(&a, &client);
    sync_phone(&b, &client);
    for phone in [&a, &b] {
        let merged = phone.get_task(original.uuid.clone()).unwrap().unwrap();
        assert_eq!(merged.description, "private concurrent CLI title");
        assert_eq!(
            epoch(merged.due.as_deref().unwrap()),
            epoch("2030-01-04T04:05:06Z")
        );
        assert_eq!(merged.date_modes.due, LocalDateTime);
        assert_eq!(
            epoch(merged.scheduled.as_deref().unwrap()),
            epoch("2030-01-03T00:00:00Z")
        );
        assert_eq!(merged.date_modes.scheduled, LocalDateTime);
        assert_eq!(property(&merged, "fatto_display_wait"), Some("v99:retain"));
        assert_eq!(property(&merged, "external_custom"), Some("keep"));
    }
    // Both replicas now edit the same timestamp offline. TaskChampion chooses a
    // property winner; require convergence and a valid retained candidate.
    for (phone, desired) in [(&a, "2030-01-06T10:11:12Z"), (&b, "2030-01-07T13:14:15Z")] {
        let baseline = phone.get_task(original.uuid.clone()).unwrap().unwrap();
        let mut edit = unchanged(baseline.uuid.clone(), baseline.status.clone());
        edit.due = StringEdit::Replace {
            expected: baseline.due,
            value: Some(desired.into()),
        };
        edit.date_modes.due = DateModeEdit::Check {
            expected: Some("v1:local-datetime".into()),
        };
        phone.update_task(edit).unwrap();
    }
    sync_phone(&a, &client);
    sync_phone(&b, &client);
    sync_phone(&a, &client);
    sync_phone(&b, &client);
    cli.task(&["sync"]);
    let merged_a = a.get_task(original.uuid.clone()).unwrap().unwrap();
    let merged_b = b.get_task(original.uuid.clone()).unwrap().unwrap();
    let winning_epoch = epoch(merged_a.due.as_deref().unwrap());
    assert!(
        [epoch("2030-01-06T10:11:12Z"), epoch("2030-01-07T13:14:15Z")].contains(&winning_epoch)
    );
    assert_eq!(merged_a.due, merged_b.due);
    assert_eq!(merged_a.date_modes.due, LocalDateTime);
    assert_eq!(merged_b.date_modes.due, LocalDateTime);
    let exported = cli.export();
    let exported = exported
        .iter()
        .find(|task| task["uuid"] == original.uuid)
        .unwrap();
    assert_eq!(epoch(exported["due"].as_str().unwrap()), winning_epoch);
    assert_eq!(exported["fatto_display_due"], "v1:local-datetime");
    assert_eq!(exported["external_custom"], "keep");
    // A stale draft cannot overwrite a marker removed via another client.
    cli.marked(&[&original.uuid, "modify", "fatto_display_due:"]);
    cli.task(&["sync"]);
    sync_phone(&b, &client);
    let mut stale = unchanged(merged_b.uuid.clone(), merged_b.status.clone());
    stale.due = StringEdit::Replace {
        expected: merged_b.due.clone(),
        value: Some("2030-01-08T01:02:03Z".into()),
    };
    stale.date_modes.due = DateModeEdit::Check {
        expected: Some("v1:local-datetime".into()),
    };
    assert!(
        matches!(b.update_task(stale), Err(TaskError::EditConflict { fields, .. }) if fields.contains(&"due".into()))
    );
    let unchanged = b.get_task(original.uuid.clone()).unwrap().unwrap();
    assert_eq!(unchanged.due, merged_b.due);
    assert_eq!(unchanged.date_modes.due, CalendarDate);
    assert_eq!(property(&unchanged, "external_custom"), Some("keep"));
}

#[test]
fn display_sync_keeps_unreadable_native_dates_until_explicit_repair_or_clear() {
    use taskchampion::storage::{sqlite::SqliteStorage, AccessMode};
    use taskchampion::{Operations, Replica, Status, Uuid};
    use taskchampion_android::{DateDisplayMode::*, DateModeEdit};
    let client = uuid::Uuid::new_v4().to_string();
    let dir_a = tempfile::tempdir().unwrap();
    let dir_b = tempfile::tempdir().unwrap();
    let id = Uuid::new_v4();
    // Model a pre-existing external replica with values outside native timestamp
    // encoding. No app-facing generic UDA mutation API is added for the fixture.
    tokio::runtime::Runtime::new().unwrap().block_on(async {
        let storage = SqliteStorage::new(dir_a.path(), AccessMode::ReadWrite, true)
            .await
            .unwrap();
        let mut replica = Replica::new(storage);
        let mut ops = Operations::new();
        let mut task = replica.create_task(id, &mut ops).await.unwrap();
        task.set_description("private unreadable date fixture".into(), &mut ops)
            .unwrap();
        task.set_status(Status::Pending, &mut ops).unwrap();
        for (key, value) in [
            ("due", "2030-01-02T00:00:45Z"),
            ("scheduled", "malformed-scheduled"),
            ("wait", "malformed-wait"),
            ("fatto_display_due", "v1:local-datetime"),
            ("fatto_display_scheduled", "v99:retain"),
            ("fatto_display_wait", "v1:local-datetime"),
            ("external_custom", "keep raw import"),
        ] {
            task.set_value(key, Some(value.into()), &mut ops).unwrap();
        }
        replica.commit_operations(ops).await.unwrap();
    });
    let a = ReplicaWrapper::new_on_disk(dir_a.path().to_string_lossy().into_owned()).unwrap();
    let b = ReplicaWrapper::new_on_disk(dir_b.path().to_string_lossy().into_owned()).unwrap();
    sync_phone(&a, &client);
    sync_phone(&b, &client);
    let original = b.get_task(id.to_string()).unwrap().unwrap();
    assert!(original.due.is_none() && original.scheduled.is_none() && original.wait.is_none());
    assert_eq!(original.date_modes.due, LocalDateTime);
    assert_eq!(original.date_modes.scheduled, CalendarDate);
    let raw = |task: &taskchampion_android::TaskData, key: &str| {
        task.native_dates
            .iter()
            .find(|pair| pair.key == key)
            .map(|pair| pair.value.clone())
    };
    assert_eq!(
        raw(&original, "due").as_deref(),
        Some("2030-01-02T00:00:45Z")
    );
    assert_eq!(
        raw(&original, "scheduled").as_deref(),
        Some("malformed-scheduled")
    );
    assert_eq!(raw(&original, "wait").as_deref(), Some("malformed-wait"));
    let mut unrelated = unchanged(original.uuid.clone(), original.status.clone());
    unrelated.description = StringEdit::Replace {
        expected: Some(original.description.clone()),
        value: Some("private unrelated raw-date edit".into()),
    };
    b.update_task(unrelated).unwrap();
    sync_phone(&b, &client);
    sync_phone(&a, &client);
    let retained = a.get_task(id.to_string()).unwrap().unwrap();
    assert!(retained.due.is_none() && retained.scheduled.is_none() && retained.wait.is_none());
    for field in ["due", "scheduled", "wait"] {
        assert_eq!(raw(&retained, field), raw(&original, field));
    }
    assert_eq!(
        property(&retained, "fatto_display_scheduled"),
        Some("v99:retain")
    );
    let mut repair = unchanged(retained.uuid.clone(), retained.status.clone());
    repair.due = StringEdit::Replace {
        expected: raw(&retained, "due"),
        value: Some("2030-01-02T00:00:45Z".into()),
    };
    repair.date_modes.due = DateModeEdit::Check {
        expected: Some("v1:local-datetime".into()),
    };
    repair.scheduled = StringEdit::Replace {
        expected: raw(&retained, "scheduled"),
        value: None,
    };
    repair.date_modes.scheduled = DateModeEdit::Replace {
        expected: Some("v99:retain".into()),
        value: CalendarDate,
    };
    repair.wait = StringEdit::Replace {
        expected: raw(&retained, "wait"),
        value: None,
    };
    repair.date_modes.wait = DateModeEdit::Replace {
        expected: Some("v1:local-datetime".into()),
        value: CalendarDate,
    };
    a.update_task(repair).unwrap();
    sync_phone(&a, &client);
    sync_phone(&b, &client);
    let repaired = b.get_task(id.to_string()).unwrap().unwrap();
    assert_eq!(
        epoch(repaired.due.as_deref().unwrap()),
        epoch("2030-01-02T00:00:45Z")
    );
    assert_eq!(repaired.date_modes.due, LocalDateTime);
    assert!(repaired.scheduled.is_none() && repaired.wait.is_none());
    assert!(raw(&repaired, "scheduled").is_none() && raw(&repaired, "wait").is_none());
    assert_eq!(
        property(&repaired, "external_custom"),
        Some("keep raw import")
    );
    let cli = Cli::new(client);
    cli.task(&["sync"]);
    let exported = cli.export();
    let exported = exported
        .iter()
        .find(|task| task["uuid"] == id.to_string())
        .unwrap();
    assert_eq!(
        epoch(exported["due"].as_str().unwrap()),
        epoch("2030-01-02T00:00:45Z")
    );
    assert_eq!(exported["fatto_display_due"], "v1:local-datetime");
    assert_eq!(exported["external_custom"], "keep raw import");
    assert!(exported.get("fatto_display_wait").is_none());
    assert!(exported.get("fatto_display_scheduled").is_none());
}
