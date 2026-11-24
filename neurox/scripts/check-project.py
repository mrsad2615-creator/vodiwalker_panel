#!/usr/bin/env python3
"""Static invariants. This does not replace an Android build or runtime tests."""
import ast
import hashlib
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

PROJECT = Path(__file__).resolve().parents[1]
ROOT = PROJECT.parent
count = 0

def verify(name, predicate):
    global count
    if not predicate:
        raise AssertionError(name)
    count += 1
    print(f"PASS static: {name}")

android_ns = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(PROJECT / "app/src/main/AndroidManifest.xml").getroot()
verify("no network permissions", not manifest.findall("uses-permission"))
application = manifest.find("application")
verify("system backup disabled", application.get(android_ns + "allowBackup") == "false")
verify("cleartext disabled", application.get(android_ns + "usesCleartextTraffic") == "false")
verify("RTL enabled", application.get(android_ns + "supportsRtl") == "true")
for path in sorted(PROJECT.glob("**/*.xml")):
    if "build" not in path.parts:
        ET.parse(path)
        print(f"PASS XML: {path.relative_to(PROJECT)}")
        count += 1
for path in sorted(ROOT.glob("*.py")):
    ast.parse(path.read_text())
    baseline = subprocess.check_output(["git", "show", f"78a60848f4419621cbb36a8e730cdb1e29ce64e8:{path.name}"], cwd=ROOT)
    verify(f"existing {path.name} preserved", hashlib.sha256(baseline).digest() == hashlib.sha256(path.read_bytes()).digest())
for folder in ["core/domain/src/main", "backup/crypto/src/main"]:
    for path in (PROJECT / folder).rglob("*.kt"):
        verify(f"{path.name} has no Android or Compose dependency", "import android" not in path.read_text())
repository = (PROJECT / "core/data/src/main/java/com/neurox/data/Repository.kt").read_text()
database = (PROJECT / "core/data/src/main/java/com/neurox/data/Database.kt").read_text()
verify("profile-scoped session lookup", "WHERE id = :id AND profileId = :profileId" in database)
verify("profile-scoped history", "WHERE profileId = :profileId ORDER BY startedAt" in database)
verify("profile deletion cascades", database.count("onDelete = ForeignKey.CASCADE") == 5)
verify("no destructive migration calls", ".fallbackToDestructiveMigration(" not in database)
verify("session completion uses a transaction", "database.withTransaction" in repository)
verify("duplicate completion guarded", 'if (old.status != "ACTIVE")' in repository)
verify("font licence present", (PROJECT / "assets/licenses/Vazirmatn-OFL.txt").is_file())
for path in sorted((PROJECT / "core/designsystem/src/main/res/font").glob("*.ttf")):
    verify(f"bundled TrueType {path.name}", path.read_bytes()[:4] == b"\x00\x01\x00\x00")
verify("Gradle wrapper present", (PROJECT / "gradle/wrapper/gradle-wrapper.jar").stat().st_size > 10000)
print(f"STATIC RESULT: {count} checks passed")
