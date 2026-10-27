"""Fixed isolated station profiles; no caller-provided hosts, ports or resource overrides."""
import copy
from urllib.parse import urlsplit

HP_HOST = 'mm-HP-Pavilion-Notebook'
HP_ROOT = '/home/mm/dev/agenticcps-lab'
HP_LIMITS = {
    'qiji-mysql': (512, 0.25, 128),
    'qiji-redis': (64, 0.10, 64),
    'qiji-server': (1024, 0.50, 256),
    'admin': (32, 0.10, 64),
}
PROFILES = {
    'ci': {'ports': {'web': 18081, 'server': 48081, 'sql': 13307, 'redis': 16380}},
    'hp': {'ports': {'web': 18082, 'server': 48082, 'sql': 13308, 'redis': 16381},
           'service_memory_mib': sum(value[0] for value in HP_LIMITS.values()),
           'reserve_mib': 768,
           'required_available_mib': sum(value[0] for value in HP_LIMITS.values()) + 768},
}

def get_profile(name):
    if name not in PROFILES:
        raise ValueError('Unknown station profile; only ci and hp are accepted')
    return copy.deepcopy(PROFILES[name])

def require_loopback(base, enabled, name):
    profile = get_profile(name)
    url = urlsplit(base)
    if enabled != '1' or url.scheme != 'http' or url.hostname != '127.0.0.1' \
            or url.port != profile['ports']['web'] or url.username or url.password \
            or url.path or url.query or url.fragment:
        raise ValueError('Only this profile explicit loopback test address is accepted')

def require_hp_identity(facts):
    if facts.get('hostname') != HP_HOST or facts.get('root') != HP_ROOT \
            or facts.get('branch') != 'develop':
        raise ValueError('HP hostname, repository path and develop branch must match the verified target')

def require_hp_target(facts):
    require_hp_identity(facts)
    profile = get_profile('hp')
    if facts.get('dirty') is not False:
        raise ValueError('Preserve working tree changes before initializing a test workspace')
    if facts.get('available_mib', 0) < profile['required_available_mib']:
        raise ValueError('Insufficient available memory for service limits plus 768 MiB reserve')
    if set(facts.get('occupied_ports', [])) & set(profile['ports'].values()):
        raise ValueError('An HP test port is already occupied; existing services must be preserved')

def make_compose(name, base):
    get_profile(name)
    result = copy.deepcopy(base)
    if name == 'ci':
        return result
    ports = {'qiji-mysql': '127.0.0.1:13308:3306', 'qiji-redis': '127.0.0.1:16381:6379',
             'qiji-server': '127.0.0.1:48082:48080', 'admin': '127.0.0.1:18082:80'}
    for service_name, (memory, cpu, pids) in HP_LIMITS.items():
        service = result['services'][service_name]
        service.update(ports=[ports[service_name]], mem_limit=f'{memory}m',
                       memswap_limit=f'{memory}m', cpus=cpu, pids_limit=pids)
    result['services']['qiji-mysql']['command'] = [
        '--innodb-buffer-pool-size=64M', '--innodb-log-buffer-size=16M',
        '--max-connections=25', '--performance-schema=OFF', '--table-open-cache=256']
    result['services']['qiji-redis']['command'] += [
        '--maxmemory', '32mb', '--maxmemory-policy', 'noeviction']
    command = result['services']['qiji-server']['command']
    result['services']['qiji-server']['command'] = [
        'java', '-Xms128m', '-Xmx384m', '-XX:MaxMetaspaceSize=256m',
        '-XX:ReservedCodeCacheSize=96m', '-XX:MaxDirectMemorySize=64m',
        '-Xss512k', '-XX:ActiveProcessorCount=2'] + command[command.index('-jar'):]
    return result
