import copy
import json
import unittest
from pathlib import Path
from station_profiles import get_profile, make_compose, require_hp_target, require_loopback

ROOT = Path(__file__).resolve().parents[2]

class HpStationProfileTest(unittest.TestCase):
    def facts(self):
        return dict(hostname='mm-HP-Pavilion-Notebook', root='/home/mm/dev/agenticcps-lab',
                    branch='develop', dirty=False, available_mib=4096, occupied_ports=[])

    def test_wrong_machine_path_branch_or_dirty_tree_is_rejected(self):
        for key, value in [('hostname', 'MM'), ('root', '/tmp/agenticcps'),
                           ('branch', 'master'), ('dirty', True)]:
            facts = self.facts()
            facts[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                require_hp_target(facts)

    def test_headroom_and_each_port_are_required(self):
        profile = get_profile('hp')
        facts = self.facts()
        facts['available_mib'] = profile['required_available_mib'] - 1
        with self.assertRaises(ValueError):
            require_hp_target(facts)
        for port in profile['ports'].values():
            facts = self.facts()
            facts['occupied_ports'] = [port]
            with self.subTest(port=port), self.assertRaises(ValueError):
                require_hp_target(facts)
        require_hp_target(self.facts())

    def test_profile_cannot_redirect_to_other_profile_or_external_target(self):
        require_loopback('http://127.0.0.1:18082', '1', 'hp')
        for url in ['http://127.0.0.1:18081', 'http://user@127.0.0.1:18082',
                    'http://127.0.0.1:18082/production', 'https://example.com']:
            with self.subTest(url=url), self.assertRaises(ValueError):
                require_loopback(url, '1', 'hp')
        for name in ['production', '', '../hp']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                get_profile(name)

    def test_hp_compose_keeps_isolation_and_caps_resources_without_mutating_ci(self):
        base = json.loads((ROOT / 'script/ci/compose.station.json').read_text(encoding='utf-8'))
        original = copy.deepcopy(base)
        hp = make_compose('hp', base)
        self.assertEqual(base, original)
        self.assertTrue(hp['networks']['default']['internal'])
        expected = {'qiji-mysql': '127.0.0.1:13308:3306',
                    'qiji-redis': '127.0.0.1:16381:6379',
                    'qiji-server': '127.0.0.1:48082:48080',
                    'admin': '127.0.0.1:18082:80'}
        for name, service in hp['services'].items():
            with self.subTest(service=name):
                self.assertEqual(service['ports'], [expected[name]])
                self.assertNotIn('container_name', service)
                self.assertNotIn('network_mode', service)
                self.assertEqual(service['memswap_limit'], service['mem_limit'])
                self.assertLessEqual(float(service['cpus']), 0.5)
                self.assertGreater(service['pids_limit'], 0)
        self.assertIn('-Xmx384m', hp['services']['qiji-server']['command'])
        for volume in hp['volumes'].values():
            self.assertNotIn('name', volume)
            self.assertNotIn('external', volume)

if __name__ == '__main__':
    unittest.main()
