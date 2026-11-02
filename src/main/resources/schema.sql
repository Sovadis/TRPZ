PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS build_script (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           TEXT    NOT NULL UNIQUE,
    description    TEXT,
    default_target TEXT,
    base_dir       TEXT    NOT NULL DEFAULT '.',
    created_at     TEXT    NOT NULL,
    updated_at     TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS script_property (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    script_id INTEGER NOT NULL REFERENCES build_script(id) ON DELETE CASCADE,
    name      TEXT    NOT NULL,
    value     TEXT    NOT NULL,
    read_only INTEGER NOT NULL DEFAULT 0,
    UNIQUE (script_id, name)
);

CREATE TABLE IF NOT EXISTS target (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    script_id   INTEGER NOT NULL REFERENCES build_script(id) ON DELETE CASCADE,
    name        TEXT    NOT NULL,
    description TEXT,
    position    INTEGER NOT NULL,
    UNIQUE (script_id, name)
);

CREATE TABLE IF NOT EXISTS target_dependency (
    target_id     INTEGER NOT NULL REFERENCES target(id) ON DELETE CASCADE,
    depends_on_id INTEGER NOT NULL REFERENCES target(id) ON DELETE CASCADE,
    PRIMARY KEY (target_id, depends_on_id)
);

CREATE TABLE IF NOT EXISTS task (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    target_id INTEGER NOT NULL REFERENCES target(id) ON DELETE CASCADE,
    task_type TEXT    NOT NULL,
    position  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS task_attribute (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id INTEGER NOT NULL REFERENCES task(id) ON DELETE CASCADE,
    name    TEXT    NOT NULL,
    value   TEXT    NOT NULL,
    UNIQUE (task_id, name)
);
