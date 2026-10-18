//! Explicit field presentation. Unmarked dates retain the historical calendar day.
use crate::{parse_rfc3339, recurrence, Result, StringEdit, TaskEditPatch, TaskError};
use taskchampion::{Operations, Task};

const TIMED: &str = "v1:local-datetime";

#[derive(uniffi::Enum, Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum DateDisplayMode {
    #[default]
    CalendarDate,
    LocalDateTime,
}

#[derive(uniffi::Record, Debug, Clone, Default)]
pub struct DateDisplayModes {
    pub due: DateDisplayMode,
    pub scheduled: DateDisplayMode,
    pub wait: DateDisplayMode,
}

#[derive(uniffi::Enum, Debug, Clone, Default)]
pub enum DateModeEdit {
    #[default]
    Unchanged,
    Check {
        expected: Option<String>,
    },
    Replace {
        expected: Option<String>,
        value: DateDisplayMode,
    },
}

#[derive(uniffi::Record, Debug, Clone, Default)]
pub struct DateDisplayModesPatch {
    pub due: DateModeEdit,
    pub scheduled: DateModeEdit,
    pub wait: DateModeEdit,
}

impl DateDisplayModesPatch {
    pub(crate) fn has_changes(&self) -> bool {
        [&self.due, &self.scheduled, &self.wait]
            .iter()
            .any(|edit| matches!(edit, DateModeEdit::Replace { .. }))
    }
}

const FIELDS: [(&str, &str); 3] = [
    ("due", "fatto_display_due"),
    ("scheduled", "fatto_display_scheduled"),
    ("wait", "fatto_display_wait"),
];

fn mode(task: &Task, field: usize) -> DateDisplayMode {
    let (_, key) = FIELDS[field];
    if task.get_value(key) == Some(TIMED) {
        DateDisplayMode::LocalDateTime
    } else {
        DateDisplayMode::CalendarDate
    }
}

pub(crate) fn read(task: &Task) -> DateDisplayModes {
    DateDisplayModes {
        due: mode(task, 0),
        scheduled: mode(task, 1),
        wait: mode(task, 2),
    }
}

pub(crate) fn initialize(
    task: &mut Task,
    modes: DateDisplayModes,
    ops: &mut Operations,
) -> Result<()> {
    for (field, key, value) in [
        ("due", "fatto_display_due", modes.due),
        ("scheduled", "fatto_display_scheduled", modes.scheduled),
        ("wait", "fatto_display_wait", modes.wait),
    ] {
        if value == DateDisplayMode::LocalDateTime {
            if recurrence::timestamp(task, field).is_none() {
                return Err(TaskError::Internal(format!(
                    "A timed {field} needs a valid date"
                )));
            }
            task.set_value(key, Some(TIMED.into()), ops)?;
        }
    }
    Ok(())
}

/// Validate before applying any edits. Check never rewrites an unknown marker.
pub(crate) fn validate(
    task: &Task,
    patch: &TaskEditPatch,
    allow_template_dates: bool,
    conflicts: &mut Vec<String>,
) -> Result<Vec<(&'static str, Option<String>)>> {
    let mut changes = Vec::new();
    for ((field, key), (date, edit)) in FIELDS.into_iter().zip([
        (&patch.due, &patch.date_modes.due),
        (&patch.scheduled, &patch.date_modes.scheduled),
        (&patch.wait, &patch.date_modes.wait),
    ]) {
        let current = task.get_value(key).map(str::to_owned);
        let has_marker = current.is_some();
        let expected = match edit {
            DateModeEdit::Unchanged => {
                if matches!(date, StringEdit::Replace { value: None, .. }) && has_marker {
                    return Err(TaskError::Internal(format!(
                        "Clearing {field} needs its display baseline"
                    )));
                }
                continue;
            }
            DateModeEdit::Check { expected } | DateModeEdit::Replace { expected, .. } => expected,
        };
        if current != *expected {
            conflicts.push(field.into());
        }
        if let DateModeEdit::Replace { value, .. } = edit {
            if recurrence::is_template(task) && !allow_template_dates {
                return Err(TaskError::Internal(
                    "Use the series editor to change date display".into(),
                ));
            }
            let StringEdit::Replace {
                value: desired_date,
                ..
            } = date
            else {
                return Err(TaskError::Internal(format!(
                    "Changing {field} display needs its date baseline"
                )));
            };
            if *value == DateDisplayMode::LocalDateTime
                && parse_rfc3339(desired_date.clone())?.is_none()
            {
                return Err(TaskError::Internal(format!(
                    "A timed {field} needs a valid date"
                )));
            }
            let desired = (*value == DateDisplayMode::LocalDateTime).then(|| TIMED.to_owned());
            if current != desired {
                changes.push((key, desired));
            }
        } else if matches!(date, StringEdit::Replace { value: None, .. }) && has_marker {
            return Err(TaskError::Internal(format!(
                "Clearing {field} must clear its display mode"
            )));
        }
    }
    Ok(changes)
}
