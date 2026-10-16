//! Native Taskwarrior recurrence metadata. This module never generates tasks.
use crate::{DynStorage, Result, TaskStatus};
use chrono::{DateTime, TimeZone, Utc};
use std::collections::{BTreeMap, HashMap};
use taskchampion::{Operations, Replica, Status, Task, Uuid};

pub const MAX_REPAIR_MASK_LENGTH: usize = 1_000_000;

#[derive(uniffi::Record, Debug, Clone)]
pub struct RecurrenceData {
    pub recur: Option<String>,
    pub rtype: Option<String>,
    pub parent: Option<String>,
    pub imask: Option<String>,
    pub mask: Option<String>,
    pub until_raw: Option<String>,
    pub until: Option<String>,
    pub last: Option<String>,
    pub template: Option<String>,
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct RecurrenceDiagnostic {
    pub task_uuid: String,
    pub code: String,
    pub message: String,
}

pub fn timestamp(task: &Task, key: &str) -> Option<DateTime<Utc>> {
    task.get_value(key)?
        .parse::<i64>()
        .ok()
        .and_then(|s| Utc.timestamp_opt(s, 0).single())
}

pub fn read_recurrence(task: &Task) -> Option<RecurrenceData> {
    let raw = |key| task.get_value(key).map(str::to_owned);
    if task.get_status() != Status::Recurring
        && [
            "recur", "rtype", "parent", "imask", "mask", "until", "last", "template",
        ]
        .iter()
        .all(|key| task.get_value(*key).is_none())
    {
        return None;
    }
    Some(RecurrenceData {
        recur: raw("recur"),
        rtype: raw("rtype"),
        parent: raw("parent"),
        imask: raw("imask"),
        mask: raw("mask"),
        until_raw: raw("until"),
        until: timestamp(task, "until").map(|d| d.to_rfc3339()),
        last: raw("last"),
        template: raw("template"),
    })
}

// A conservative inspection boundary, not a CLI duration parser. Unrecognized
// imported intervals remain readable and preserved, but their masks are not repaired.
fn valid_interval(raw: &str) -> bool {
    if matches!(
        raw,
        "daily"
            | "weekly"
            | "biweekly"
            | "fortnight"
            | "monthly"
            | "bimonthly"
            | "quarterly"
            | "semiannual"
            | "annual"
            | "yearly"
            | "biannual"
            | "biyearly"
            | "weekdays"
    ) {
        return true;
    }
    let split = raw.find(|c: char| !c.is_ascii_digit()).unwrap_or(raw.len());
    if split == 0 {
        return matches!(raw, "P1D" | "P1W" | "P1M" | "P1Y" | "P3M");
    }
    raw[..split]
        .parse::<u32>()
        .is_ok_and(|n| n > 0 && n <= i32::MAX as u32)
        && matches!(
            &raw[split..],
            "d" | "day"
                | "days"
                | "w"
                | "week"
                | "weeks"
                | "m"
                | "month"
                | "months"
                | "q"
                | "y"
                | "year"
                | "years"
        )
}

pub fn is_template(task: &Task) -> bool {
    task.get_status() == Status::Recurring
        || (task.get_value("parent").is_none()
            && task.get_value("imask").is_none()
            && (task.get_value("recur").is_some() || task.get_value("mask").is_some()))
}

fn supported_parent(task: &Task) -> bool {
    task.get_status() == Status::Recurring
        && task.get_value("rtype").is_none_or(|v| v == "periodic")
        && task.get_value("last").is_none()
        && task.get_value("template").is_none()
        && task.get_value("parent").is_none()
        && task.get_value("imask").is_none()
        && task.get_value("recur").is_some_and(valid_interval)
        && timestamp(task, "due").is_some()
        && (task.get_value("until").is_none() || timestamp(task, "until").is_some())
}

fn valid_mask(mask: &str) -> bool {
    mask.len() <= MAX_REPAIR_MASK_LENGTH
        && mask
            .bytes()
            .all(|c| matches!(c, b'-' | b'+' | b'X' | b'W' | b'?'))
}

fn index(raw: &str) -> Option<usize> {
    if raw.is_empty() || !raw.bytes().all(|c| c.is_ascii_digit()) {
        return None;
    }
    raw.parse::<usize>()
        .ok()
        .filter(|i| *i < MAX_REPAIR_MASK_LENGTH)
}

pub fn mask_character(
    status: &TaskStatus,
    wait: Option<DateTime<Utc>>,
    now: DateTime<Utc>,
) -> Option<u8> {
    match status {
        TaskStatus::Pending => Some(if wait.is_some_and(|w| w > now) {
            b'W'
        } else {
            b'-'
        }),
        TaskStatus::Completed => Some(b'+'),
        TaskStatus::Deleted => Some(b'X'),
        _ => None,
    }
}

fn diagnostic(out: &mut Vec<RecurrenceDiagnostic>, task: &Task, code: &str, message: &str) {
    out.push(RecurrenceDiagnostic {
        task_uuid: task.get_uuid().to_string(),
        code: code.into(),
        message: message.into(),
    });
}

/// Returns conservative repairs for existing slots only. Missing records, duplicate
/// indices and malformed relationships remain untouched; no mask length changes.
fn inspect(
    tasks: &HashMap<Uuid, Task>,
    now: DateTime<Utc>,
) -> (Vec<RecurrenceDiagnostic>, BTreeMap<Uuid, String>) {
    let mut diagnostics = Vec::new();
    let mut slots: BTreeMap<(Uuid, usize), Vec<&Task>> = BTreeMap::new();
    for task in tasks.values() {
        let Some(meta) = read_recurrence(task) else {
            continue;
        };
        if meta.until_raw.is_some() && meta.until.is_none() {
            diagnostic(
                &mut diagnostics,
                task,
                "invalid_until",
                "Until is not a representable timestamp; its raw value is preserved.",
            );
        }
        if meta.recur.as_deref().is_some_and(|v| !valid_interval(v))
            || (task.get_status() == Status::Recurring && meta.recur.is_none())
        {
            diagnostic(
                &mut diagnostics,
                task,
                "invalid_interval",
                "The imported repeat interval cannot be safely interpreted; it is preserved.",
            );
        }
        if meta.last.is_some()
            || meta.template.is_some()
            || meta.rtype.as_deref().is_some_and(|v| v != "periodic")
        {
            diagnostic(
                &mut diagnostics,
                task,
                "unsupported_type",
                "This recurrence layout is unsupported; no series metadata will be changed.",
            );
        }
        if task.get_status() == Status::Recurring && timestamp(task, "due").is_none() {
            diagnostic(
                &mut diagnostics,
                task,
                "unsupported_type",
                "The series has no readable Due date.",
            );
        }
        if meta.mask.as_deref().is_some_and(|m| !valid_mask(m)) {
            diagnostic(
                &mut diagnostics,
                task,
                "invalid_mask",
                "The recurrence mask is invalid or exceeds the repair limit.",
            );
        }
        if matches!(task.get_status(), Status::Unknown(_)) {
            diagnostic(
                &mut diagnostics,
                task,
                "unsupported_type",
                "The imported occurrence status is unknown; its mask position is preserved.",
            );
        }
        if (meta.parent.is_some() || meta.imask.is_some())
            && task.get_value("wait").is_some()
            && timestamp(task, "wait").is_none()
        {
            diagnostic(
                &mut diagnostics,
                task,
                "unsupported_type",
                "The occurrence Wait date is unreadable; its mask position is preserved.",
            );
        }
        if meta.parent.is_none() && meta.imask.is_none() {
            continue;
        }
        let Some(parent) = meta.parent.as_deref().and_then(|p| Uuid::parse_str(p).ok()) else {
            diagnostic(
                &mut diagnostics,
                task,
                "invalid_parent",
                "The occurrence has no valid parent UUID.",
            );
            continue;
        };
        let Some(i) = meta.imask.as_deref().and_then(index) else {
            diagnostic(
                &mut diagnostics,
                task,
                "invalid_index",
                "The occurrence has no valid mask index.",
            );
            continue;
        };
        slots.entry((parent, i)).or_default().push(task);
    }
    // A mask is a generated prefix even when its children have been purged.
    // Report missing records without filling gaps or changing the prefix.
    for parent in tasks
        .values()
        .filter(|t| t.get_status() == Status::Recurring)
    {
        if let Some(mask) = parent.get_value("mask").filter(|m| valid_mask(m)) {
            let mut missing = 0usize;
            let mut first = None;
            for i in 0..mask.len() {
                if !slots.contains_key(&(parent.get_uuid(), i)) {
                    missing += 1;
                    first.get_or_insert(i);
                }
            }
            if let Some(first) = first {
                diagnostic(&mut diagnostics, parent, "missing_occurrence", &format!(
                    "{missing} stored recurrence slots have no matching occurrence (first index {first}); their mask positions are preserved."
                ));
            }
        }
    }
    let mut repairs: BTreeMap<Uuid, String> = BTreeMap::new();
    for ((parent_uuid, i), children) in slots {
        if children.len() != 1 {
            for task in children {
                diagnostic(&mut diagnostics, task, "duplicate_index", "Multiple occurrences share this series index; no automatic deduplication is performed.");
            }
            continue;
        }
        let child = children[0];
        let Some(parent) = tasks.get(&parent_uuid) else {
            diagnostic(
                &mut diagnostics,
                child,
                "missing_parent",
                "The series template is missing; the occurrence can still be edited.",
            );
            continue;
        };
        if !supported_parent(parent) {
            diagnostic(
                &mut diagnostics,
                child,
                "unsupported_type",
                "The parent is retired or unsupported; its mask is preserved.",
            );
            continue;
        }
        let Some(mask) = parent.get_value("mask").filter(|m| valid_mask(m)) else {
            diagnostic(
                &mut diagnostics,
                child,
                "invalid_mask",
                "The parent has no valid mask; no prefix is invented.",
            );
            continue;
        };
        if i >= mask.len() {
            diagnostic(&mut diagnostics, child, "mask_index_out_of_range", "The occurrence is outside the existing mask; extending it could suppress CLI generation.");
            continue;
        }
        if child.get_value("rtype").is_some_and(|v| v != "periodic")
            || child.get_value("last").is_some()
            || child.get_value("template").is_some()
            || (child.get_value("wait").is_some() && timestamp(child, "wait").is_none())
        {
            continue;
        }
        let Some(c) = mask_character(&child.get_status().into(), timestamp(child, "wait"), now)
        else {
            continue;
        };
        if mask.as_bytes()[i] != c {
            diagnostic(&mut diagnostics, child, "reconciliation_pending", "The existing series mask differs from this occurrence; it will be reconciled on sync.");
            let value = repairs
                .entry(parent_uuid)
                .or_insert_with(|| mask.to_owned());
            let mut bytes = value.as_bytes().to_vec();
            bytes[i] = c;
            *value = String::from_utf8(bytes).expect("validated ASCII mask");
        }
    }
    diagnostics.sort_by(|a, b| (&a.task_uuid, &a.code).cmp(&(&b.task_uuid, &b.code)));
    (diagnostics, repairs)
}

pub fn diagnostics(tasks: &HashMap<Uuid, Task>, now: DateTime<Utc>) -> Vec<RecurrenceDiagnostic> {
    inspect(tasks, now).0
}

pub fn append_repairs(
    tasks: &HashMap<Uuid, Task>,
    now: DateTime<Utc>,
    ops: &mut Operations,
) -> Result<bool> {
    let repairs = inspect(tasks, now).1;
    let changed = !repairs.is_empty();
    for (uuid, mask) in repairs {
        let mut parent = tasks[&uuid].clone();
        parent.set_value("mask", Some(mask), ops)?;
    }
    Ok(changed)
}

pub async fn reconcile_masks(
    replica: &mut Replica<DynStorage>,
    now: DateTime<Utc>,
) -> Result<bool> {
    let tasks = replica.all_tasks().await?;
    let mut ops = Operations::new();
    let changed = append_repairs(&tasks, now, &mut ops)?;
    if changed {
        replica.commit_operations(ops).await?;
    }
    Ok(changed)
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct RecurrenceRule {
    pub interval: String,
    pub until: Option<String>,
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct RecurrenceRulePatch {
    pub interval: crate::StringEdit,
    pub until: crate::StringEdit,
}

pub fn creation_interval(raw: &str) -> bool {
    if matches!(raw, "daily" | "weekdays" | "weekly" | "monthly" | "yearly") {
        return true;
    }
    let split = raw.find(|c: char| !c.is_ascii_digit()).unwrap_or(raw.len());
    split > 0
        && raw[..split]
            .parse::<u32>()
            .is_ok_and(|n| n > 0 && n <= i32::MAX as u32)
        && matches!(&raw[split..], "days" | "weeks" | "months" | "years")
}

pub fn template_source(task: &Task) -> Result<()> {
    if task.get_status() != Status::Recurring
        || task.get_value("rtype").is_some_and(|v| v != "periodic")
        || ["last", "template", "parent", "imask"]
            .iter()
            .any(|k| task.get_value(*k).is_some())
        || task.get_value("recur").is_none()
    {
        return Err(crate::TaskError::Internal(
            "This is not an active supported periodic series".into(),
        ));
    }
    Ok(())
}

pub fn validate_schedule(task: &Task) -> Result<()> {
    let due = timestamp(task, "due").ok_or_else(|| {
        crate::TaskError::Internal("A recurring series requires a readable Due date".into())
    })?;
    if task.get_value("recur").is_none_or(str::is_empty) {
        return Err(crate::TaskError::Internal(
            "A recurring series requires a Repeat interval".into(),
        ));
    }
    for key in ["wait", "scheduled"] {
        if task.get_value(key).is_some() && timestamp(task, key).is_none() {
            return Err(crate::TaskError::Internal(format!(
                "Correct or clear the unreadable {key} date before changing the schedule"
            )));
        }
    }
    if task.get_value("until").is_some() {
        let until = timestamp(task, "until").ok_or_else(|| {
            crate::TaskError::Internal(
                "Correct or clear the unreadable Until date before changing the schedule".into(),
            )
        })?;
        if until < due {
            return Err(crate::TaskError::Internal(
                "Until cannot be earlier than Due".into(),
            ));
        }
    }
    Ok(())
}

pub fn apply_rule_patch(
    task: &mut Task,
    patch: RecurrenceRulePatch,
    ops: &mut Operations,
) -> Result<()> {
    let mut conflicts = Vec::new();
    for (key, edit) in [("recur", &patch.interval), ("until", &patch.until)] {
        if let crate::StringEdit::Replace { expected, .. } = edit {
            if task.get_value(key) != expected.as_deref() {
                conflicts.push(key.to_owned());
            }
        }
    }
    if !conflicts.is_empty() {
        return Err(crate::TaskError::EditConflict {
            uuid: task.get_uuid().to_string(),
            fields: conflicts,
        });
    }
    for (key, edit) in [("recur", patch.interval), ("until", patch.until)] {
        if let crate::StringEdit::Replace { value, .. } = edit {
            let desired = if key == "recur" {
                if !value.as_deref().is_some_and(creation_interval) {
                    return Err(crate::TaskError::Internal("Use a supported repeat interval or a positive number of days, weeks, months or years".into()));
                }
                value
            } else {
                crate::parse_rfc3339(value)?.map(|d| d.timestamp().to_string())
            };
            if task.get_value(key) != desired.as_deref() {
                task.set_value(key, desired, ops)?;
            }
        }
    }
    Ok(())
}
