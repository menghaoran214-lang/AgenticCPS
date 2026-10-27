# AgenticCPS 交易模块正式数据库迁移契约（预审）

> 2026-10-28，仅源代码与部署结构核对。**不是可运行的迁移脚本**，不得导入现有业务库。所有资金场景先做隔离测试。

## 现状与范围

当前腾讯云封闭测试库已有 **136张表**（121张基础+CPS表、6张会员表、9张商品表）；贸易模块的正式表仍未建立。运行时仅监听 `127.0.0.1:18080`。

从仓库 `backend/qiji-module-mall/qiji-module-trade/src/main/java/**/dal/dataobject/` 的 `@TableName` 枚举得到 **15个数据模型**。旧的 `src/test/resources/sql/create_tables.sql` 仅包含其中 **8张**，且使用 H2 建表语法和不完整的历史字段；不能替代完整的 MySQL 8 正式迁移。

### Java 实体 → 真实表契约

| 功能范围 | TableName（必须核对 Java DO、Mapper、业务 SQL） | DO 字段数（不含 BaseDO） | 测试 SQL 有无 |
| --- | --- | ---: | --- |
| 订单 | `trade_order` | 53 | 有 |
| 订单明细 | `trade_order_item` | 27 | 有 |
| 订单日志 | `trade_order_log` | 8 | **缺** |
| 售后 | `trade_after_sale` | 28 | 有 |
| 售后日志 | `trade_after_sale_log` | 8 | 有 |
| 购物车 | `trade_cart` | 6 | **缺** |
| 交易配置 | `trade_config` | 16 | **缺** |
| 分销用户 | `trade_brokerage_user` | 7 | 有 |
| 分销记录 | `trade_brokerage_record` | 13 | 有 |
| 分销提现 | `trade_brokerage_withdraw` | 19 | 有 |
| 配送公司 | `trade_delivery_express` | 6 | 有 |
| 配送模板 | `trade_delivery_express_template` | 4 | **缺** |
| 配送模板计费 | `trade_delivery_express_template_charge` | 8 | **缺** |
| 配送模板免运 | `trade_delivery_express_template_free` | 5 | **缺** |
| 驿站自提 | `trade_delivery_pick_up_store` | 13 | **缺** |

**重要**：`trade_brokerage_withdraw` 与 CPS 自有提现、佣金账本可能同时存在。不得根据类似名称混用余额、提现或分佣表；需验证每个消费端、后台端和定时任务真正引用哪一张表。

## 下一次迁移的必过检查

1. 从 Java DO 和当前 Mapper 生成字段映射表，包含继承的 `BaseDO` 审计字段、JSON TypeHandler、多租户拦截和 `@TableLogic`。
2. 校验金额单位是否为**分**、价格与佣金使用整型，禁止浮点货币和模糊默认值。
3. 索引需涵盖订单号、租户/用户/状态、订单项、商品、支付订单 ID、售后状态、分佣幂等业务单号、提现状态及自提码，并核实唯一性约束与软删除复用风险。
4. 在独立 MySQL 8 容器创建全部 15 表；验证每个 DO 字段、索引、唯一性和创建/查询/取消订单的基本 SQL 契约。
5. 对**真实备份恢复得到的临时库**增量重放，必须保证全部136张原表不变；检查0订单/0提现/0资金变动，才考虑增量部署到封闭测试库。
6. 管理账户种子密码未清理、离机备份未做、正向登录/真实 CPS 回调和分佣未通过时，继续禁止公网开放、支付提现和对外试运营。

## 已交付内容和限制

- 已完成：15个真实 DO 与8张历史测试表的缺口核对。
- 尚未完成：版本化 MySQL 8 交易迁移 SQL、CI 全链路测试、云端增量导入、真实订单资金验收。
- 不允许从第三方受授权限制的商业 SQL 中无授权复制建表语句。

