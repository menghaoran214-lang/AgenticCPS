#!/usr/bin/env python3
"""Only sanitized exception summaries from disposable GitHub-hosted HTTP CI.

Never reads cloud hosts, files, credentials, business rows or production logs.
"""
import os
from pathlib import Path
import re
import subprocess
from station_hp_preflight import github_hosted

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / '.station-ci'
PATTERN = re.compile(
    r"(?i)(caused by:|Exception:|SQLSyntaxErrorException|SQLIntegrityConstraint|"
    r"BadSqlGrammarException|DataAccessException|TooManyResultsException|"
    r"Unknown column|Unknown table|Table '[^']+' doesn't exist|Error querying database|"
    r"Invalid bound statement|ResultMapException|TypeException|NullPointerException|"
    r"MyBatisSystemException|ClassCastException|ReflectionException)"
)


def main():
    if not github_hosted() or os.getenv('CPS_STATION_PROFILE') != 'ci' \
            or os.getenv('CPS_STATION_CANONICAL_SCHEMA') != '1' \
            or os.getenv('CPS_STATION_ISOLATED') != '1':
        raise RuntimeError('Only the disposable canonical GitHub CI is permitted')
    project = os.getenv('COMPOSE_PROJECT_NAME', '')
    if not re.fullmatch(r'cps-station-ci-[0-9]+-[0-9]+', project):
        raise RuntimeError('Unexpected project identity')
    cmd = ['docker', 'compose', '--project-directory', str(ROOT),
           '--env-file', str(WORK / 'runtime.env'),
           '-f', str(WORK / 'compose.json'),
           'logs', '--no-color', '--tail', '550', 'qiji-server']
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=20)
    if result.returncode:
        print('CI_DIAGNOSTICS_NOT_AVAILABLE')
        return
    secrets = [os.getenv(x) for x in (
        'MYSQL_ROOT_PASSWORD', 'MYSQL_PASSWORD', 'REDIS_PASSWORD',
        'CPS_STATION_PASSWORD', 'CPS_STATION_NONCE') if os.getenv(x)]
    findings = []
    for line in (result.stdout + '\n' + result.stderr).splitlines():
        if not PATTERN.search(line):
            continue
        # Only exception line/type and SQL metadata; synthetic test credentials redacted.
        for value in secrets:
            line = line.replace(value, '[REDACTED]')
        line = re.sub(r'(?i)Bearer\s+[a-z0-9._-]+', 'Bearer [REDACTED]', line)
        findings.append(line[:800])
    for line in findings[-40:]:
        print('CI_SANITIZED_EXCEPTION: ' + line)
    if not findings:
        print('CI_NO_EXCEPTION_CLASS_IN_LAST_550_BACKEND_LOG_LINES')


if __name__ == '__main__':
    main()
