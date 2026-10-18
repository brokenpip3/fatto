//! Live Taskwarrior interoperability coverage in the Rust integration suite.
use serde_json::Value;
use std::path::Path;
use std::process::{Command, Output};
use taskchampion_android::{ReplicaWrapper, TaskStatus};

fn docker(args: &[&str]) -> Output {
    Command::new("docker")
        .current_dir(Path::new(env!("CARGO_MANIFEST_DIR")).join("../.."))
        .args(["compose", "exec", "-T", "taskwarrior-client"])
        .args(args)
        .output()
        .expect("Docker Compose is required; start test services with just sync-up")
}

fn successful(output: Output) -> String {
    assert!(
        output.status.success(),
        "CLI command failed: {}\n{}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
    String::from_utf8(output.stdout).unwrap()
}

struct Cli {
    data: String,
    client_id: String,
}

impl Cli {
    fn new() -> Self {
        // Compose starts the container before its apk install has completed.
        successful(docker(&[
            "sh",
            "-c",
            "for i in $(seq 1 60); do command -v task >/dev/null && exit 0; sleep 1; done; exit 1",
        ]));
        let client_id = uuid::Uuid::new_v4().to_string();
        let cli = Self {
            data: format!("/tmp/fatto-recurrence-{client_id}"),
            client_id,
        };
        successful(docker(&["mkdir", "-p", &cli.data]));
        cli
    }

    fn task(&self, args: &[&str]) -> String {
        let data = format!("rc.data.location:{}", self.data);
        let client = format!("rc.sync.server.client_id:{}", self.client_id);
        let mut command = vec![
            "task",
            "rc:/dev/null",
            &data,
            &client,
            "rc.sync.server.url:http://tss:8080",
            "rc.sync.encryption_secret:recurrence-cli-test",
            "rc.confirmation:off",
            "rc.recurrence:off",
            "rc.verbose:nothing",
        ];
        command.extend_from_slice(args);
        successful(docker(&command))
    }

    fn export(&self) -> Vec<Value> {
        let mut tasks: Vec<Value> =
            serde_json::from_str(&self.task(&["export"])).expect("Taskwarrior JSON export");
        // Urgency is calculated at export time and drifts even without any edits.
        for task in &mut tasks {
            task.as_object_mut().unwrap().remove("urgency");
        }
        tasks
    }
}

impl Drop for Cli {
    fn drop(&mut self) {
        // Only this test's unique data directory; leave the seeded CLI untouched.
        let _ = docker(&["rm", "-rf", &self.data]);
    }
}

#[test]
fn bare_calendar_rules_roundtrip_between_taskwarrior_and_fatto() {
    let url =
        std::env::var("TASKCHAMPION_SYNC_URL").unwrap_or_else(|_| "http://localhost:8080".into());
    assert!(url.starts_with("http://localhost:") || url.starts_with("http://127.0.0.1:"));

    for interval in ["week", "day", "month", "year"] {
        eprintln!("CLI recurrence roundtrip: {interval}");
        let cli = Cli::new();
        cli.task(&[
            "add",
            "CLI recurrence regression",
            "due:today",
            &format!("recur:{interval}"),
        ]);
        cli.task(&["rc.recurrence:on", "rc.recurrence.limit:2", "next"]);
        let original = cli.export();
        let parent = original
            .iter()
            .find(|t| t["status"] == "recurring")
            .unwrap();
        assert_eq!(parent["recur"], interval);
        assert_eq!(parent["mask"], "---");
        assert_eq!(original.len(), 4, "one template and three CLI occurrences");
        let parent_id = parent["uuid"].as_str().unwrap();
        let child = original
            .iter()
            .find(|t| t["parent"] == parent_id && t["imask"] == 0)
            .unwrap();
        let child_id = child["uuid"].as_str().unwrap();
        assert_eq!(child["status"], "pending");
        cli.task(&["sync"]);

        let dir = tempfile::tempdir().unwrap();
        let phone = ReplicaWrapper::new_on_disk(dir.path().to_string_lossy().into_owned()).unwrap();
        let sync = || {
            phone
                .sync(
                    url.clone(),
                    cli.client_id.clone(),
                    "recurrence-cli-test".into(),
                )
                .unwrap()
        };
        sync();
        let diagnostics = phone.recurrence_diagnostics().unwrap();
        assert!(diagnostics.is_empty(), "{interval}: {diagnostics:?}");
        assert_eq!(phone.all_task_data().unwrap().len(), original.len());
        phone
            .update_task_status(child_id.into(), TaskStatus::Completed)
            .unwrap();
        sync();
        cli.task(&["sync"]);

        let received = cli.export();
        assert_eq!(
            received.len(),
            original.len(),
            "Fatto must not generate tasks"
        );
        let completed = received.iter().find(|t| t["uuid"] == child_id).unwrap();
        assert_eq!(completed["status"], "completed");
        let updated = received.iter().find(|t| t["uuid"] == parent_id).unwrap();
        assert_eq!(updated["recur"], interval);
        assert_eq!(updated["mask"], "+--");
        for before in &original {
            let after = received
                .iter()
                .find(|t| t["uuid"] == before["uuid"])
                .unwrap();
            for key in ["recur", "parent", "imask", "due"] {
                assert_eq!(after[key], before[key], "{interval}: {key}");
            }
        }
        // A second round trip must be idempotent, including native mask metadata.
        sync();
        cli.task(&["sync"]);
        assert_eq!(cli.export(), received);
        assert!(phone.recurrence_diagnostics().unwrap().is_empty());
    }
}
