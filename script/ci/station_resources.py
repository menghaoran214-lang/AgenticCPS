"""Capture Docker-enforced limits and cgroup peaks; never alter running services."""
import json
import os
from pathlib import Path
import subprocess
from station_profiles import get_profile, HP_LIMITS

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / '.station-ci'
PROFILE = os.environ.get('CPS_STATION_PROFILE', 'ci')

def output(args):
    return subprocess.check_output(args, text=True, stderr=subprocess.DEVNULL, timeout=15).strip()

def main():
    get_profile(PROFILE)
    project = os.environ.get('COMPOSE_PROJECT_NAME', '')
    if not project.startswith('cps-station-' + PROFILE + '-'):
        raise ValueError('Refusing to inspect an unrecognized test project')
    rows = []
    for name in ['qiji-mysql', 'qiji-redis', 'qiji-server', 'admin']:
        container = output(['docker', 'compose', '--project-directory', str(ROOT),
                            '--env-file', str(WORK / 'runtime.env'), '-f', str(WORK / 'compose.json'),
                            'ps', '-q', name])
        info = json.loads(output(['docker', 'inspect', container]))[0]
        if info['Config']['Labels'].get('com.docker.compose.project') != project:
            raise ValueError('Container project identity mismatch')
        try:
            peak = int(output(['docker', 'exec', container, 'cat', '/sys/fs/cgroup/memory.peak']))
        except (subprocess.SubprocessError, ValueError):
            peak = None
        row = {'service': name, 'memory_limit_bytes': info['HostConfig']['Memory'],
               'memory_swap_limit_bytes': info['HostConfig']['MemorySwap'],
               'cpu_nano_limit': info['HostConfig']['NanoCpus'],
               'peak_memory_bytes': peak, 'oom_killed': info['State']['OOMKilled'],
               'restarts': info['RestartCount'], 'running': info['State']['Running']}
        rows.append(row)
    report = {'profile': PROFILE, 'services': rows,
              'sum_individual_peaks_mib': round(sum(row['peak_memory_bytes'] or 0 for row in rows) / 1048576, 1),
              'peak_note': 'Sum of individual cgroup peaks; conservative, not simultaneous usage.',
              'hp_required_available_mib': get_profile('hp')['required_available_mib']}
    (WORK / 'evidence/resources.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report, indent=2))
    for row in rows:
        if not row['running'] or row['oom_killed'] or row['restarts']:
            raise ValueError('A test service stopped, restarted or was OOM-killed')
        if PROFILE == 'hp':
            memory, cpu, _ = HP_LIMITS[row['service']]
            if row['memory_limit_bytes'] != memory * 1048576 \
                    or row['memory_swap_limit_bytes'] != row['memory_limit_bytes'] \
                    or row['cpu_nano_limit'] != int(cpu * 1000000000) \
                    or row['peak_memory_bytes'] is None:
                raise ValueError('HP effective limits or cgroup peak evidence missing')
    print('PASS: test services remained within enforced profile limits without OOM or restart')

if __name__ == '__main__':
    main()
