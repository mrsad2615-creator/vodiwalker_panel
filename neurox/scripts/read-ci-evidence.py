#!/usr/bin/env python3
"""Read native build evidence through the GitHub API without downloading log blobs.

Uses the existing gh connection. Never reads or stores authentication tokens.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def command(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True)


def api(path):
    return json.loads(command("gh", "api", path))


def collect_schema_chunks(annotations):
    """Require all numbered chunks. Never accept a truncated or partial schema."""
    chunks = {}
    for annotation in annotations:
        if annotation.get("title") != "Room schema chunk":
            continue
        path, index, count, content = annotation["message"].split("|", 3)
        if not re.fullmatch(r"core/data/schemas/com\.neurox\.data\.NeuroxDatabase/[1-9][0-9]*\.json", path):
            raise ValueError("Unsafe schema path in CI evidence")
        index, count = int(index), int(count)
        if not 1 <= index <= count <= 50:
            raise ValueError("Invalid schema chunk bounds")
        entry = chunks.setdefault(path, {"count": count, "parts": {}})
        if entry["count"] != count or index in entry["parts"]:
            raise ValueError("Contradictory schema chunks")
        entry["parts"][index] = content
    schemas = {}
    for path, entry in chunks.items():
        if len(entry["parts"]) != entry["count"]:
            raise ValueError("Missing schema chunks")
        schema = json.loads("".join(entry["parts"][i] for i in range(1, entry["count"] + 1)))
        if schema.get("formatVersion") != 1 or schema["database"]["version"] != int(Path(path).stem):
            raise ValueError("Schema version does not match its path")
        if not re.fullmatch(r"[0-9a-f]{32}", schema["database"]["identityHash"]):
            raise ValueError("Invalid Room identity hash")
        schemas[path] = schema
    return schemas


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=int, help="GitHub Actions run ID for the current branch")
    parser.add_argument("--save-schema", action="store_true", help="Save complete generated Room schemas to source")
    args = parser.parse_args()
    repo = json.loads(command("gh", "repo", "view", "--json", "nameWithOwner"))["nameWithOwner"]
    run = api(f"repos/{repo}/actions/runs/{args.run}")
    branch = command("git", "branch", "--show-current").strip()
    if run["head_branch"] != branch:
        raise ValueError("This run belongs to another branch")
    jobs = api(f"repos/{repo}/actions/runs/{args.run}/jobs?per_page=100")["jobs"]
    annotations = []
    for job in jobs:
        values = api(f"repos/{repo}/check-runs/{job['id']}/annotations?per_page=100")
        annotations.extend(dict(a, job=job["name"]) for a in values)
    artifacts = api(f"repos/{repo}/actions/runs/{args.run}/artifacts?per_page=100")["artifacts"]
    evidence = {"run": run, "jobs": jobs, "annotations": annotations, "artifacts": artifacts}
    output = ROOT / "test-output" / f"ci-{args.run}"
    output.mkdir(parents=True, exist_ok=True)
    (output / "evidence.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n")
    print(f"Run {args.run}: {run['status']} / {run.get('conclusion')} at {run['head_sha']}")
    for job in jobs:
        print(f"  {job['name']}: {job['status']} / {job.get('conclusion')}")
    for value in annotations:
        if value.get("title") in ["Android test counts", "Debug APK evidence"] or value["annotation_level"] == "failure":
            print(f"  {value.get('title') or value['annotation_level']}: {value['message']}")
    for artifact in artifacts:
        if not artifact.get("expired"):
            print(f"  {artifact['name']}: https://github.com/{repo}/actions/runs/{args.run}/artifacts/{artifact['id']}")
    if args.save_schema:
        if not any(j["name"] == "native-build" and j.get("conclusion") == "success" for j in jobs):
            raise ValueError("No successful native build can attest to these schemas")
        schemas = collect_schema_chunks(annotations)
        if not schemas:
            raise ValueError("This run did not publish chunked schema evidence")
        for path, schema in schemas.items():
            target = ROOT / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps(schema, indent=2) + "\n")
            print(f"  Saved generated schema: {path}")


if __name__ == "__main__":
    main()
