import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('station_journey', Path(__file__).with_name('station_journey.py'))
journey = importlib.util.module_from_spec(spec)
spec.loader.exec_module(journey)


class StationIsolationTest(unittest.TestCase):
    def test_only_loopback_test_target_is_allowed(self):
        journey.require_isolated('http://127.0.0.1:18081', '1')
        for url in ['https://example.com', 'http://127.0.0.1.evil.test', 'http://user@127.0.0.1:18081',
                    'http://127.0.0.1:18081/production', 'http://192.168.1.10:18081']:
            with self.subTest(url=url), self.assertRaises(ValueError):
                journey.require_isolated(url, '1')
        with self.assertRaises(ValueError):
            journey.require_isolated('http://127.0.0.1:18081', '')

    def test_isolated_compose_has_no_global_names_or_external_network(self):
        compose = json.loads((ROOT / 'script/ci/compose.station.json').read_text(encoding='utf-8'))
        self.assertTrue(compose['networks']['default']['internal'])
        for name, service in compose['services'].items():
            with self.subTest(service=name):
                self.assertNotIn('container_name', service)
                self.assertNotIn('network_mode', service)
                for port in service.get('ports', []):
                    self.assertTrue(port.startswith('127.0.0.1:'))
        for volume in compose['volumes'].values():
            self.assertNotIn('external', volume)
            self.assertNotIn('name', volume)

    def test_database_marker_is_required_before_fixtures(self):
        self.assertTrue(journey.marker_matches('abc123', 'abc123'))
        for expected, actual in [('', ''), ('abc123', 'different'), ('abc123', None)]:
            with self.subTest(expected=expected, actual=actual):
                self.assertFalse(journey.marker_matches(expected, actual))


if __name__ == '__main__':
    unittest.main()
