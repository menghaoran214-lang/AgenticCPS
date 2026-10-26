"""Fail closed when required safety suites did not actually pass tests."""
import sys
from pathlib import Path
import xml.etree.ElementTree as ET


def verify(root, required):
    passed = {name: 0 for name in required}
    failed = []
    for report in Path(root).glob('**/target/surefire-reports/TEST-*.xml'):
        suite = ET.parse(report).getroot()
        for case in suite.iter('testcase'):
            name = case.get('classname', '').rsplit('.', 1)[-1]
            if name not in passed:
                continue
            if case.find('failure') is not None or case.find('error') is not None:
                failed.append(name + '.' + case.get('name', '?'))
            elif case.find('skipped') is None:
                passed[name] += 1
    missing = [name for name, count in passed.items() if count == 0]
    if failed or missing:
        raise ValueError(f'Failed tests: {failed}; suites without passing tests: {missing}')
    return passed


if __name__ == '__main__':
    results = verify(sys.argv[1], sys.argv[2].split(','))
    for name, count in results.items():
        print(f'{name}: {count} passed')
    print(f'Safety gate: {sum(results.values())} tests passed in {len(results)} required suites')
