"""Fail-closed checks for staging source-controlled 188 objects in disposable HTTP CI."""
import os
import unittest
from unittest.mock import patch
import station_journey as station


class CanonicalSchemaTest(unittest.TestCase):
    def test_refuse_canonical_on_non_github_runner(self):
        with patch.dict(os.environ, {"CPS_STATION_CANONICAL_SCHEMA": "1",
                                     "GITHUB_ACTIONS": "false",
                                     "RUNNER_ENVIRONMENT": "github-hosted"}):
            with self.assertRaises(ValueError):
                station.canonical_schema_enabled()

    def test_refuse_unknown_mode(self):
        with patch.dict(os.environ, {"CPS_STATION_CANONICAL_SCHEMA": "true"}):
            with self.assertRaises(ValueError):
                station.canonical_schema_enabled()

    def test_source_sql_is_present_and_versions_ordered(self):
        self.assertEqual(len(station.CANONICAL_MIGRATIONS), 6)
        self.assertEqual([name.split("__")[0] for name in station.CANONICAL_MIGRATIONS],
                         [f"V20261028_0{x}" for x in range(1, 7)])
        for name in station.CANONICAL_MIGRATIONS:
            self.assertTrue((station.ROOT / "backend/sql/migrations" / name).is_file())
        self.assertTrue((station.ROOT / "backend/sql/module/member-update.sql").is_file())

    def test_fixture_table_is_never_created_in_canonical_mode(self):
        with patch.dict(os.environ, {"CPS_STATION_CANONICAL_SCHEMA": "1",
                                     "GITHUB_ACTIONS": "true",
                                     "RUNNER_ENVIRONMENT": "github-hosted"}):
            # A unit test must model CI explicitly even when the matrix is exercising
            # the separate HP resource profile on a GitHub-hosted runner.
            with patch.object(station, "PROFILE_NAME", "ci"):
                with patch.object(station, "query", side_effect=AssertionError("fixture DDL executed")):
                    station.create_fixture_table(None, "CREATE TABLE member_user (id bigint)")

    def test_canonical_mode_rejects_hp_profile_even_on_github(self):
        with patch.dict(os.environ, {"CPS_STATION_CANONICAL_SCHEMA": "1",
                                     "GITHUB_ACTIONS": "true",
                                     "RUNNER_ENVIRONMENT": "github-hosted"}):
            with patch.object(station, "PROFILE_NAME", "hp"):
                with self.assertRaises(ValueError):
                    station.canonical_schema_enabled()

    def test_legacy_fixture_remains_unchanged(self):
        with patch.dict(os.environ, {"CPS_STATION_CANONICAL_SCHEMA": "0"}):
            with patch.object(station, "query", return_value=1) as q:
                station.create_fixture_table("testconnection", "CREATE TABLE ci_test (id bigint)")
                q.assert_called_once_with("testconnection", "CREATE TABLE ci_test (id bigint)")


if __name__ == "__main__":
    unittest.main()
