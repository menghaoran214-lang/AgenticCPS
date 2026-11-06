# 旺财狗（wcg.ymette.com）与 AgenticCPS 对比及能力移植记录

> 本文只记录**有来源**的结论，分三类标注：
> - **观测**：直接来自抓取到的页面正文或仓库源码。
> - **推断**：由观测线索推导，未取得安装包/运行态证据。
> - **未知**：当前证据无法确定。
>
> 未对旺财狗安装包做任何逆向，因此本文不声称还原了其内部实现。

## 一、旺财狗的技术路径

### 1.1 你给的那个网址是什么（观测）

`https://wcg.ymette.com/robot/agree.html` 的 HTML 中：

```html
<meta name="generator" content="VitePress v1.6.3">
<script type="module" src="/assets/app.-ahAeGO-.js"></script>
```

- 站点由 **VitePress 1.6.3**（Vue 3 + Vite 静态站点生成器）构建，是**官方使用文档站**，不是产品后台。
- 全站 112 个有效页面，路由由 `sitemap.xml` 与页面实测确认；`/xxx` 形式返回 404，真实路径是 `/xxx.html` 或 `/xxx/index.html`。
- 因此"拆这个网址"能拿到的只有**文档站的技术栈**和**产品功能说明书**，拿不到产品服务端代码。

### 1.2 产品本体怎么部署（观测，文档原文）

| 项 | 内容 |
| --- | --- |
| Windows | 下载"旺财狗安装程序_***.exe"，安装后桌面出现程序图标，右下角托盘有狗头图标 |
| Linux | 解压后 `./RebateDog.Server` 启动 |
| Docker | 镜像 `zhaoyangguang/rebatedog`，挂载 `/app/data` |
| Linux 一键安装 | `bash <(curl -sSL https://xiyi.ymette.com/rebateDog/install.sh)`，默认 `/usr/local/rebateDog` |
| 管理端口 | **15888**（浏览器访问 `IP:15888`） |
| WebSocket 端口 | **15999**（机器人插件长连接 / 回调） |
| 数据库 | SQLite，`data/data.db`；自动备份到 `data/data_backup/*.zip`，默认保留 5 份 |

### 1.3 技术栈（推断）

依据（观测）：`RebateDog.Server` 可执行文件、`appsettings.json` 配置、`wwwroot/` 静态目录、Windows 托盘宿主、H5 端 `RebateDogH5.exe` 与 `wwwroot/static/config/config.js`、nginx docker 托管 `wwwroot`。

推断：主程序是 **.NET / ASP.NET Core 自托管服务**（自带 `appsettings.json` + `wwwroot` + 可执行入口 + Windows 托盘壳），H5 端是同一套静态资源 + 独立可执行宿主。

**需要注意**：文档里确实出现了 "Blazor Server"，但那是 **WanQ（旺Q，QQ 官方机器人独立服务端）** 的技术说明，不能直接算到旺财狗主程序头上。

**未知**：具体 .NET 版本、ORM、是否用了 Blazor 做后台、SQLite 之外是否有可选数据库（文档只在第三方 QiQiBot/TauBot 教程里提到 MySQL，那是框架侧要求）。

### 1.4 机器人接入层的设计（观测）

这是旺财狗真正的护城河，也是最不该照搬的部分：

1. **插件市场**：后台"插件管理 → 插件市场"搜索 → 点击下载自动安装 → 启用 → 编辑 `config.ini`。
2. **配置是 ini 段**，例如 GeWe：
   ```ini
   [Token]
   token=1111
   [Server]
   server_url=http://<ip>:<port>/v2/api
   ```
   飞鱼是 `[Send]` 段（`only_show_command`、`block_words` 等）。
3. **统一回调**：插件回调地址形如
   `ws://<host>:15999/notify/pluginreceive?plid=<插件ID>`
   旺财狗按 URL 参数把消息派发给对应插件。
4. **云中继**：官方中转服务器，用"对接码"替代内网穿透（家里没公网 IP 也能接）。
5. **协议生态**：微信（ClawBot 官方 / GeWe、QiQiBot、TauBot、酷V 托管 / 鲁班 RPA / 木牛流马窗体模拟）、QQ（WanQ 官方、LLOneBot-NapCat OneBot v11、QYBot 企点）、企业微信、微信客服、公众号（直连 / 转发）、伟大复兴 QPigeon、闲鱼飞鱼、Telegram、抖音（未上线）。

### 1.5 授权与商业模型（观测）

- 授权做在**账号权限**上，不区分程序分发："所有的功能都是在账号权限上做限制"，"旺财不抽佣不扣佣"。
- 7 类 VIP：社群发单 / 用户返利 / 朋友圈 / 公众号 / 公众号转发 / 闲鱼 / 企业微信（含微信客服）。
- 免费版限制举例：社群发单限 10 群（带广告）、用户返利月限 30 单、消息广播限 1 个/次、商品关键词订阅限 1 个。
- 价格：社群发单 每 10 群/年 50 元、用户返利 88 元/年、朋友圈 50 元/号/年、公众号 80 元/年、公众号转发 25 元/年、闲鱼 98 元/月、企业微信 88 元/年。

### 1.6 核心业务链路（观测，多篇文档交叉一致）

```
采集（自己素材群 / 旺财云频道）
  → 素材/线报（文案有效期默认 7 天）
  → 跟推（收单群订阅采集群，间隔/免打扰/黑白名单/文案整理）
  → 转链（联盟账号 + 推广位 PID；淘宝渠道关系 rid 决定归属）
  → 订单归因（PID / rid）
  → 返利计算（全局 / 阶梯 / 单个顾客个性化）
  → 冻结与结算（结算日、最低提现积分、订单最低提现周期 30 天）
  → 打款（支付宝企业支付自动 / 导出 xls 批量手动）
```

另外两条旁支：**场所主（代理分销）** 与 **AI 店员（RAG 知识库 + 14 个 Function Calling 工具）**。

## 二、AgenticCPS 现状（源码观测）

| 维度 | 事实 |
| --- | --- |
| 栈 | Java 21 / Spring Boot 4.1.1 / Spring AI 2.0.1 / MyBatis-Plus |
| 模块 | `qiji-module-cps`（核心）+ `-ai`、`-member`、`-system`、`-mp`、`-mall`、`-pay` |
| 数据表 | 60 张（50 `cps_*` + 10 `cpx_*`） |
| MCP Tool | 25 个（19 `cps_*` + 6 `cpx_*`），`/mcp/cps` |
| Admin API | 32 个 Controller / 204 个端点 |
| 定时任务 | 13 个 Quartz `JobHandler` |

关键缺口：

- **没有 IM 机器人接入层**：无微信/QQ/Telegram/企微出站能力。`qiji-module-mp` 只有公众号（模板消息、客服消息、自动回复），`qiji-module-system` 有短信/邮件/站内信，`qiji-spring-boot-starter-websocket` 有 WS 通道。
- **没有群发/投放调度**：`CPX` 有 `cpx_task / cpx_offer / cpx_material / cpx_tracking_link / cpx_event / cpx_conversion / cpx_settlement_record`，但 `cpx_task` 只有 `start_time/end_time` **有效期**，没有调度器、没有渠道分发、没有发送记录；`cpx_offer` / `cpx_material` 连 Service 都没有。
- **已有的、比旺财狗更强的部分**：`cps_promoter_relation`（三级分销）+ `cps_split_rule`（多级分佣规则）已经覆盖并超过旺财狗"场所主"；`cps_marketing_short_link` + `cps_marketing_click_event` 已是带归因摘要（哈希）的短链点击层；提现/转账链路完整。

## 三、功能对比矩阵

| 旺财狗能力 | CPS 现状 | 结论 |
| --- | --- | --- |
| 机器人协议插件生态（GeWe/TauBot/LLOneBot/WanQ…） | 无 | **不移植**：依赖非官方协议与真实框架，合规与稳定性风险高 |
| 群发 / 定时群发 / 窗口群发 / 消息队列 | **空白** | **已移植**（本次） |
| 免打扰时段、每日频控、幂等不重复触达 | 无 | **已移植**（本次） |
| 投放记录追溯 | 无 | **已移植**（本次） |
| 自动通过好友 / 自动拉群 / 踢人 / 欢迎语 | 无 | 不移植：依赖 IM 好友关系，CPS 无 IM |
| 采集（自己素材群 / 云频道） | 有 vendor 商品采集，无社群采集 | 不移植：需 IM 通道 |
| 朋友圈发圈 / 自动点赞 | 无 | 不移植：RPA/协议模拟 |
| 关键词订阅 / "我要买" 导购指令 | 无 | **待办**：可做成 `cps_keyword_subscription` + MCP 工具（旺财狗同名工具 3 个） |
| 阶梯返利 / 单顾客个性化返利比例 | 有 6 级优先级配置，无阶梯 | **待办**：加 `cps_rebate_tier_rule`，改结算计算 |
| 返利文案模板 `{0}`~`{6}`、`/* */` 无券隐藏 | 无模板层 | **待办**：加模板表 + 渲染服务 |
| 淘口令模板 / 短链服务商 / 中间页模板 | 有 `taoCommand` 字段与短链，无模板 | **待办** |
| 场所主（代理分销分成） | **已有且更强** | 无需移植 |
| 积分体系（10 积分 = 1 元） | `member_point_record` 存在但未与返利打通 | 不做：CPS 用返利直结，避免双账本 |
| 支付宝自动打款 / 批量导出 | 有 withdraw + transfer 链路 | 无需移植 |
| 云中继 / 内网穿透 | 无 | 不移植：产品形态不同 |
| 云频道选品 | 有 vendor 选品 + 公开 API | 无需移植 |
| AI 店员（RAG + 工具调用 + 相关性守门 + 限流） | `qiji-module-ai` 有 RAG、角色、技能、MCP 注册，但**没有被 IM 消息触发**的闭环 | **待办**：缺的是消息入口，不是 AI 能力 |

## 四、本次移植：CPS 投放任务（群发 / 定时群发）

### 4.1 设计取舍

旺财狗的群发是"消息管道 + 队列 + 频控"，CPS 侧对应的正确落点是 **CPX 投放能力的调度层**，而不是再做一套消息系统。因此：

- 任务只描述"什么时候、发给谁、通过哪个渠道、发什么"，**不内置任何 IM 协议**。
- 渠道是扩展点：`CpsPublishChannel` 接口 + Spring Bean 自动注册，与 `CpsPlatformClientFactory` 的插件风格一致。
- 内置三个渠道：`SMS`（短信）、`NOTIFY`（站内信）、`CLIPBOARD`（文案看板，运营手动复制到社群）。
- 只支持 `MANUAL` 内容来源。`SELECTION_THEME / REBATE_ACTIVITY / CPX_TASK` 需要真实的内容抓取逻辑，未实现前**直接拒绝**，避免"看起来支持实际不生效"。

### 4.2 新增文件

后端：

- `dal/dataobject/publish/CpsPublishTaskDO.java`、`CpsPublishLogDO.java`
- `dal/mysql/publish/CpsPublishTaskMapper.java`、`CpsPublishLogMapper.java`
- `service/publish/CpsPublishConstants.java`、`CpsPublishTaskService.java`、`CpsPublishTaskServiceImpl.java`
- `service/publish/channel/CpsPublishChannel.java`、`CpsPublishSendContext.java`、`CpsPublishSendResult.java`
- `service/publish/channel/CpsSmsPublishChannel.java`、`CpsNotifyPublishChannel.java`、`CpsClipboardPublishChannel.java`
- `controller/admin/publish/CpsPublishTaskController.java` + 6 个 VO
- `job/CpsPublishDispatchJob.java`
- `mcp/tool/CpsListPublishTasksToolFunction.java`、`CpsSchedulePublishTaskToolFunction.java`
- 测试：`CpsPublishTaskServiceImplTest`、`CpsPublishMcpToolFunctionTest`

前端：

- `frontend/admin-vue3/src/api/cps/publishTask.ts`
- `frontend/admin-vue3/src/views/cps/publish/task/index.vue`

SQL（`cps-update.sql` 末尾追加，并同步 `cps-all-in-one.sql`，版本 `v2.1.7`）：

- 表 `cps_publish_task`、`cps_publish_log`
- 菜单 6310–6317（`cps:publish-task:{query,create,update,delete,publish,offline,dispatch}`）
- `infra_job`：`cpsPublishDispatchJob`，`0 * * * * ?`

### 4.3 行为约束（已实现并有测试）

- **手工专属任务不会被调度**：`cron_expression` 为空 ⇒ `next_dispatch_time` 为 null ⇒ 扫描 SQL 要求 `next_dispatch_time IS NOT NULL`。
- **非法 Cron 直接拒绝**：不再静默降级成"手工任务"。
- **状态与窗口**：只有 `ONLINE` 且落在 `start_time`~`end_time` 内才会发送；`DRAFT/OFFLINE` 即便手工触发也不发。
- **免打扰**：`quiet_start == quiet_end` 视为未配置；跨零点区间按 `[start, end)` 处理。
- **频控**：先查当日次数再占位，避免"第一次就因上限被拒"的 off-by-one；只统计已受理/已成功/预览态。
- **幂等**：`idempotent_key = taskId:memberId:投放分钟`，表级唯一索引，冲突即跳过。
- **并发**：按任务加 Redisson 锁，避免多实例重复投放。
- **时区**：统一 `Clock.system(Asia/Shanghai)`。
- **收件人校验**：必填、≤1000、必须为正整数，非法直接报错而不是静默丢弃。
- **链接安全**：`link_template` 只允许 http(s)，且不允许出现 `{...}` 之外的模板注入。

### 4.4 语义诚实性

- 短信/站内信状态是**`ACCEPTED`（渠道已受理）**，不是"已送达"：现有短信 API 只返回日志 ID，没有回执存储，代码与前端都不宣称送达。
- `CLIPBOARD` 状态是 **`PREVIEW`（文案已生成）**，因为内容并没有真正投递出去。

### 4.5 验证

```
mvn -pl qiji-module-cps/qiji-module-cps-biz -am test \
  -Dtest=CpsPublishTaskServiceImplTest,CpsPublishMcpToolFunctionTest,CpsMcpToolConfigurationTest,CpsMcpToolAuditSupportTest,CpsMcpAuthorizationServiceTest,CpsMcpIdentityVerifierTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：`Tests run: 35, Failures: 0, Errors: 0`；`mvn -q -DskipTests compile` 通过。

### 4.6 未验证 / 遗留

- **前端未构建验证**：本机没有 `pnpm`，只做了代码编写与结构对齐，未跑 `pnpm ts:check` / `pnpm build:prod`。
- **SQL 未在真实 MySQL 上执行**：只保证语法结构与既有脚本一致，未做实际导入验证。
- **无短信回执**：需要加列（如 `delivery_status`）与回执回调，属于跨模块改动，本次未做。
- **本期只支持 `MANUAL` 内容来源**：接选品主题/活动中心需要真实的内容抽取，是下一步。

## 五、建议的后续顺序

1. 关键词订阅（旺财狗同名 3 个工具，CPS 侧一行表 + 一个 Job 即可，价值/成本比最高）。
2. 返利文案模板 + 淘口令/短链/中间页模板（纯模板层，不改资金逻辑）。
3. 阶梯返利（触碰结算，必须补契约测试）。
4. 投放任务接 `SELECTION_THEME` 内容来源，并补短信回执。
5. 若确实需要 IM 触达：优先走公众号客服消息 / 小程序订阅消息（CPS 已有 `qiji-module-mp` 与 `SocialClientApi`），而不是接非官方协议。
