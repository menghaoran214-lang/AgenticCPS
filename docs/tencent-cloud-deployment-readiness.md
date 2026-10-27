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

1. **业务表不足**：`cps` 封闭测试库现有 **151 张表**，原有121张包含 CPS 订单、返利、资金流水等；2026-10-28已增量迁入6张正式会员核心表，包括 `member_user` 与 `member_level`。2026-10-28会员6张、商品9张、交易15张核心表均已追加到云端封闭测试库；仍缺少营销及支付模块完整表（如 `pay_wallet`、`promotion_coupon`、`promotion_diy_template`、`promotion_diy_page`）。营销23张和支付14张候选迁移已通过 GitHub CI，但**仅文件暂存，尚未导入业务库**。
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


## 2026-10-28 云端封闭测试库实际迁移记录

- 版本化来源：`backend/sql/migrations/V20261028_01__member_core_mysql8.sql`（Git Blob `d7da7aea0d4f328776d72ad904efe2b7b48c72cc`，提交 `1877850`）；GitHub 的独立 MySQL 8 CI **已通过**。
- 云端先在空临时库验证全部6张表、字段、唯一性、跨租户、软删除约束，随后在从定时备份恢复的临时库重放，表数 `121 → 127`，恢复库已清理。
- 对仅本机访问的云端 `cps` **封闭测试库**执行附加式迁移：执行前确认没有订单、提现、资金流水、OAuth 登录令牌或登录日志，且6张新表原本不存在；创建全量数据库备份 `pre-member-core-20261027T172006Z.sql.gz` 并校验后，只新增六表。迁移后的原表仍存在，新表为空，表数为127；执行清单在云端 `/home/cpsops/agenticcps/runtime/member-core-applied-sealed.json`。
- 对外监听仍仅 `127.0.0.1:18080`，健康检查通过，业务未上线。**这不是一次真实订单/登录/资金 E2E 验收**。
- 随后拟进行的合成账号登录测试被远程执行安全检查拦截，**没有执行**，不可报告为通过，也不可尝试绕过限制。
- 账号加固**仍未完成**：`system_users` 为20个预置账号，其中19个为启用状态。需要受控安全处理和验证后才能开放管理入口。现有备份只在同机，离机备份仍待做。

## 推荐后续顺序

1. 在 GitHub `develop` 为交易/营销/支付模块准备**合法来源、符合当前源码**的正式 MySQL 8 版本化迁移（需按实体、Mapper、业务 SQL、索引和约束审查）。
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

## 2026-10-28 商品9张表正式迁移实际记录

- GitHub迁移脚本：`backend/sql/migrations/V20261028_02__product_core_mysql8.sql`，Blob SHA `a2e63bc0ea316f996dcce59fcd47b57286f6a51d`，提交 `f68e803`。依据当前 Java Product*DO（9种实体）及 BaseDO 编制，而非直接引用存在旧字段和语法错误的 H2 测试SQL。
- GitHub Actions `CPS Product MySQL 8 Migration Probe (isolated)` [40550699365](https://github.com/menghaoran214-lang/AgenticCPS/actions/runs/40550699365) 已通过，涵盖9张表、模型字段、JSON、租户列、索引及 SPU/SKU 关联。
- 腾讯云对原始 Blob SHA 校验后，在独立临时库创建9张表，确认 JSON 类型、租户字段和当前模型字段，**临时库已清理**。
- 在仅监听 `127.0.0.1:18080` 的封闭测试库，迁移前核查已有127张表、新表全不存在、无订单/提现/账本/会员/登录记录。创建并校验完整备份 `pre-product-core-20261027T172848Z.sql.gz`，只执行新增商品9表，`127→136`，新表全空。服务器操作清单位于 `runtime/product-core-applied-sealed.json`。
- 操作后4容器健康检查通过；定时备份及巡检正常，数据库/Java仍无公网端口。**并未验证真实商品订单闭环，也未启用外部支付与提现**。
- 交易模块实际存在 **15 个 Java DO**，上一轮临时测试 SQL 仅8张表，不得直接当正式生产迁移；需按 Java 实体、Mapper 和业务查询制作完整版本。
- 管理员默认密码及预置账户仍未加固，正向会员登录验收仍未通过，离机备份仍未做，禁止公开上线。


## 2026-10-28 交易模块与营销/支付结构新进展

- 交易模块15表（`V20261028_03__trade_core_mysql8.sql`）：GitHub Actions [40552401983](https://github.com/menghaoran214-lang/AgenticCPS/actions/runs/40552401983) 通过；腾讯云在真实136表快照的独立数据库中增量复测 `136→151`，并清理了试验库。原业务库在备份后执行仅新增15表的封闭迁移；操作前确认零订单、零提现、零 CPS 资金流水；备份 `pre-trade-core-20261027T174011Z.sql.gz` 已生成并校验。云端操作清单 `runtime/trade-core-applied-sealed.json`。
- 根据**37份实际 Java DO 源码**（营销23、支付14）生成并提交候选迁移 `V20261028_04__promotion_core_mysql8.sql` 和 `V20261028_05__pay_core_mysql8.sql`。各字段已经静态对照源码；优惠券等的 LongList/StringList handler 对应字符串，JacksonTypeHandler 对应 JSON。
- 独立 GitHub Actions [40555205923](https://github.com/menghaoran214-lang/AgenticCPS/actions/runs/40555205923) **成功**：实际在一次性 MySQL 8 创建37表，核验37个 Java DO 字段、租户列、主键、价格整数分、支付应用/渠道默认禁用、支付订单/退款/转账/钱包重复业务键约束，并移除测试数据库。
- 新版本化候选 SQL 已下载到云端 `runtime/schema-candidates/` 并逐份通过 Git Blob SHA 校验：营销 `1bd144399bbad85c08f1857c2bd578e06cd843cf`，支付 `bdf471450c0212a765a80d7b786626a49a73bece`。**没有在云端运行或导入这37张表**：云端实际业务库仍是151表。
- **限制**：PayChannel 客户端配置包括支付密钥信息，当前候选是 longtext 类型映射；这**不能替代静态加密、密钥轮换、访问控制和生产通道验收**。PayWallet 与 CPS 自有返利账户、流水分别管理，禁止混账。数据库索引语法和静态字段验证通过，**不代表全部 Mapper、Java运行时、营销结算或真实支付已通过**。
- 下一步先完成合规来源与业务 SQL 字段级审查、云端备份恢复的只读预检，再单独核准封闭测试库的增量迁移。预置后台账号仍存在默认密码风险；离机备份、正向登录、订单归因、资金守恒、退款及提现验收均未完成，**继续禁止开放公网**。


## 2026-10-28 五大模块联合 MySQL 8 CI（新增）

- 集成脚本：`script/ci/cps_all_module_schema_probe.py`；自动化工作流：`.github/workflows/cps-all-module-schema-probe.yml`。
- GitHub Actions [40557244238](https://github.com/menghaoran214-lang/AgenticCPS/actions/runs/40557244238) 在一次性 MySQL 8 **成功**，按顺序执行会员6、商品9、交易15、营销23、支付14张表的版本化迁移，总计67张。
- 检查通过：新增表数量与去重、63张表的租户字段、4张会员 `BaseDO` 例外（`member_address`、`member_level`、`member_group`、`member_tag`）、67张表的基础审计字段、整数分金额、支付应用/渠道默认禁用、会员→交易、商品 SPU→SKU 的模拟关联，以及同租户/用户类型钱包唯一性。全部检查成功，临时数据库已删除，无支付和转账动作。
- 上述4张 `BaseDO` 表不带 `tenant_id` 与当前 Java 继承关系一致，**不应为了满足泛化测试强行向已部署表加列**。它们的关联查询是否受身份授权及跨租户访问控制仍应开展接口级验收。
- 支付/返利账本风险审查详见 `docs/cps-payment-ledger-safety-review.md`：支付钱包与 CPS 返利账本分离，商户订单与退款、转账、钱包流水的唯一键不替代完整业务幂等；支付渠道配置须单独核验静态加密及访问控制。
- 本轮曾尝试发起云端当前151表备份恢复 + 营销/支付37表联合迁移演练，但远程执行被安全检查阻止，**没有进行该云端演练，也没有修改线上封闭业务库**。不可将 GitHub CI 成功写成该演练成功。
- 最新云端只读复查：原数据库仍为151张表，四容器健康，网页只监听 `127.0.0.1:18080`，自动备份可用。营销/支付 SQL 已经暂存但未导入业务库。
- 下一阶段仍需：受控完成151→188张表的临时恢复兼容验证，再决定封闭库迁移；处理预置账户及管理员口令、离机加密备份、正向登录、订单归因、退款和资金守恒 E2E。**生产上线门禁仍关闭。**


## 2026-10-28 真实仓库全量初始化演练 + 第6份租户修正

- 已把仓库的 `backend/sql/mysql/ruoyi-vue-pro.sql`、`backend/sql/mysql/quartz.sql`、`backend/sql/module/cps-all-in-one.sql` **实际依次导入 GitHub Actions 一次性 MySQL 8 数据库**，得到 **121张基础表**，与腾讯云此前的初始表数量一致。
- 之后依次执行会员6、商品9、交易15、营销23和支付14张表，共 **188张表**，原有121张表和数据库初始化记录保留。成功记录：[40558946043](https://github.com/menghaoran214-lang/AgenticCPS/actions/runs/40558946043)。这不是当前腾讯云151表数据库的备份恢复演练。
- 代码审查发现：`TenantDatabaseInterceptor.computeIgnoreTable` 对没有 `@TenantIgnore` 的 `BaseDO` 实体仍会应用租户 SQL 拦截；`application.yaml` 没有将4张会员基础表列入 `ignore-tables`，因此先前仅因其继承 `BaseDO` 就认定可以省去 `tenant_id` **是错误的**。
- 已新增版本化**增量**迁移 `backend/sql/migrations/V20261028_06__member_tenant_interceptor_alignment.sql`，仅为 `member_address`、`member_tag`、`member_level`、`member_group` 补充 `tenant_id` 和对应租户索引；不会增加表数。若表中已有真实数据，必须先核对每行归属并阻断迁移，不能直接把旧记录都赋给默认租户1。
- 修正后重新运行两套独立测试均**通过**：五模块联合 CI [40559218683](https://github.com/menghaoran214-lang/AgenticCPS/actions/runs/40559218683) 检查67张表均具租户字段；仓库**全量121表初始化 + 67表迁移 + V06兼容修正** CI [40559231323](https://github.com/menghaoran214-lang/AgenticCPS/actions/runs/40559231323) 检查原有121张表保留、188张总表、原有数据不变、67张新增表租户字段齐全、支付渠道默认禁用。两次 CI 均已删除临时数据库。
- **云端真实状态仍为151张表**，上面 V06 及营销、支付37表均未导入。未在腾讯云进行151→188表的备份重放，远程执行曾遇到安全拦截，不得绕过，也不得声称真实支付或登录通过。
- 上线阻碍仍包括：20个预置后台账号及默认口令风险、离机加密备份未完成、正向认证与真实联动订单/返利冻结/退款/提现 E2E 未通过。公网不得开放。


## 2026-10-28 生产认证与第三方密钥风险（新增）

- **固定短信验证码的生产修复**：公共 `application.yaml` 原把 `qiji.sms-code.begin-code` 和 `end-code` 设成同一测试值；`SmsCodeServiceImpl.createSmsCode` 使用这两项直接生成 OTP，生产配置此前未覆盖它们。现已在 **GitHub develop 的 `application-prod.yaml`** 显式覆盖为 **100000–999999 随机六位数**，且生产 `mock-enable=false`、`captcha.enable=true`。
- 自动检查 `script/ci/cps_production_auth_guard.py` 和工作流 `.github/workflows/cps-production-auth-guard.yml` 已通过：[40559878562](https://github.com/menghaoran214-lang/AgenticCPS/actions/runs/40559878562)。这只是**配置静态审查**，未在运行中的腾讯云 Java 容器执行短信注册或正向登录。
- **版本隔离**：腾讯云当前 JAR 仍来自较早的 `c3ba73f` 构建，尚未包含本次短信配置修复和 V06 租户增量迁移。必须使用安全审查完成后的新构建、校验真实产物 SHA，经过封闭环境的启动与 OTP 行为验证，才能考虑注册或公网访问。不能因为 GitHub CI 通过就推断线上风险已解决。
- **第三方配置凭据**：源码中的 YAML 仍包含看起来像第三方服务密钥/测试号凭据的字面值（包括微信、社交登录、地图和配送相关项），无法仅凭源码证明是否为真实有效凭据。必须先由拥有者核实并在相应服务端**轮换/吊销实际有效密钥**，再将密钥改为运行环境或密钥管理服务注入，禁止在文档、日志、CI Artifacts、群聊或公开网页复制这些值；必要时清理 Git 历史中的敏感值。
- **未完成安全上线门禁**：原有20个预置后台账号及示例密码风险、离机加密备份缺失、4张会员表的 V06 修正未部署、营销支付37表未导入当前封闭测试库、真实短信供应商/用户认证/联盟订单/退款冲回/资金守恒等均未通过实际验收。
