# AgenticCPS 支付与返利账本安全复核（2026-10-28）

> 范围：代码与 MySQL 8 迁移文件的静态核查、GitHub Actions 临时数据库验证。不构成真实资金业务验收，不允许直接开放公网或支付。

## 已验证的安全规则

1. **状态码的真实含义**：`backend/qiji-framework/qiji-common/src/main/java/com/qiji/cps/framework/common/enums/CommonStatusEnum.java` 明确 `ENABLE=0`，`DISABLE=1`。候选迁移 `V20261028_05__pay_core_mysql8.sql` 的 `pay_app.status`、`pay_channel.status` 默认值为 `1`，与 `PayAppServiceImpl.validatePayApp` / `PayChannelServiceImpl.validPayChannel` 的关闭逻辑一致。此规则已被独立 MySQL 8 CI 检查。
2. **账本隔离**：`pay_wallet`、`pay_wallet_transaction` 由 `qiji-module-pay` 的 Wallet Service 管理；CPS 自有账户和资金流水属于另外的 `cps_rebate_*` 表。表相互独立；禁止通过数据库迁移合并余额或自动迁移资金。
3. **钱包 CAS 更新**：`PayWalletMapper.updateWhenConsumption`、`freezePrice`、`unFreezePrice` 对可用余额/冻结额有比较条件；`PayWalletServiceImpl` 有 Redis 锁及交易注解。这是已有代码机制，不代表已经完成并发一致性实测。
4. **数据库幂等键**：候选 `pay_order` 的 `(tenant_id,app_id,merchant_order_id)`、`pay_refund` 的 `(tenant_id,app_id,merchant_refund_id)`、`pay_transfer` 的 `(tenant_id,app_id,merchant_transfer_id)`、`pay_wallet_transaction` 的 `(tenant_id,wallet_id,biz_type,biz_id)` 设置唯一约束，并在隔离 CI 中测试重复键拒绝。

## 上线前高优先级待验收问题

- **支付渠道密钥存储**：`PayChannelDO.config` 通过 `PayClientConfigTypeHandler` 落在数据库的 `longtext`，不能仅凭字段类型保证静态加密或防止后台接口泄露。需审查加密存储、密钥轮换、读取权限、日志脱敏和备份加密；测试时不能使用真实支付凭据。
- **钱包余额计算与幂等性**：`PayWalletMapper` 有多处 `setSql` 余额增减；核查调用链对 `price > 0`、整数溢出、重复请求、并发退款、资金守恒与跨租户身份的控制。特别需审查 `PayWalletTransactionMapper.selectByBiz` 的查询条件与候选唯一索引作用域是否一致。**未确认存在可利用漏洞；仅标记为待测场景。**
- **商户单号为空的约束**：MySQL 唯一索引允许多个含 NULL 的键组合；必须从接口层禁止空商户订单号、退款号或转账号，不能把“唯一索引”理解为完整业务幂等。
- **默认后台账号**：腾讯云封闭测试库仍存在20个预置账号，其中19个启用；上一轮曾识别到示例密码风险。账号加固前不允许公开后台。
- **离机容灾**：每日备份现存于同一云主机，不等于离机、加密备份。必须完成恢复演练、权限控制及异地持久化。
- **业务端到端**：真实登录、CPS 联盟订单归因、返利冻结/解冻、驿站分账、退款冲回、支付/提现异常及多次回调仍未完成验收。数据库 CI 不能替代它们。

## 版本化数据库交付与状态

- 已在腾讯云**封闭测试库**追加：会员6张、商品9张、交易15张；现有业务库 **151 张表**。
- 营销23张、支付14张：候选迁移和 GitHub MySQL 8 独立测试成功；候选 SQL 已在服务器受限目录暂存并校验 Blob SHA，**尚未导入腾讯云当前151张业务库**。
- GitHub 工作流 `cps-all-module-schema-probe.yml` 将进一步组合执行全部五份候选迁移（合计67张表），验证组合兼容性。它使用 GitHub Actions 自带的临时 MySQL，**不得连接到腾讯云生产/测试业务库**。
- 当前禁止使用真实商户资金、真实支付/提现凭据；上线门禁保持失败时，不得切换公网入口。

## 后续执行顺序

先确认五模块整合 CI 成功，再验证各 Mapper/TypeHandler 实际映射和空号约束；补支付资金并发及幂等模拟测试、管理员账号安全、离机备份，最后才进入真实联盟单和受控小流量试点。若远程写操作受安全策略拦截，保留已通过的只读核查及 GitHub 隔离测试，不绕过限制。
