#[path = "support/recurrence_fixtures.rs"]
#[allow(dead_code)]
mod support;
use support::*;
use taskchampion_android::{ReplicaWrapper, TaskStatus};

fn matrix(sync: impl Fn(&ReplicaWrapper)) {
    let first = tempfile::tempdir().unwrap();
    let second = tempfile::tempdir().unwrap();
    seed(first.path(), &series("--"));
    let a = wrapper(first.path());
    let b = wrapper(second.path());
    sync(&a);
    sync(&b);
    a.transition_occurrence(CHILD0.into(), TaskStatus::Completed, true)
        .unwrap();
    b.transition_occurrence(CHILD1.into(), TaskStatus::Completed, true)
        .unwrap();
    assert_eq!(properties(first.path(), PARENT)["mask"], "+-");
    assert_eq!(properties(second.path(), PARENT)["mask"], "-+");
    for _ in 0..3 {
        sync(&a);
        sync(&b);
    }
    for (r, path) in [(&a, first.path()), (&b, second.path())] {
        assert_eq!(
            r.get_task(CHILD0.into()).unwrap().unwrap().status,
            TaskStatus::Completed
        );
        assert_eq!(
            r.get_task(CHILD1.into()).unwrap().unwrap().status,
            TaskStatus::Completed
        );
        assert_eq!(properties(path, PARENT)["mask"], "++");
        assert_eq!(r.all_task_data().unwrap().len(), 3);
        assert_eq!(properties(path, CHILD0)["custom"], "child UDA");
        assert_eq!(
            properties(path, CHILD0)["annotation_1700000000"],
            "keep this note"
        );
        assert!(r.recurrence_diagnostics().unwrap().is_empty());
    }
    a.update_task_status(CHILD0.into(), TaskStatus::Deleted)
        .unwrap();
    sync(&a);
    sync(&b);
    assert_eq!(properties(second.path(), PARENT)["mask"], "X+");
    b.update_task_status(CHILD0.into(), TaskStatus::Pending)
        .unwrap();
    sync(&b);
    sync(&a);
    assert_eq!(properties(first.path(), PARENT)["mask"], "-+");
    // Network failure never rolls back a committed offline mutation.
    a.update_task_status(CHILD1.into(), TaskStatus::Deleted)
        .unwrap();
    assert!(a
        .sync(
            "http://127.0.0.1:1".into(),
            uuid::Uuid::new_v4().to_string(),
            "private-test".into()
        )
        .is_err());
    assert_eq!(properties(first.path(), PARENT)["mask"], "-X");
    sync(&a);
    sync(&b);
    assert_eq!(properties(second.path(), PARENT)["mask"], "-X");

    // Conflicting offline terminal changes to the same child must converge to
    // TaskChampion's winning status and a mask derived from that status.
    a.transition_occurrence(CHILD0.into(), TaskStatus::Pending, false)
        .unwrap();
    sync(&a);
    sync(&b);
    a.transition_occurrence(CHILD0.into(), TaskStatus::Completed, false)
        .unwrap();
    b.transition_occurrence(CHILD0.into(), TaskStatus::Deleted, false)
        .unwrap();
    for _ in 0..3 {
        sync(&a);
        sync(&b);
    }
    let winner = a.get_task(CHILD0.into()).unwrap().unwrap().status;
    assert_eq!(winner, b.get_task(CHILD0.into()).unwrap().unwrap().status);
    let symbol = match winner {
        TaskStatus::Completed => '+',
        TaskStatus::Deleted => 'X',
        _ => panic!("Unexpected conflict winner"),
    };
    for path in [first.path(), second.path()] {
        assert_eq!(
            properties(path, PARENT)["mask"].chars().next(),
            Some(symbol)
        );
    }
    a.transition_occurrence(CHILD0.into(), TaskStatus::Deleted, false)
        .unwrap();
    sync(&a);
    sync(&b);
    a.transition_occurrence(CHILD0.into(), TaskStatus::Pending, false)
        .unwrap();
    b.transition_occurrence(CHILD0.into(), TaskStatus::Completed, false)
        .unwrap();
    for _ in 0..3 {
        sync(&a);
        sync(&b);
    }
    let winner = a.get_task(CHILD0.into()).unwrap().unwrap().status;
    assert_eq!(winner, b.get_task(CHILD0.into()).unwrap().unwrap().status);
    let symbol = match winner {
        TaskStatus::Completed => '+',
        TaskStatus::Pending => '-',
        _ => panic!("Unexpected restore conflict winner"),
    };
    for path in [first.path(), second.path()] {
        assert_eq!(
            properties(path, PARENT)["mask"].chars().next(),
            Some(symbol)
        );
    }

    // A primary CLI may grow its generation prefix while a phone has an older
    // mask offline. Neither repair nor conflict resolution may discard children
    // or invent slots when the winning native mask is shorter.
    a.transition_occurrence(CHILD0.into(), TaskStatus::Pending, false)
        .unwrap();
    a.transition_occurrence(CHILD1.into(), TaskStatus::Pending, false)
        .unwrap();
    sync(&a);
    sync(&b);
    a.transition_occurrence(CHILD0.into(), TaskStatus::Completed, false)
        .unwrap();
    const THIRD: &str = "10000000-0000-4000-8000-000000000004";
    seed(
        second.path(),
        &[
            (PARENT, vec![("mask", "---")]),
            (
                THIRD,
                vec![
                    ("description", "CLI generation control"),
                    ("status", "pending"),
                    ("parent", PARENT),
                    ("imask", "2"),
                    ("recur", "daily"),
                    ("rtype", "periodic"),
                    ("due", "1893628800"),
                ],
            ),
        ],
    );
    for _ in 0..3 {
        sync(&a);
        sync(&b);
    }
    let mask = properties(first.path(), PARENT)["mask"].clone();
    assert_eq!(mask, properties(second.path(), PARENT)["mask"]);
    assert!([2, 3].contains(&mask.len()));
    for r in [&a, &b] {
        assert_eq!(r.all_task_data().unwrap().len(), 4);
        assert_eq!(
            r.get_task(THIRD.into())
                .unwrap()
                .unwrap()
                .recurrence
                .unwrap()
                .parent
                .as_deref(),
            Some(PARENT)
        );
        if mask.len() == 2 {
            assert!(r
                .recurrence_diagnostics()
                .unwrap()
                .iter()
                .any(|d| d.task_uuid == THIRD && d.code == "mask_index_out_of_range"));
        }
    }
    sync(&a);
    sync(&b);
    assert_eq!(properties(first.path(), PARENT)["mask"].len(), mask.len());
    assert_eq!(properties(second.path(), PARENT)["mask"].len(), mask.len());

    // Retirement and an independent offline child completion commute: no
    // occurrence completion may resurrect the parent or delete another child.
    a.stop_recurrence(PARENT.into()).unwrap();
    b.transition_occurrence(THIRD.into(), TaskStatus::Completed, false)
        .unwrap();
    for _ in 0..3 {
        sync(&a);
        sync(&b);
    }
    for r in [&a, &b] {
        assert_eq!(
            r.get_task(PARENT.into()).unwrap().unwrap().status,
            TaskStatus::Deleted
        );
        assert_eq!(
            r.get_task(THIRD.into()).unwrap().unwrap().status,
            TaskStatus::Completed
        );
        assert_eq!(
            r.get_task(CHILD1.into()).unwrap().unwrap().status,
            TaskStatus::Pending
        );
        assert_eq!(r.all_task_data().unwrap().len(), 4);
    }
}

#[test]
#[ignore = "requires explicit isolated local HTTP test endpoint"]
fn independent_offline_completions_converge_over_http() {
    let url = std::env::var("TASKCHAMPION_SYNC_URL").expect("explicit test URL");
    assert!(
        url.starts_with("http://127.0.0.1:") || url.starts_with("http://localhost:"),
        "local test endpoint only"
    );
    let id = uuid::Uuid::new_v4().to_string();
    matrix(|r| {
        r.sync(url.clone(), id.clone(), "recurrence-http-test".into())
            .unwrap()
    });
}

#[test]
#[ignore = "requires explicit dedicated S3 test bucket"]
fn independent_offline_completions_converge_over_s3() {
    let endpoint = std::env::var("TASKCHAMPION_S3_ENDPOINT").expect("explicit endpoint");
    let bucket = std::env::var("TASKCHAMPION_S3_BUCKET").expect("dedicated bucket");
    assert!(
        bucket.starts_with("fatto-recurrence-"),
        "private recurrence test bucket only"
    );
    let key = std::env::var("TASKCHAMPION_S3_ACCESS_KEY_ID").expect("test access key");
    let secret = std::env::var("TASKCHAMPION_S3_SECRET_ACCESS_KEY").expect("test secret");
    matrix(|r| {
        r.sync_aws(
            bucket.clone(),
            Some("us-east-1".into()),
            Some(endpoint.clone()),
            key.clone(),
            secret.clone(),
            "recurrence-s3-test".into(),
        )
        .unwrap()
    });
}
