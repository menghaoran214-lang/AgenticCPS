# 腾讯云只读迁移预检（2026-10-28）

本轮目标由用户明确指定为腾讯云 VM-0-8-ubuntu，覆盖 AGENTS.md 中较早的 HP 目标记录。
本轮未修改 HP，不变更当前业务库、容器、监听、备份原件或真实资金。
云端审查工作区：/home/cpsops/agenticcps/review-20261028，初始 develop 干净，基线 cac065cea96f556b1a465114ab91083a61a85cd5。

## 已交付

- script/ci/cps_cloud_readonly_preflight.py：仅允许固定 SQL 元数据/聚合查询，不接受任意 SQL；锁定主机、账号、容器和 cps 数据库。
- 以现有六份迁移为依据核对 67 表字段、普通/唯一索引的列顺序与唯一性；不执行任何迁移。
- 对关键业务表只读统计，核对初始化账号总数、服务名、端口监听与运行清单提交。
- 流式核对最新定时备份 SHA-256 和 gzip CRC；不展示备份正文，不执行恢复。
- 7项单元测试覆盖任意 SQL 拒绝、缺表/缺列区分、索引解析、未知记录数关闭门禁、损坏备份拒绝。
- .github/workflows/cps-migration-preflight.yml 在 GitHub 一次性 MySQL 8 重跑原有全量初始化、V01–V06、营销支付实体字段和重复键检查。该工作流不执行云端脚本 main，也不连接腾讯云。

## 云端实测

2026-10-28（上海时间）：MySQL 8.0.46，cps 151表，营销23/支付14表未部署；
member_address、member_group、member_level、member_tag 各缺 tenant_id 和 V06租户索引。
cps_order、cps_withdraw、cps_rebate_asset_ledger 以及上述四表和 member_user 的 COUNT(*) 均为0。
system_users 20个，19个启用；没有读取密码或尝试登录。
四个项目容器运行；18080仅监听127.0.0.1，3306/6379/48080未发布到主机。
运行清单记录提交仍为 c3ba73fac5f023536c6e2b08325237cdc13f75db，不能推断已部署新认证配置。
定时备份 scheduled-20261027T155759Z.sql.gz 的SHA-256及gzip完整性通过；
这不是恢复成功、最新备份充分性或离机持久化的证明。
原始聚合报告留在云端 logs/readonly-preflight-20261028.json，不提交数据库内容。

运行（只适用于已核对的腾讯云 cpsops 环境）：

    cd /home/cpsops/agenticcps/review-20261028
    python3 -m unittest discover -s script/ci -p test_cloud_readonly_preflight.py -v
    python3 script/ci/cps_cloud_readonly_preflight.py

退出码2表示门禁关闭，是本次实测的预期结果，不代表数据库已迁移。
源码与部署版本比较依据 runtime/artifact-manifest.json，未独立证明运行JAR字节来源。
脚本不验证密码安全、Java Mapper实际执行、列类型/默认值、业务幂等或完整金融闭环。
单独CI中的模型/DDL测试也不等价于真实Mapper/TypeHandler、跨租户认证和资金守恒测试。

## 必须保留的阻塞项

1. 既往云端151→188备份恢复演练和合成账号登录遭安全检查阻止。本轮不重试、不更换工具绕过；云端恢复与正向登录仍未验证。
2. V06与37表迁移未应用；不得只依据CI通过就改业务库。V06仍需逐行租户归属门禁，不能把未知旧数据归属租户1。
3. 管理员账号加固、有效第三方凭据核实/轮换、新JAR构建及封闭启动验证尚未完成。
4. 离机加密备份目标尚未指定，未验证异地恢复。
5. Mapper复核中 pay_wallet_transaction 的唯一键包含 wallet_id，而 selectByBiz 仅按 biz_id/biz_type 查询；需验证同租户不同钱包的业务键作用域，不能宣称完整幂等。
6. member_user 的(tenant_id,mobile,deleted)唯一键只支持一个已删除历史记录；重复注销/重注册生命周期需要回归和增量设计，不能把一次软删除测试视为完整覆盖。
7. 不执行真实支付、提现、联盟下单或外部短信。后续资金/订单回归必须继续使用mock和一次性数据库。

用户后续需要提供的内容：离机加密备份目的地；有效外部密钥的拥有者轮换结果；
账号初始化保留策略。没有必要向聊天发送密码、API密钥或备份正文。
当前全部上线与真实资金门禁保持关闭。
