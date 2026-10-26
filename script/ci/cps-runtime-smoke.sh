#!/usr/bin/env bash
# Disposable, localhost-only CI smoke test. Never run against existing production.
set -Eeuo pipefail
cd "$(dirname "$0")/../../backend/script/docker"
mkdir -p mysql/init backend frontend/dist frontend/mall-h5
cp ../../sql/mysql/ruoyi-vue-pro.sql mysql/init/00-ruoyi-vue-pro.sql
cp ../../sql/mysql/quartz.sql mysql/init/05-quartz.sql
cp ../../sql/module/cps-all-in-one.sql mysql/init/10-cps-all-in-one.sql
if test -f ../../sql/module/ai-all.sql; then
  cp ../../sql/module/ai-all.sql mysql/init/15-ai-all.sql
else
  echo "NOTE: upstream ai-all.sql missing; testing core initialization only."
fi
test -s backend/qiji-server.jar
test -s frontend/dist/index.html
test -s frontend/mall-h5/index.html
python3 - <<'PY'
from pathlib import Path
from secrets import token_hex
p = Path("docker.env")
values = {
  "MYSQL_ROOT_PASSWORD": token_hex(20),
  "MYSQL_PASSWORD": token_hex(20),
  "REDIS_PASSWORD": token_hex(20),
  "ADMIN_HOST_PORT": "127.0.0.1:18080",
  "SERVER_HOST_PORT": "127.0.0.1:48080",
  "MYSQL_HOST_PORT": "127.0.0.1:13306",
  "REDIS_HOST_PORT": "127.0.0.1:16379",
  "JAVA_OPTS": "-Xms256m -Xmx640m",
  "JAVA_ARGS": "--qiji.security.mock-enable=false --qiji.captcha.enable=true --spring.ai.mcp.server.enabled=false --justauth.enabled=false",
  "SWAGGER_ENABLED": "false",
  "AI_MCP_CLIENT_ENABLED": "false",
  "AI_MCP_CLIENT_TOOLCALLBACK_ENABLED": "false",
  "HAINA_MCP_ENABLED": "false",
}
output=[]
for line in p.read_text().splitlines():
    if "=" in line and not line.lstrip().startswith("#"):
        key=line.split("=",1)[0]
        if key in values:
            line=f"{key}={values[key]}"
    output.append(line)
Path("docker.smoke.env").write_text("\n".join(output)+"\n")
PY
chmod 600 docker.smoke.env
cat > docker.smoke.limits.yml <<'YAML'
services:
  mysql:
    mem_limit: 960m
    environment:
      MYSQL_INITDB_SKIP_TZINFO: "1"
  redis:
    mem_limit: 256m
  server:
    mem_limit: 1500m
    environment:
      SPRING_AI_MCP_SERVER_ENABLED: "false"
      CAPTCHA_ENABLE: "true"
  admin:
    mem_limit: 192m
YAML
compose() {
  docker compose --env-file docker.smoke.env -f docker-compose.yml -f docker.smoke.limits.yml -p cps-ci-smoke "$@"
}
cleanup() {
  echo "Removing disposable CI containers and volumes"
  compose down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT
compose config --quiet
echo "Starting isolated, resource-limited CPS containers..."
compose up -d --build
echo "Probing admin, H5, and backend health..."
for i in $(seq 1 100); do
  admin_status=$(curl -sS -o /dev/null --max-time 3 -w '%{http_code}' http://127.0.0.1:18080/ || true)
  h5_status=$(curl -sS -o /dev/null --max-time 3 -w '%{http_code}' http://127.0.0.1:18080/h5/ || true)
  java_status=$(curl -sS -o /dev/null --max-time 3 -w '%{http_code}' http://127.0.0.1:48080/actuator/health || true)
  if [[ "$admin_status" == "200" && "$h5_status" == "200" && "$java_status" == "200" ]]; then
    echo "SUCCESS: admin=200, h5=200, backend-health=200"
    echo "Container resource usage:"
    docker stats --no-stream --format 'table {{.Name}} {{.MemUsage}} {{.CPUPerc}}'
    echo "Memory limits combined <= 3 GiB; this is NOT a physical 4GiB host benchmark."
    exit 0
  fi
  if (( i % 10 == 0 )); then
    echo "Probe $i: admin=$admin_status h5=$h5_status backend=$java_status"
    compose ps --format 'table {{.Service}} {{.Status}}'
  fi
  sleep 5
done
echo "FAIL: health check did not pass: admin=$admin_status h5=$h5_status backend=$java_status"
compose ps --format 'table {{.Service}} {{.Status}}'
exit 1
