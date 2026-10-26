# AgenticCPS 部署验收记录（2026-10-26）

## 当前结论

**结果：隔离 Docker 启动冒烟测试通过；不能视为生产上线或真实 4GB 云主机的完整性能验收。**

- 代码仓库：`menghaoran214-lang/AgenticCPS`
- 开发分支：`develop`，稳定分支 `master` 暂不修改
- 运行记录：[GitHub Actions #40351887549](https://github.com/menghaoran214-lang/AgenticCPS/actions/runs/40351887549)
- 环境：GitHub Actions `ubuntu-22.04` 临时虚拟机，所有发布端口绑定到 `127.0.0.1`，测试后执行 `docker compose down -v`
- 真实部署产物：Java JAR 与 Vue 管理端来自通过的构建 #40348880700，手机 H5 来自通过的 DCloud 构建 #40350592394
- Docker 运行编排：MySQL 8、Redis 8、Java 21、Nginx
- 容器内存硬上限：MySQL 960MiB、Redis 256MiB、Java 1500MiB、Nginx 192MiB，总和低于 3GiB
- 隔离测试期间未启用正式支付/提现、未部署公网、未停止惠普上现有容器

## 运行实际结果

| 检查 | 测得状态 |
|---|---|
| MySQL 健康检查 | Healthy |
| Redis 健康检查 | Healthy |
| 管理后台 `/` | HTTP 200 |
| 手机 H5 `/h5/` | HTTP 200 |
| Java `/actuator/health` | HTTP 200 |
| 完整 GitHub Actions 工作流 | Success |

测试运行时的单次内存快照：

| 容器 | 使用内存 |
|---|---:|
| qiji-server | 963.8MiB |
| qiji-mysql | 373.3MiB |
| qiji-redis | 5.531MiB |
| qiji-admin | 4.57MiB |

注意：这些是**启动后单次快照**，没有覆盖用户并发、长期运行、定时任务峰值、数据库数据量增长和操作系统/容器运行时开销。并未在真正的 4GiB 宿主机上执行全链路压力测试。4核4GB 仅作为继续试点的合理候选配置。

## 发现并修复的问题

1. Java 后端、Vue 后台及 DCloud HBuilderX H5 已分别成功编译。H5 使用用户自己的 AppID `__UNI__D067534`。
2. 上游 `frontend/admin-vue3/pnpm-workspace.yaml` 构建配置缺陷已修复。
3. H5 的 NPM 依赖及 Vue3 版本冲突已处理，构建流程使用加密仓库 Secrets，保留手动触发。
4. 原仓库 UTF-8 校验所需脚本与单元测试缺失问题已修复，相关工作流成功。
5. 上游 `backend/sql/module/ai-all.sql` **不存在**。两份打包脚本现会发出警告，不再因为该缺失文件停止核心打包；**AI 默认角色/初始化数据并未补齐，相关功能尚不能宣称正常**。

## 正式经营前的阻断清单

- [ ] 完整初始化 SQL 兼容性审查，补齐 AI 模块初始化或明确禁用 AI 模块。
- [ ] 管理员真实登录、权限隔离、消费者注册登录、H5 API 功能验收。
- [ ] 检查 `application-prod.yaml` 中的测试号、演示/模拟开关、第三方密钥和支付回调，修改为受控的生产配置；按环境严格关闭 mock 登录并验证实效。
- [ ] MySQL/Redis/Java 仅允许内网访问；Nginx 对外仅 HTTPS；禁用公开文档及非必要 MCP 服务。
- [ ] 验证官方联盟 API 凭证、真实转链、订单归因、结算金额、退款冲正、重复订单幂等和资金对账。
- [ ] 快递站专属二维码、推广关系、代理佣金和总部账本为**独立待开发功能**，不可直接复用商城二级分销作为正式 CPS 账本。
- [ ] 自动提现默认关闭；完成财务审计、审批、备份/恢复、安全回归后再开启真实提现。
- [ ] 在独立 4核4GB 宿主机或可信的 4GB 限额环境执行 24h 稳态及业务高峰压测，验证 CPU、RSS、OOM、磁盘、响应时间与数据库恢复。
- [ ] 逐项核实个人主体经营资质、微信接入、付费服务及平台代理规则。

## 未来云服务器选择

继续以腾讯云 4核4GB 轻量机作为小规模试点候选，而非经过性能承诺的结论。部分首购活动机可能禁止原地升配，因此部署结构须保证异机备份与整机迁移能力。**购买前仍应核实活动条款、磁盘大小、续费价和地域。**

## 复现测试

工作流：`.github/workflows/cps-runtime-smoke.yml`

执行脚本：`script/ci/cps-runtime-smoke.sh`

该工作流当前直接读取此前构建的 GitHub Actions Artifacts。其产物保留期约 7 天；到期后需要更新引用的构建 run ID 或改造为单次流水线构建，不可将其当成长期固定的发布包。
