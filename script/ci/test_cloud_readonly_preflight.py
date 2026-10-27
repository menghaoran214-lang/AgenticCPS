"""Tests for the read-only Tencent preflight; no Docker or database needed."""
import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location("preflight", Path(__file__).with_name("cps_cloud_readonly_preflight.py"))
mod = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(mod)

class PreflightTest(unittest.TestCase):
    def test_rejects_mutations_and_sql_batches(self):
        for query in ("DELETE FROM cps_order", "SELECT 1; DROP DATABASE cps",
                      "SELECT * FROM cps_order INTO OUTFILE '/tmp/x'",
                      "SELECT SLEEP(10)", "SELECT 1 /* hidden */", "SELECT @secret"):
            with self.subTest(query=query), self.assertRaises(ValueError):
                mod.validate_query(query)

    def test_only_fixed_inventory_queries_are_allowed(self):
        for query in mod.QUERIES.values():
            self.assertEqual(mod.validate_query(query), query)
        with self.assertRaises(ValueError):
            mod.validate_query("SELECT password FROM system_users")

    def test_plan_uses_entity_ddl_and_tenant_correction(self):
        expected = mod.expected_columns()
        self.assertEqual(len(expected), 67)
        for table in ("member_address", "member_tag", "member_level", "member_group"):
            self.assertIn("tenant_id", expected[table])

    def test_index_inventory_includes_unique_keys_and_v06(self):
        indexes = mod.expected_indexes()
        self.assertEqual(indexes[("member_user", "uk_member_tenant_mobile_deleted")],
                         (0, ("tenant_id", "mobile", "deleted")))
        self.assertEqual(indexes[("member_address", "idx_member_address_tenant_user")],
                         (1, ("tenant_id", "user_id", "deleted")))
        self.assertGreater(len(indexes), 67)

    def test_missing_table_and_column_are_distinct(self):
        expected = {"member_address": {"id", "tenant_id"}, "pay_wallet": {"id"}}
        gaps = mod.schema_gaps(expected, {"member_address": {"id"}})
        self.assertEqual(gaps["missing_tables"], ["pay_wallet"])
        self.assertEqual(gaps["missing_columns"], {"member_address": ["tenant_id"]})

    def test_unknown_row_count_blocks_empty_gate(self):
        self.assertFalse(mod.empty_gate({"cps_order": 0, "cps_withdraw": None}))
        self.assertFalse(mod.empty_gate({"cps_order": 1}))
        self.assertFalse(mod.empty_gate({}))
        self.assertTrue(mod.empty_gate({"cps_order": 0, "cps_withdraw": 0}))

    def test_backup_requires_checksum_and_archive_integrity(self):
        import gzip, hashlib, tempfile
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            archive = base / "scheduled-test.sql.gz"
            archive.write_bytes(gzip.compress(b"-- synthetic backup\n"))
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            archive.with_name(archive.name + ".sha256").write_text(digest + "  " + archive.name)
            self.assertTrue(mod.backup_check(base)["verified"])
            archive.write_bytes(b"corrupted")
            self.assertFalse(mod.backup_check(base)["verified"])

if __name__ == "__main__":
    unittest.main()
