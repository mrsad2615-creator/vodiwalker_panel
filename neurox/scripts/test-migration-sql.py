#!/usr/bin/env python3
"""Execute the production migration SQL with local SQLite. Not a Room runtime test."""
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[1]
source = (root / "core/data/src/main/java/com/neurox/data/Database.kt").read_text()
statements = [a or b for a, b in re.findall(r'db\.execSQL\((?:"""(.*?)"""|"(.*?)")\)', source, re.S)]
assert len(statements) == 2, "Review this test when another migration is added"
with sqlite3.connect(":memory:") as db:
    db.execute("PRAGMA foreign_keys = ON")
    db.executescript((root / "core/data/src/androidTest/assets/schema-v1.sql").read_text())
    db.execute("INSERT INTO profiles VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", ("guest", "Keep me", 1, "FA", "DARK", 4292131202, 4285946408, 1, 0, 1.0))
    db.execute("INSERT INTO favorites VALUES ('guest', 'FOCUS_LOCK')")
    db.execute("INSERT INTO saved_themes VALUES ('theme-old', 'guest', 'Keep theme', 4292131202, 4285946408)")
    before = {t: db.execute(f"SELECT * FROM {t}").fetchall() for t in ("profiles", "sessions", "favorites", "saved_themes", "adaptations")}
    for sql in statements:
        db.execute(sql)
    for table, rows in before.items():
        assert db.execute(f"SELECT * FROM {table}").fetchall() == rows, table
    print("PASS SQLite: production migration preserves every existing table row")
    db.execute("INSERT INTO custom_exercises VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", ("custom-id", "guest", "Local", "PATTERN_MATCH", "VISION", 1, 5, 5000, 80, 2, 1, 10, 10))
    assert len(db.execute("PRAGMA table_info(custom_exercises)").fetchall()) == 13
    assert not db.execute("PRAGMA foreign_key_check").fetchall()
    assert db.execute("SELECT name FROM sqlite_master WHERE type='index' AND name='index_custom_exercises_profileId'").fetchone()
    print("PASS SQLite: custom exercise columns, index and foreign key are present")
    try:
        db.execute("INSERT INTO custom_exercises SELECT 'bad', 'missing-profile', name, template, module, difficulty, rounds, responseLimitMs, targetAccuracyPercent, errorAllowance, schemaVersion, createdAt, updatedAt FROM custom_exercises LIMIT 1")
    except sqlite3.IntegrityError:
        print("PASS SQLite: missing-profile insert is rejected")
    else:
        raise AssertionError("Foreign key did not reject an invalid profile")
    db.execute("DELETE FROM profiles WHERE id = 'guest'")
    for table in ("custom_exercises", "favorites", "saved_themes"):
        assert db.execute(f"SELECT count(*) FROM {table}").fetchone()[0] == 0
    print("PASS SQLite: profile deletion cascades to custom and existing child rows")
print("SQLITE RESULT: 4 checks passed; Android Room validation still requires a device or emulator")
