"""Isolation boundaries for the synthetic restore harness; no DB or subprocess is run."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import subprocess

spec = importlib.util.spec_from_file_location("restore", Path(__file__).with_name("cps_mysql8_synthetic_restore.py"))
restore = importlib.util.module_from_spec(spec)
spec.loader.exec_module(restore)

class SyntheticRestoreBoundaryTest(unittest.TestCase):
    def test_requires_all_disposable_runner_markers(self):
        markers = {"GITHUB_ACTIONS": "true", "RUNNER_ENVIRONMENT": "github-hosted",
                   "CPS_MYSQL8_INTEGRATION": "1"}
        with patch.dict(restore.os.environ, markers, clear=True):
            restore.guard()
        for key in markers:
            invalid = dict(markers)
            invalid.pop(key)
            with patch.dict(restore.os.environ, invalid, clear=True), self.assertRaises(RuntimeError):
                restore.guard()

    def test_wrong_environment_fails_before_any_command(self):
        with patch.dict(restore.os.environ, {}, clear=True), patch.object(restore, "command") as command:
            with self.assertRaises(RuntimeError):
                restore.main()
            command.assert_not_called()

    def test_rejects_business_schema_before_any_command(self):
        with patch.object(restore, "command") as command:
            for name in ("cps", "mysql", "other"):
                with self.assertRaises(ValueError):
                    restore.sql(name, "SHOW TABLES")
            command.assert_not_called()

    def test_command_uses_fixed_loopback_and_redacts_failure(self):
        failure = subprocess.CompletedProcess([], 1, b"", b"synthetic confidential diagnostic")
        with patch.object(restore.subprocess, "run", return_value=failure) as run:
            with self.assertRaisesRegex(RuntimeError, "^Synthetic CI database command failed: mysql$"):
                restore.command("mysql", ["cps_mapper_ci"])
            self.assertEqual(run.call_args.args[0], ["mysql", "-h127.0.0.1", "-uroot", "cps_mapper_ci"])
            self.assertEqual(run.call_args.kwargs["timeout"], 90)

if __name__ == "__main__":
    unittest.main()
