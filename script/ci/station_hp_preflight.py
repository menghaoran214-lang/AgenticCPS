"""Read-only HP preflight. Never installs, starts, stops or changes services."""
import json
import os
from pathlib import Path
import socket
import subprocess
from station_profiles import get_profile, require_hp_target, require_hp_identity

ROOT = Path(__file__).resolve().parents[2]

def run_read(command, root=ROOT):
    return subprocess.check_output(command, cwd=root, text=True, timeout=10).strip()

def collect_facts(root=ROOT):
    available = None
    for line in Path('/proc/meminfo').read_text(encoding='utf-8').splitlines():
        if line.startswith('MemAvailable:'):
            available = int(line.split()[1]) // 1024
    if available is None:
        raise ValueError('Cannot determine available Linux memory; refusing to guess')
    occupied = []
    for line in run_read(['ss', '-H', '-ltn'], root).splitlines():
        parts = line.split()
        if len(parts) >= 4:
            occupied.append(int(parts[3].rsplit(':', 1)[1]))
    return {'hostname': socket.gethostname(), 'root': str(root.resolve()),
            'branch': run_read(['git', 'branch', '--show-current'], root),
            'dirty': bool(run_read(['git', 'status', '--porcelain=v1'], root)),
            'available_mib': available, 'occupied_ports': sorted(set(occupied))}

def github_hosted():
    return os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted'

def require_execution_context(profile, initial=False):
    get_profile(profile)
    if github_hosted():
        return
    if profile != 'hp':
        raise ValueError('CI fixture actions may only run on a GitHub-hosted runner')
    facts = collect_facts()
    (require_hp_target if initial else require_hp_identity)(facts)

if __name__ == '__main__':
    facts = collect_facts()
    report = {'mode': 'read-only', 'facts': facts, 'profile': get_profile('hp')}
    try:
        require_hp_target(facts)
        report['ready'] = True
    except ValueError as error:
        report.update(ready=False, reason=str(error))
    print(json.dumps(report, indent=2))
    raise SystemExit(0 if report['ready'] else 2)
