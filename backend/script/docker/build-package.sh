#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
PROJECT_ROOT="$(cd -- "${BACKEND_ROOT}/.." && pwd)"
FRONTEND_ROOT="${PROJECT_ROOT}/frontend/admin-vue3"
MALL_ROOT="${PROJECT_ROOT}/frontend/mall-uniapp"

H5_CLI="${SHOPRO_H5_CLI:-${HBUILDERX_CLI_PATH:-}}"

BUILD_BACKEND=0
BUILD_FRONTEND=0
BUILD_MOBILE=0
SKIP_BUILD=0

for arg in "$@"; do
  case "${arg}" in
    --backend|--server)
      BUILD_BACKEND=1
      ;;
    --frontend|--admin)
      BUILD_FRONTEND=1
      ;;
    --mobile|--mall|--h5)
      BUILD_MOBILE=1
      ;;
    --all)
      BUILD_BACKEND=1
      BUILD_FRONTEND=1
      BUILD_MOBILE=1
      ;;
    --skip-build)
      SKIP_BUILD=1
      ;;
    *)
      echo "未知参数: ${arg}。支持: --backend, --frontend, --mobile, --all, --skip-build" >&2
      exit 1
      ;;
  esac
done

# 未指定特定目标时，默认全部构建
if [[ "${BUILD_BACKEND}" -eq 0 && "${BUILD_FRONTEND}" -eq 0 && "${BUILD_MOBILE}" -eq 0 ]]; then
  BUILD_BACKEND=1
  BUILD_FRONTEND=1
  BUILD_MOBILE=1
fi

echo "==> 打包目标: 后端=${BUILD_BACKEND}, 管理前端=${BUILD_FRONTEND}, 移动端=${BUILD_MOBILE}"

# 1. 后端构建
if [[ "${BUILD_BACKEND}" -eq 1 ]]; then
  if [[ "${SKIP_BUILD}" -eq 1 ]]; then
    echo "[1/3] 跳过后端构建，使用已有制品"
  else
    echo "[1/3] 构建后端 JAR"
    mvn -f "${BACKEND_ROOT}/pom.xml" clean package -pl qiji-server -am -DskipTests
  fi
fi

# 2. 管理前端构建
if [[ "${BUILD_FRONTEND}" -eq 1 ]]; then
  if [[ "${SKIP_BUILD}" -eq 1 ]]; then
    echo "[2/3] 跳过管理前端构建，使用已有制品"
  else
    echo "[2/3] 构建管理前端"
    (
      cd "${FRONTEND_ROOT}"
      pnpm install --frozen-lockfile
      pnpm build:docker
    )
  fi
fi

# 3. 移动端 H5 构建
if [[ "${BUILD_MOBILE}" -eq 1 ]]; then
  if [[ "${SKIP_BUILD}" -eq 1 ]]; then
    echo "[3/3] 跳过移动端 H5 构建，使用已有制品"
  elif [[ -n "${SHOPRO_H5_DIST:-}" ]]; then
    echo "[3/3] 使用 SHOPRO_H5_DIST 指定的 mall-uniapp H5 制品"
  else
    echo "[3/3] 构建 mall-uniapp H5"
    if [[ -z "${H5_CLI}" ]]; then
      H5_CLI="$(command -v cli.exe || command -v cli.cmd || command -v cli || true)"
    fi
    if [[ -z "${H5_CLI}" ]]; then
      echo "未找到 HBuilderX CLI。请设置 SHOPRO_H5_CLI/HBUILDERX_CLI_PATH 或将 cli 加入 PATH。" >&2
      exit 1
    fi
    "${H5_CLI}" publish web --project "${MALL_ROOT}" --webHosting false --ssr false
  fi
fi

# 4. 校验与归集
echo "[归集] 校验并归集目标制品..."

if [[ "${BUILD_BACKEND}" -eq 1 ]]; then
  BACKEND_JAR="${BACKEND_ROOT}/qiji-server/target/qiji-server.jar"
  if [[ ! -f "${BACKEND_JAR}" ]]; then
    echo "未找到后端构建产物: ${BACKEND_JAR}" >&2
    exit 1
  fi
  mkdir -p "${SCRIPT_DIR}/backend" "${SCRIPT_DIR}/mysql/init"
  cp -f "${BACKEND_JAR}" "${SCRIPT_DIR}/backend/qiji-server.jar"
  cp -f "${BACKEND_ROOT}/sql/mysql/ruoyi-vue-pro.sql" "${SCRIPT_DIR}/mysql/init/00-ruoyi-vue-pro.sql"
  cp -f "${BACKEND_ROOT}/sql/mysql/quartz.sql" "${SCRIPT_DIR}/mysql/init/05-quartz.sql"
  cp -f "${BACKEND_ROOT}/sql/module/cps-all-in-one.sql" "${SCRIPT_DIR}/mysql/init/10-cps-all-in-one.sql"
  if [[ -f "${BACKEND_ROOT}/sql/module/ai-all.sql" ]]; then
    cp -f "${BACKEND_ROOT}/sql/module/ai-all.sql" "${SCRIPT_DIR}/mysql/init/15-ai-all.sql"
  else
    echo "  [WARN] ai-all.sql is absent in upstream; core CPS can be packaged, but AI default roles/data are NOT initialized." >&2
  fi
  echo "  [OK] 后端 JAR 与数据库初始化脚本已归集"
fi

if [[ "${BUILD_FRONTEND}" -eq 1 ]]; then
  FRONTEND_DIST="${FRONTEND_ROOT}/dist-prod"
  if [[ ! -f "${FRONTEND_DIST}/index.html" ]]; then
    echo "未找到前端构建产物: ${FRONTEND_DIST}/index.html" >&2
    exit 1
  fi
  rm -rf "${SCRIPT_DIR}/frontend/dist"
  mkdir -p "${SCRIPT_DIR}/frontend/dist"
  cp -R "${FRONTEND_DIST}/." "${SCRIPT_DIR}/frontend/dist/"
  echo "  [OK] 管理前端产物已归集: ${SCRIPT_DIR}/frontend/dist"
fi

if [[ "${BUILD_MOBILE}" -eq 1 ]]; then
  if [[ -n "${SHOPRO_H5_DIST:-}" ]]; then
    MALL_H5_DIST="${SHOPRO_H5_DIST}"
  elif [[ -f "${MALL_ROOT}/unpackage/dist/build/web/index.html" ]]; then
    MALL_H5_DIST="${MALL_ROOT}/unpackage/dist/build/web"
  else
    MALL_H5_DIST="${MALL_ROOT}/unpackage/dist/build/h5"
  fi
  if [[ ! -f "${MALL_H5_DIST}/index.html" ]]; then
    echo "未找到 mall-uniapp H5 构建产物: ${MALL_H5_DIST}/index.html。请使用 HBuilderX 发行 H5，或设置 SHOPRO_H5_DIST 指向 H5 dist 目录。" >&2
    exit 1
  fi
  rm -rf "${SCRIPT_DIR}/frontend/mall-h5"
  mkdir -p "${SCRIPT_DIR}/frontend/mall-h5"
  cp -R "${MALL_H5_DIST}/." "${SCRIPT_DIR}/frontend/mall-h5/"
  echo "  [OK] 移动端 H5 产物已归集: ${SCRIPT_DIR}/frontend/mall-h5"
fi

# 5. 校验部署目录
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  (
    cd "${SCRIPT_DIR}"
    docker compose --env-file docker.env config --quiet
  )
else
  echo "未检测到 Docker Compose，跳过 Compose 配置校验。"
fi

echo ""
echo "部署包准备完成: ${SCRIPT_DIR}"
if [[ "${BUILD_BACKEND}" -eq 1 ]]; then echo "  - 后端 JAR: ${SCRIPT_DIR}/backend/qiji-server.jar"; fi
if [[ "${BUILD_FRONTEND}" -eq 1 ]]; then echo "  - 管理前端: ${SCRIPT_DIR}/frontend/dist"; fi
if [[ "${BUILD_MOBILE}" -eq 1 ]]; then echo "  - 移动端 H5: ${SCRIPT_DIR}/frontend/mall-h5"; fi
echo "复制对应文件到服务器后，执行: bash deploy.sh redeploy"
