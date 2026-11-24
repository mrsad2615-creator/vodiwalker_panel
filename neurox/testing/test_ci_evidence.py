"""Tests for the local CI evidence reader. No network access is required."""
import importlib.util
import json
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / "scripts/read-ci-evidence.py"
spec = importlib.util.spec_from_file_location("ci_evidence", path)
reader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reader)


class SchemaEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.path = "core/data/schemas/com.neurox.data.NeuroxDatabase/2.json"
        self.schema = {"formatVersion": 1, "database": {"version": 2, "identityHash": "a" * 32}}
        text = json.dumps(self.schema)
        self.values = [
            {"title": "Room schema chunk", "message": f"{self.path}|1|2|" + text[:20]},
            {"title": "Room schema chunk", "message": f"{self.path}|2|2|" + text[20:]},
        ]

    def test_complete_out_of_order_chunks(self):
        self.assertEqual({self.path: self.schema}, reader.collect_schema_chunks(self.values[::-1]))

    def test_missing_chunk_is_not_accepted(self):
        with self.assertRaises(ValueError):
            reader.collect_schema_chunks(self.values[:1])

    def test_duplicate_chunk_is_not_accepted(self):
        with self.assertRaises(ValueError):
            reader.collect_schema_chunks(self.values + self.values[:1])

    def test_unsafe_path_is_rejected(self):
        self.values[0]["message"] = self.values[0]["message"].replace(self.path, "../../unsafe.json")
        with self.assertRaises(ValueError):
            reader.collect_schema_chunks(self.values)

    def test_inconsistent_version_is_rejected(self):
        for value in self.values:
            value["message"] = value["message"].replace(self.path, self.path.replace("/2.json", "/3.json"))
        with self.assertRaises(ValueError):
            reader.collect_schema_chunks(self.values)

    def test_malformed_payload_is_rejected(self):
        self.values[1]["message"] = f"{self.path}|2|2|broken json"
        with self.assertRaises(ValueError):
            reader.collect_schema_chunks(self.values)


if __name__ == "__main__":
    unittest.main()
