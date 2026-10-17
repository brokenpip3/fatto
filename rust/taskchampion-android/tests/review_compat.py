"""Check the native reviewed encoding without opening the container's current tasks."""

import json
import os
from pathlib import Path
import sqlite3
import subprocess
import tempfile
import time


with tempfile.TemporaryDirectory(prefix="fatto-review-compat-") as directory:
    root = Path(directory)
    data = root / "data"
    data.mkdir()
    taskrc = root / "taskrc"
    taskrc.write_text(
        f"data.location={data}\nuda.reviewed.type=date\nuda.reviewed.label=Reviewed\n"
    )
    environment = dict(os.environ, TASKRC=str(taskrc), TASKDATA=str(data))

    def task(*arguments):
        return subprocess.run(
            ["task", "rc.confirmation:no", "rc.hooks:off", "rc.verbose:nothing", *arguments],
            env=environment,
            text=True,
            capture_output=True,
            check=True,
        ).stdout

    task("add", "Fatto isolated review fixture", "reviewed:now")
    exported = json.loads(task("export"))
    assert len(exported) == 1 and exported[0]["description"] == "Fatto isolated review fixture"
    raw_reviewed = None
    for path in data.rglob("*"):
        if not path.is_file():
            continue
        with path.open("rb") as handle:
            if handle.read(16) != b"SQLite format 3\x00":
                continue
        with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as connection:
            try:
                rows = connection.execute("SELECT data FROM tasks").fetchall()
            except sqlite3.OperationalError:
                continue
            for (serialized,) in rows:
                value = json.loads(serialized)
                if "reviewed" in value:
                    raw_reviewed = value["reviewed"]
    assert raw_reviewed is not None, "No reviewed property found in the isolated native database"
    assert abs(int(raw_reviewed) - time.time()) < 60, raw_reviewed
    print("Taskwarrior date UDA uses native epoch seconds:", raw_reviewed)
    print("Date UDA export:", exported[0]["reviewed"])
    print("Compatibility check passed; temporary taskrc and task data removed on exit.")
