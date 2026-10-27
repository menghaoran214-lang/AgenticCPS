# AgenticCPS 腾讯云迁移：数据库与上线门禁（2026-10-28）

> 当前只允许封闭测试；不得把此文档或任何测试夹具解释为生产上线许可。

## 目标、环境与版本

- 目标主机：腾讯云轻量应用服务器 `VM-0-8-ubuntu`（非惠普笔记本）。
- 远程账号：`cpsops`，Rootless Docker 29.9.2、Compose 5.6.1。
- 仓库：`menghaoran214-lang/AgenticCPS` 的 `develop`；当前部署业务 JAR 和管理端来自 CI 构建提交 `c3ba73fac5f023536c6e2b08325237cdc13f75db`，H5 为前一已验证的独立构建。
- 业务入口仅本机 `127.0.0.1:18080`；MySQL/Redis/Java 端口不发布到主机。
- 运行服务：MySQL 8、Redis 8、Java 21、Nginx/H5。实际健康检查通过。
- Rootless Docker、远程管理、每日 MySQL 本机备份及每 5 分钟被动巡检均已配置。

## 已证实的上线阻碍

1. **业务表不足**：目前 `cps` 有 121 张表，包含 CPS 订单、返利、资金流水等，但缺少 `member_user`、`member_level`、`pay_wallet`、`trade_order`、`trade_cart`、`trade_after_sale`、`promotion_coupon`、`promotion_diy_template` 和 `promotion_diy_page` 等。会员、商品、交易、营销、支付五个模块的测试 SQL 合计涉及 40 张不同的表，当前正式初始化脚本均未创建。
2. **账号初始化风险**：`system_users` 中有 20 个预置用户，其中 12 个与常见公开示例密码匹配；其中包括初始管理员。未经账号清理与重置，不得公开管理入口。检查期间未登录成功、未对用户账户进行修改。
3. **正式数据库脚本来源**：`backend/sql/mysql/ruoyi-vue-pro.sql`、`backend/sql/mysql/quartz.sql`、`backend/sql/module/cps-all-in-one.sql` 不包含上述模块的完整正式表结构。所依据的框架把会员/商城初始化 SQL 单独提供；官方文档说明其部分模块 SQL 有独立使用授权限制，不能擅自把测试代码或第三方非授权 SQL 当成正式交付。
4. **尚未验收**：手机号/密码正向登录、实体扫码、真实联盟测试订单、真实结算/退款/提现、完整冻结资金规则、离机备份与恢复。

## 本轮可重复的兼容性实验

GitHub 增加：

- `script/ci/cps_schema_compatibility.py`
- `.github/workflows/cps-schema-compatibility.yml`

它们只使用此仓库 5 个模块的 `src/test/resources/sql/create_tables.sql` 文件，在 GitHub 托管的 MySQL 8 临时实例上做 SQL 语法验证、清理测试数据库并生成报告；任何情形**均不连接腾讯云业务库**。

腾讯云先行原型实验已证实：H2 测试 SQL 转换后 5 个模块可以在独立 MySQL 数据库中创建 **41** 张测试表（商品模块源文件另外包含 `product_comment`，其原始 `DROP TABLE ... (columns)` 明显不合法，需要测试专用转换）。成功后已删掉临时库；**没有导入 `cps`**。

这些数据只有“表可创建”意义，尚未验证索引、字段完整性、唯一约束、多租户规则、与 Java 实体的逐字段匹配，更没有验证真实商业资金流程。**禁止将该转换结果作为生产迁移使用。**

## 推荐后续顺序

1. 在 GitHub `develop` 为会员/商城/支付模块准备**合法来源、符合当前源码**的正式 MySQL 8 版本化迁移（需按实体、Mapper、业务 SQL、索引和约束审查）。
2. 使用独立新数据库进行导入、回滚演练和完整业务 E2E；通过后再对封闭云端测试库做有备份的增量迁移。
3. 安全处理默认后台账号与密码，并验证真实正向登录、禁用用户不能登录、跨租户拒绝。
4. 测试上游供应商测试订单、返利冻结/解冻、驿站分账、退款、重复事件、提现安全与账务守恒。
5. 配置独立位置的加密备份、恢复演练、HTTPS 和访问控制后，才允许对外试点。
6. 最后确认云端不依赖惠普，再清理惠普上的 AgenticCPS **专属**缓存与副本，不影响原有 n8n、Meme Alpha、FomoPulse。

## 保护规则

- 不在服务器构建大型 Java/H5；使用锁定提交的 CI 产物。
- 不修改既有真实财务流水、真实用户数据或执行全局 Docker 清理。
- 不禁用业务验证码，不开启模拟支付/模拟登录，不开放数据库端口。
- 无验收依据时，不声明“已完整上线”。
