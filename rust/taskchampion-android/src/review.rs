use super::*;

fn ensure_reviewable(task: &Task) -> Result<()> {
    if task.get_status() != taskchampion::Status::Pending || recurrence::is_template(task) {
        return Err(TaskError::Internal(
            "Only pending tasks can be reviewed".into(),
        ));
    }
    if let Some(raw) = task.get_value("reviewed").filter(|raw| !raw.is_empty()) {
        let valid = raw
            .parse::<i64>()
            .ok()
            .and_then(|seconds| DateTime::from_timestamp(seconds, 0))
            .is_some()
            || DateTime::parse_from_rfc3339(raw).is_ok()
            || chrono::NaiveDateTime::parse_from_str(raw, "%Y%m%dT%H%M%SZ").is_ok();
        if !valid {
            return Err(TaskError::Internal(
                "Invalid reviewed date; the existing value was preserved".into(),
            ));
        }
    }
    Ok(())
}

fn stamp(task: &mut Task, now: DateTime<Utc>, ops: &mut Operations) -> Result<()> {
    task.set_value("reviewed", Some(now.timestamp().to_string()), ops)?;
    task.set_modified(now, ops)?;
    Ok(())
}

fn with_review_task<T>(
    wrapper: &ReplicaWrapper,
    uuid: String,
    change: impl FnOnce(&mut Task, &mut Operations) -> Result<T>,
) -> Result<T> {
    let id = Uuid::parse_str(&uuid).map_err(|_| TaskError::Internal("Invalid UUID".into()))?;
    let mut replica = wrapper.inner.lock().unwrap();
    let mut tasks = wrapper.rt.block_on(replica.all_tasks())?;
    let task = tasks
        .get_mut(&id)
        .ok_or_else(|| TaskError::Internal("Task not found".into()))?;
    ensure_reviewable(task)?;
    let mut ops = Operations::new();
    let result = change(task, &mut ops)?;
    if !ops.is_empty() {
        let now = Utc::now();
        stamp(task, now, &mut ops)?;
        recurrence::append_repairs(&tasks, now, &mut ops)?;
        wrapper.rt.block_on(replica.commit_operations(ops))?;
    }
    Ok(result)
}

#[uniffi::export]
impl ReplicaWrapper {
    pub fn mark_reviewed(&self, uuid: String) -> Result<()> {
        with_review_task(self, uuid, |task, ops| {
            task.set_value("reviewed", Some(Utc::now().timestamp().to_string()), ops)?;
            Ok(())
        })
    }

    pub fn update_task_for_review(&self, patch: TaskEditPatch) -> Result<()> {
        with_review_task(self, patch.uuid.clone(), |task, ops| {
            apply_edit_patch(task, patch, false, ops)
        })
    }

    /// A reverse relationship may edit another task, but only the task being reviewed is stamped.
    pub fn update_dependencies_for_review(
        &self,
        reviewed_uuid: String,
        patch: TaskEditPatch,
    ) -> Result<()> {
        if [
            &patch.description,
            &patch.project,
            &patch.priority,
            &patch.due,
            &patch.wait,
            &patch.scheduled,
            &patch.start,
        ]
        .iter()
        .any(|field| !matches!(field, StringEdit::Unchanged))
            || !matches!(patch.tags, StringSetEdit::Unchanged)
        {
            return Err(TaskError::Internal(
                "Only dependencies may change in this review operation".into(),
            ));
        }
        let reviewed_id = Uuid::parse_str(&reviewed_uuid)
            .map_err(|_| TaskError::Internal("Invalid UUID".into()))?;
        let target_id =
            Uuid::parse_str(&patch.uuid).map_err(|_| TaskError::Internal("Invalid UUID".into()))?;
        let mut replica = self.inner.lock().unwrap();
        let mut tasks = self.rt.block_on(replica.all_tasks())?;
        ensure_reviewable(
            tasks
                .get(&reviewed_id)
                .ok_or_else(|| TaskError::Internal("Reviewed task not found".into()))?,
        )?;
        let target = tasks
            .get_mut(&target_id)
            .ok_or_else(|| TaskError::Internal("Task not found".into()))?;
        let mut ops = Operations::new();
        apply_edit_patch(target, patch, false, &mut ops)?;
        if !ops.is_empty() {
            let now = Utc::now();
            stamp(
                tasks
                    .get_mut(&reviewed_id)
                    .ok_or_else(|| TaskError::Internal("Reviewed task not found".into()))?,
                now,
                &mut ops,
            )?;
            recurrence::append_repairs(&tasks, now, &mut ops)?;
            self.rt.block_on(replica.commit_operations(ops))?;
        }
        Ok(())
    }

    pub fn add_annotation_for_review(
        &self,
        uuid: String,
        description: String,
    ) -> Result<Annotation> {
        with_review_task(self, uuid, |task, ops| {
            let mut entry = DateTime::from_timestamp(Utc::now().timestamp(), 0)
                .ok_or_else(|| TaskError::Internal("Invalid current time".into()))?;
            let existing: Vec<_> = task
                .get_annotations()
                .map(|a| a.entry.timestamp())
                .collect();
            while existing.contains(&entry.timestamp()) {
                entry += chrono::Duration::seconds(1);
            }
            task.add_annotation(
                taskchampion::Annotation {
                    entry,
                    description: description.clone(),
                },
                ops,
            )?;
            Ok(Annotation {
                entry: entry.to_rfc3339(),
                description,
            })
        })
    }

    pub fn remove_annotation_for_review(&self, uuid: String, entry: String) -> Result<()> {
        let entry = parse_rfc3339(Some(entry))?
            .ok_or_else(|| TaskError::Internal("Invalid annotation date".into()))?;
        with_review_task(self, uuid, |task, ops| {
            if !task
                .get_annotations()
                .any(|a| a.entry.timestamp() == entry.timestamp())
            {
                return Err(TaskError::Internal("Annotation changed elsewhere".into()));
            }
            task.remove_annotation(entry, ops)?;
            Ok(())
        })
    }
}
