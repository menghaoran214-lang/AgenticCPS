# AgenticCPS Spring AI 开发指导

> 本文是 AgenticCPS 开发 Spring AI 功能的仓库级指导手册。内容以 Spring AI 2.0.0-M3 中文开发文档、Spring AI 2.0.1 官方文档和仓库现状为依据，并吸收腾讯云文章《Spring AI 2.0 正式发布：Java 程序员必须关注的 8 个升级清单》的工程启示。
>
> **当前基线（2026-09-18）**：`backend/qiji-module-ai/pom.xml` 使用 Spring AI `2.0.1`，根 POM 使用 Spring Boot `4.1.1`、Java `17`。新增代码按 2.0.1 API 编写；2.0.0-M3 文档只作为 2.0 API 设计参考，不能替代当前版本的 Javadoc、依赖源码和编译结果。
>
> **文档状态**：2.0.0-M3 页面明确标记为开发中，页面示例仍出现 Spring Boot 3.4/3.5；当前官方 2.0.1 文档已改为支持 Spring Boot 4.0.x/4.1.x。文章中的“Spring Boot 4 + Jackson 3”等结论可作为方向判断，但升级决策必须以当前官方版本和本仓库构建结果为准。

## 0. 版本与资料优先级

遇到版本差异时按以下顺序判断：

1. `backend/qiji-module-ai/pom.xml`、`backend/pom.xml` 和 Maven 的实际依赖树；
2. 当前 Spring AI 官方版本文档/Javadoc（当前为 2.0.1）；
3. 2.0.0-M3 中文文档，用于理解 API 设计和迁移背景；
4. 腾讯云文章，用于理解版本方向，不作为 API、配置或依赖的唯一依据。

任何新 API、starter、配置键或包名，都必须用 `mvn dependency:tree`、IDE/Javadoc 或模块编译确认，不能只凭文章或旧代码记忆。

### 0.1 腾讯云文章的工程结论

文章的核心判断是：Spring AI 2.0 的重点从“调用模型”转向“构建可治理的 Agent”。对本仓库的落地要求如下：

| 文章观点 | 本仓库采用的要求 | 版本核对结论 |
| --- | --- | --- |
| Spring Boot 4、Jackson 3、JSpecify | 新模块以 Boot 4、当前 JSON 运行时和明确的空值边界为前提；升级旧模块先做依赖树和编译验证 | Boot 4 已由当前基线确认；Jackson/JSpecify 的具体落点仍以 2.0.1 依赖和源码为准 |
| Options Builder、不可变配置 | 统一在 factory/builder 构造 options，请求间不共享可变状态 | 与 2.0 文档一致 |
| 工具调用循环进入 Advisor 链 | 使用 `ToolCallingAdvisor` 的统一循环；需要审批/中断时显式用 `ToolCallingManager` | 与 2.0.1 官方工具文档一致 |
| 海量工具渐进式暴露 | 工具数量增长时评估 `ToolSearchToolCallingAdvisor`，按角色和会话按需暴露 | 2.0.1 官方文档有对应能力，使用前核对 starter/配置 |
| 结构化输出自修复 | DTO/schema + Bean Validation + 有限重试；不能只依赖模型“保证 JSON” | 文章提到的 `StructuredOutputValidationAdvisor` 必须先在 2.0.1 Javadoc/依赖中确认，未确认前不得直接引用 |
| MCP 标准化和注解驱动 | 通过 Spring AI MCP starter，按权限暴露工具/资源/提示，保留身份、审计和超时 | 2.0 文档确认注解和传输迁移；SDK/starter 坐标以当前 POM 为准 |
| 社区记忆、工具和 Agent 扩展 | 新增第三方组件必须做版本、许可证、维护度和安全评估 | 社区项目不是默认依赖 |

## 1. 技术定位与边界

Spring AI 为模型、提示词、工具调用、向量检索和可观测性提供统一抽象，核心接口包括：

| 能力 | 主要接口/类型 | 在本仓库的落点 |
| --- | --- | --- |
| 对话模型 | `ChatModel`、`StreamingChatModel`、`ChatResponse` | `AiModelFactory`、`AiChatMessageServiceImpl` |
| 流畅调用 | `ChatClient`、`ChatClient.Builder` | 新功能优先采用；旧链路仍直接构造 `Prompt` |
| 提示词/消息 | `Prompt`、`SystemMessage`、`UserMessage`、`AssistantMessage` | `AiUtils.buildMessage`、聊天历史 |
| 向量与嵌入 | `EmbeddingModel`、`VectorStore`、`Document`、`SearchRequest` | 知识库分段与 Redis/Qdrant/Milvus 适配 |
| 工具调用 | `@Tool`、`ToolCallback`、`ToolCallbacks`、`ToolCallingManager` | 本地 CPS 工具、动态工具和 MCP 工具 |
| 顾问 | `Advisor`、`QuestionAnswerAdvisor`、`RetrievalAugmentationAdvisor` | 会话记忆、RAG、日志/上下文增强 |
| MCP | MCP Client/Server starters、`McpSyncClient` | `/mcp/cps` CPS 工具服务及外部 MCP |

业务边界仍由 AgenticCPS 约束决定：商品推荐、搜索和分析结果是运营快照，不能直接驱动订单结算、返利冻结/扣减、提现或 Token 兑换。任何资金或会员资产变更必须通过已有领域服务、登录上下文、幂等键和审计链路完成，不能让模型或 AI 工具绕过这些边界。

## 2. 模块与依赖

AI 代码位于 `backend/qiji-module-ai`，模型、向量库和 MCP starter 已集中在该模块的 POM 中。新增依赖前先确认当前 BOM/版本，避免同一组件引入多个版本。

常用依赖（版本号应统一为项目属性；本仓库实际依赖以 POM 为准）：

```xml
<!-- 对话模型：按实际供应商选择，不要无条件全部引入 -->
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-openai</artifactId>
  <version>${spring-ai.version}</version>
</dependency>

<!-- 仅在使用向量顾问时按需引入 -->
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-advisors-vector-store</artifactId>
  <version>${spring-ai.version}</version>
</dependency>
```

本仓库已经接入 OpenAI、Anthropic、DeepSeek、Ollama、通义等模型，以及 Redis、Qdrant、Milvus 向量存储。运行时由 `AiModelFactory` 按平台和配置创建/缓存模型；业务服务不要自行散落创建 HTTP 客户端。Spring AI 2.0 的官方模型生态趋向少而精，新增供应商前先确认官方 starter、官方 SDK 和 2.0 兼容性，不要因为社区示例存在就直接加入依赖。

## 3. 配置与密钥

Spring Boot 配置前缀由 starter 定义，常见形式如下（示例只使用环境变量）：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://127.0.0.1:11434}
      chat:
        options:
          model: ${OLLAMA_CHAT_MODEL:llama3}
    vectorstore:
      redis:
        initialize-schema: false
        index-name: knowledge_index
        prefix: "knowledge_segment:"
```

开发要求：

1. API Key、数据库密码和 MCP 密钥只能来自环境变量、密钥管理系统或本地未提交配置；不得写入 Git、日志、异常消息或 Markdown 示例。
2. 生产环境关闭不需要的模型、向量库初始化和演示工具；为每个供应商设置连接与读取超时、重试上限和熔断策略。
3. 供应商特有参数通过对应的 `*ChatOptions`/`*EmbeddingOptions` 设置，通用参数（模型、温度、最大 token）由 `AiUtils.buildChatOptions` 统一转换。
4. 本项目使用 Spring MVC。MCP 服务端使用 `spring-ai-starter-mcp-server-webmvc`，不要引入 WebFlux server/client starter；配置 `type: SYNC`。流式 ChatClient 仍需要响应式栈，不能据此把 MCP 传输栈替换成 WebFlux。混用 WebFlux MCP starter 可能导致 SSE 失效。

5. Spring AI 2.0 的 Options 使用 Builder、创建后不可变，并减少了旧版配置中的多余 `.options` 层级。不要在共享单例上修改 options；优先在模型工厂中构造默认 options，在请求级别使用不可变的请求 options 覆盖。

## 4. 对话调用模式

### 4.1 新功能优先使用 ChatClient

`ChatClient` 提供类似 `WebClient` 的流畅 API，可统一默认系统提示、顾问和工具：

```java
@Service
public class ProductAnswerService {

    private final ChatClient chatClient;

    public ProductAnswerService(ChatModel model) {
        this.chatClient = ChatClient.builder(model)
                .defaultSystem("你是 CPS 商品助手，只能依据提供的数据回答。")
                .build();
    }

    public String answer(String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}
```

需要多个模型时，为每个 `ChatModel` 创建独立的 `ChatClient` bean；不要在单例中修改共享的 options。模型选择仍应经过 `AiModelFactory` 和数据库中的模型配置。

### 4.2 直接使用 ChatModel 的场景

需要精细控制消息、供应商 options、工具上下文或兼容现有链路时，使用 `Prompt`：

```java
List<Message> messages = List.of(
        new SystemMessage(systemPrompt),
        new UserMessage(userText));
ChatOptions options = AiUtils.buildChatOptions(platform, model, 0.2D, 2048,
        toolCallbacks, toolContext);
ChatResponse response = chatModel.call(new Prompt(messages, options));
String text = AiUtils.getChatResponseContent(response);
```

消息角色必须保留，历史消息需按会话和租户隔离。不要把未经清洗的用户文本拼接进系统提示，也不要把模型输出当作可信指令执行。

### 4.3 流式输出

`StreamingChatModel.stream(Prompt)` 返回 `Flux<ChatResponse>`。SSE/WebSocket 控制器应逐块发送文本，同时处理取消、超时、客户端断开和最终落库：

```java
Flux<String> chunks = streamingModel.stream(prompt)
        .map(AiUtils::getChatResponseContent)
        .filter(Objects::nonNull);
```

流式链路要记录 conversation/message/trace 标识；只有在模型正常结束后才把消息标记为完成，异常时保存可重试状态和失败原因。不要在每个 token 到达时写一次数据库。

## 5. 结构化输出

需要驱动前端卡片、报表或后续服务时，优先让模型输出 DTO，而不是解析自由文本。Spring AI 2.0 提供 Bean/Map/List 输出转换器；模型支持时优先使用原生 JSON Schema/Structured Output，模型不支持时再使用 converter 的格式指令：

```java
record ProductSummary(String title, BigDecimal price, List<String> reasons) {}

ProductSummary result = chatClient.prompt()
        .user("总结商品：" + productText)
        .call()
        .entity(ProductSummary.class);
```

生产实现必须：

- 对必填字段、枚举、金额范围和列表长度做 Bean Validation/业务校验；
- 解析失败时返回可诊断错误，不要静默使用半截 JSON；
- 在提示中明确“不确定时返回 null/空列表”，禁止臆造价格、销量、转化率或返利；
- 结构化结果保留原始来源、模型、提示版本和 trace id，便于审计和回放。

## 6. 工具调用（Tool Calling）

### 6.1 声明工具

使用 `@Tool` 描述只读查询或明确动作，参数使用简单、可校验的 DTO：

```java
public class CpsTools {
    @Tool(description = "按关键词搜索商品，仅返回运营分析快照，不生成推广链接")
    public GoodsSearchResult search(@ToolParam(description = "搜索关键词", required = true) String keyword) {
        return goodsService.search(keyword);
    }
}
```

通过 `ToolCallbacks.from(new CpsTools())` 转为 `ToolCallback`，按会话/角色动态装配。2.0 推荐 `ToolCallback`/`ToolCallbacks`，不要新增旧的 `FunctionCallback` API。`defaultToolCallbacks` 会作用于同一个 Builder 创建的所有请求；涉及权限或写操作的工具不要设为默认工具。工具定义的 description、输入 JSON Schema、返回值和异常语义都是模型决策所需的契约，必须稳定、清晰、可测试。

### 6.2 工具调用循环与 Advisor

Spring AI 2.0 将工具调用循环提升为 `ChatClient` Advisor 链中的一等组件，默认由 `ToolCallingAdvisor` 统一驱动；不要在每个供应商适配器中重复实现循环。需要自定义停止条件、逐轮审批、人工确认或细粒度观测时，使用 `AdvisorParams.toolCallingAdvisorAutoRegister(false)`，再通过 `ToolCallingManager` 显式执行循环。

顾问链是栈式执行：`getOrder()` 数值越小越先处理请求、越后处理响应；相同 order 不保证顺序。构建时使用 `defaultAdvisors()` 注册稳定顾问，调用时仅传入会话 ID、租户过滤器等请求级参数。记忆顾问、RAG 顾问和工具顾问的顺序必须写在测试中，避免上下文和工具结果错位。

### 6.3 安全与上下文

本项目在 `AiUtils` 中使用工具上下文键：`LOGIN_USER_ID`、`TENANT_ID`、`CONVERSATION_ID`、`MCP_CLIENT_NAME`、`ALLOW_MUTATION`、`TRACE_ID` 等。工具必须从可信上下文取得会员/租户身份，不能信任模型生成的 `memberId`、`userId` 或租户字段。

工具分级建议：

| 级别 | 示例 | 规则 |
| --- | --- | --- |
| 只读 | 搜索、比价、返利汇总、趋势分析 | 默认允许；限流、分页、脱敏、记录调用日志 |
| 可变更 | 保存选品条件、创建草稿 | 要求登录用户、幂等键和显式确认 |
| 高风险 | 生成推广链接、冻结/扣减返利、Token 兑换 | 独立端点和领域服务；二次确认；审计；禁止模型自动触发 |

模型不支持工具调用时，`AiUtils.buildChatOptions` 应明确抛出 `MODEL_TOOL_CALL_UNSUPPORTED`，不要静默忽略工具列表。工具异常要返回稳定错误码和可读原因，不把堆栈或密钥暴露给模型/用户。

## 7. MCP 集成

MCP 将工具、资源和提示以标准协议暴露。Spring AI 提供 MCP Client/Server starter 及同步/异步客户端。

### 7.1 CPS MCP Server

当前服务端配置位于 `backend/qiji-server/src/main/resources/application*.yaml`：

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: cps-mcp-server
        type: SYNC
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message
```

Spring AI 2.0 的 MCP 传输实现已从 MCP Java SDK 移入 Spring AI，传输依赖坐标使用 `org.springframework.ai:mcp-spring-webmvc`/`mcp-spring-webflux`；自动配置场景优先使用 `spring-ai-starter-mcp-server-webmvc`。直接导入传输类时，包名也应从 `io.modelcontextprotocol.*.transport` 迁移到 `org.springframework.ai.mcp.*.transport`。MCP Java SDK 需使用 1.0.x 发行线（2.0 文档要求 1.0.0 RC1 或更高）。

新增 CPS 工具时同步：

1. 工具实现和 `CpsMcpToolConfiguration` 注册；
2. `CpsMcpToolRiskRegistry` 风险分级；
3. `backend/sql/module/ai-all.sql`、`ai-update.sql`（如有持久化配置）；
4. 工具函数、身份归属、审计和契约测试；
5. 服务端 `instructions` 与客户端可见工具说明。

### 7.2 MCP Client

外部 MCP 连接统一通过管理后台的 MCP 管理页登记，数据库是运行时连接、启停状态、工具目录和授权策略的唯一来源；不得再向 `application*.yaml` 或 Java 配置类添加静态外部客户端。管理页保存后可先测试连接、同步工具并审核风险，再启用服务。密钥字段只保存 `env:` 引用，真实密钥必须来自部署环境或密钥管理系统，接口和页面不得回显明文。

客户端按服务编码按需初始化。单个外部服务不可用时不应阻断应用启动；连接、`listTools` 和调用都要设置超时、重试及降级。跨系统调用必须携带并验证 `X-App-Id`、`X-Tenant-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature`、`X-Idempotency-Key` 等共享请求头。`spring.ai.mcp.server` 仍用于本应用 `/mcp/cps` 服务端，不属于外部客户端管理范围。

MCP 工具回调进入 ChatModel 前，使用 `AiChatToolCallbackService` 做角色授权和身份包装；不要把所有外部 MCP 工具全局暴露给每个会话。MCP 2.0 注解能力（如 `@McpTool`、`@McpResource`、`@McpPrompt`）只在确认 starter、扫描范围和传输协议后使用，并为每个暴露能力保留权限、审计和稳定错误码。

## 8. RAG 与向量存储

### 8.1 数据管道

标准流程为：文档读取（如 Tika）→ 清洗/分段 → `EmbeddingModel` 生成向量 → `VectorStore.add()` 写入 → `similaritySearch()` 检索 → 将结果注入提示或 Advisor → 模型生成并返回引用。

```java
List<Document> docs = List.of(new Document(content,
        Map.of("tenantId", tenantId.toString(), "knowledgeId", knowledgeId.toString())));
vectorStore.add(docs);
List<Document> hits = vectorStore.similaritySearch(
        SearchRequest.builder().query(question).topK(5)
                .filterExpression(new FilterExpressionBuilder()
                        .eq("tenantId", tenantId.toString()).build()).build());
```

过滤表达式必须使用 `FilterExpressionBuilder`、白名单字段和可信上下文构造，不能直接拼接用户输入。当前实现将部分 metadata 转为字符串以兼容 Qdrant；新增字段时同步检查各 VectorStore 的类型支持。租户过滤器必须在检索器构造或每次查询上下文中强制注入，不能由模型生成。

### 8.2 Advisor 方式

数据已进入向量库时可使用：

```java
ChatResponse response = chatClient.prompt()
        .user(question)
        .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
        .call()
        .chatResponse();
```

需要更复杂的预检索、重排、后处理或引用注入时，使用 `RetrievalAugmentationAdvisor` 组合 `DocumentRetriever`。RAG 必须返回来源/命中文档标识，并明确“没有证据时无法回答”；检索结果不得越过租户和知识库权限。

## 9. 记忆与顾问

Advisor 适合封装可复用的生成式模式，例如会话记忆、RAG、日志和上下文增强。顾问有顺序和优先级：先确定会话标识，再加载记忆/检索上下文，最后执行工具和模型调用。每个顾问都要说明输入、输出、失败行为和 token 成本。2.0 的 `MessageWindowChatMemory` 默认维护有限消息窗口；生产实现必须明确窗口大小、摘要策略和持久化仓库，不得把无限历史发送给模型。

不要把无限历史全部发送给模型；按 token 预算截断或摘要，保留最近消息和关键事实。跨请求共享的记忆必须使用 `conversationId + tenantId + userId` 作为隔离键。

## 10. 可观测性、成本与可靠性

Spring AI 可对 `ChatClient`（含 Advisor）、`ChatModel`、`EmbeddingModel`、`ImageModel` 和 `VectorStore` 产生 Micrometer 指标和 tracing。建议：

- 每次调用关联 `traceId`、tenant、conversation、model、provider、tool 名称和耗时；
- 默认不记录完整 prompt/completion，生产只在受控采样或脱敏后开启；
- 记录 token 用量、估算成本、重试次数、超时、限流和降级原因；
- 对供应商错误分类（认证、配额、参数、网络、内容安全），采用有限重试和指数退避；
- 流式响应、工具调用和 MCP 调用都设置超时及取消传播。

## 11. 测试策略

按风险分层测试：

1. **单元测试**：Prompt 构造、options 映射、结构化 DTO 校验、工具参数校验、租户/会员上下文。
2. **契约测试**：每个 MCP/本地工具的名称、描述、输入 schema、风险注册和稳定错误码。
3. **集成测试**：使用 WireMock/MockWebServer 或本地 Ollama 验证超时、重试、流式结束和供应商响应解析；不要在 CI 直接调用真实付费模型。
4. **RAG 评估**：使用固定问题/文档集，结合 `RelevancyEvaluator` 或自定义评估器验证召回相关性、引用完整性和越权过滤。
5. **回归测试**：CPS P0/P1 路径必须覆盖身份归属、幂等、审计、资产不变量和失败补偿。

测试应断言业务事实而非某一句自然语言，例如结构化字段、工具调用次数、过滤条件、状态迁移和错误码。

## 12. 推荐实现流程

1. 明确 AI 功能的输入、输出 DTO、模型能力、是否需要工具/RAG/流式，以及失败和降级策略。
2. 复用 `AiModelFactory`、`AiUtils`、`AiChatToolCallbackService` 和现有会话/知识库服务，先写最小失败测试。
3. 选择 `ChatClient`（默认）或直接 `ChatModel + Prompt`（需要底层控制时），将供应商差异隔离在 options/factory 层；不要在业务 Controller 中实例化模型。
4. 设计 Advisor 链顺序：记忆、租户/RAG、工具调用、结构化校验和日志，并为每个 order 写测试。
5. 工具按最小权限动态装配，建立风险登记、身份上下文、幂等与审计；高风险动作必须显式确认。工具多于少量时评估 `ToolSearchToolCallingAdvisor` 的渐进式暴露，避免把完整工具集塞进每次请求。
6. 对结构化输出优先使用原生 schema；对失败结果做 Bean Validation 和有限次修正/重试，不能把修正循环变成无限调用。
7. 对 RAG 引用、租户过滤、流式状态、MCP 错误和工具调用次数增加契约测试；使用 `RelevancyEvaluator`/`FactCheckingEvaluator` 或固定评估集验证生成质量。
8. 运行针对模块的 Maven 测试、`mvn -pl qiji-module-ai -am compile` 和必要的静态检查；记录 Spring Boot、Spring AI、MCP SDK、模型和向量库版本。
9. 更新配置说明、SQL（如有）、MCP instructions 和相关业务文档，检查是否泄露密钥或跨租户数据。

## 13. 提交前检查清单

- [ ] 已核对 `backend/qiji-module-ai/pom.xml` 与官方 2.0.1 文档，没有把 M3/1.x 示例直接复制到生产代码。
- [ ] 新增依赖使用 BOM/项目属性管理，没有引入重复版本或未经评估的社区 starter。
- [ ] `ChatClient`、`ChatModel`、options 和 Advisor 的职责边界清晰；共享对象没有被请求线程修改。
- [ ] 工具使用 `ToolCallback`/`@Tool`，描述和 JSON Schema 稳定；工具从可信上下文取得用户、租户和幂等信息。
- [ ] 工具调用循环有最大轮数、超时、取消和异常策略；高风险动作不自动执行。
- [ ] RAG 有租户/知识库过滤、来源标识和“无证据不回答”策略。
- [ ] 结构化输出有 schema、业务校验和有限修正；失败可诊断且不会静默落库。
- [ ] prompt/completion、工具参数和向量结果默认不进入日志/trace；敏感观测只允许受控脱敏采样。
- [ ] 已执行模块测试、编译和必要的契约/E2E 验证，并记录外部模型调用未覆盖的风险。

## 14. 常见反模式

- 在 Controller 中直接 `new OpenAiChatModel` 或硬编码 API Key；
- 把用户输入、模型输出直接拼入 SQL、URL、命令或身份字段；
- 全局注册所有工具，或让模型自动调用资金/资产变更工具；
- 使用 WebFlux MCP starter 运行在 Spring MVC 应用中；
- 只解析自由文本正则，不校验结构化输出和来源；
- RAG 不带 tenant/knowledge 过滤，或把检索结果当作结算事实；
- 无限重试、无限历史和无限 token，导致费用失控；
- 在日志中打印完整 prompt、响应、Authorization 或 MCP 签名。
- 把 2.0.0-M3 页面中“开发中”或旧 Boot 版本示例当成 2.0.1 的稳定 API。
- 在 `ChatClient` 外部手写供应商专属工具循环，导致 Advisor、观测和记忆链路失去统一治理。
- 把工具调用参数/结果无条件作为 trace span 属性，造成个人信息和业务数据泄露。

## 15. 参考资料

- [Spring AI 2.0.1 官方文档](https://docs.spring.io/spring-ai/reference/)
- [Spring AI 2.0.1 Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html)
- [Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [RAG](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)
- [Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output/converters.html)
- [MCP Overview](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)
- [Observability](https://docs.spring.io/spring-ai/reference/observability/index.html)
- [Model Evaluation](https://docs.spring.io/spring-ai/reference/api/testing.html)
- [Spring AI 2.0.0-M3 中文文档](https://spring-ai.spring-doc.cn/docs/2.0.0-M3/index.html)
- [腾讯云：Spring AI 2.0 正式发布：Java 程序员必须关注的 8 个升级清单](https://cloud.tencent.com/developer/article/2697474)
