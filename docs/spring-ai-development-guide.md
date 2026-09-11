# AgenticCPS Spring AI 开发指导

> 本文面向 AgenticCPS 后续 AI 功能开发，依据 [Spring AI 1.1.3 中文文档](https://www.spring-doc.cn/spring-ai/1.1.3/index.html) 整理，并结合仓库当前实现约束编写。
>
> 当前 `backend/qiji-module-ai/pom.xml` 使用 Spring AI `1.1.8`。1.1.x API 基本兼容；升级到其他版本时，必须以对应版本的官方 API 和编译结果为准。

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

常用依赖（版本号应统一为项目属性）：

```xml
<!-- 对话模型：按实际供应商选择，不要无条件全部引入 -->
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-openai</artifactId>
  <version>${spring-ai.version}</version>
</dependency>

<!-- 使用 QuestionAnswerAdvisor / VectorStoreChatMemoryAdvisor 时需要 -->
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-advisors-vector-store</artifactId>
  <version>${spring-ai.version}</version>
</dependency>
```

本仓库已经接入 OpenAI、Azure OpenAI、Anthropic、DeepSeek、Ollama、智谱、通义等模型，以及 Redis、Qdrant、Milvus 向量存储。运行时由 `AiModelFactory` 按平台和配置创建/缓存模型；业务服务不要自行散落创建 HTTP 客户端。

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
4. 本项目使用 Spring MVC。MCP 服务端使用 `spring-ai-starter-mcp-server-webmvc`，不要引入 WebFlux starter；配置 `type: SYNC`。混用 WebFlux 可能导致 SSE 失效。

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

需要驱动前端卡片、报表或后续服务时，优先让模型输出 DTO，而不是解析自由文本。Spring AI 1.1.x 支持 Bean/Map/List 输出转换器及部分模型的原生 JSON Schema：

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
    public GoodsSearchResult search(@ToolParam(description = "搜索关键词") String keyword) {
        return goodsService.search(keyword);
    }
}
```

通过 `ToolCallbacks.from(new CpsTools())` 转为 `ToolCallback`，按会话/角色动态装配。`defaultTools` 会作用于同一个 Builder 创建的所有请求；涉及权限或写操作的工具不要设为默认工具。

### 6.2 安全与上下文

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

新增 CPS 工具时同步：

1. 工具实现和 `CpsMcpToolConfiguration` 注册；
2. `CpsMcpToolRiskRegistry` 风险分级；
3. `backend/sql/module/ai-all.sql`、`ai-update.sql`（如有持久化配置）；
4. 工具函数、身份归属、审计和契约测试；
5. 服务端 `instructions` 与客户端可见工具说明。

### 7.2 MCP Client

外部 MCP 连接按名称配置，建议按需初始化。单个外部服务不可用时不应阻断应用启动；连接、`listTools` 和调用都要设置超时、重试及降级。跨系统调用必须携带并验证 `X-App-Id`、`X-Tenant-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature`、`X-Idempotency-Key` 等共享请求头。

MCP 工具回调进入 ChatModel 前，使用 `AiChatToolCallbackService` 做角色授权和身份包装；不要把所有外部 MCP 工具全局暴露给每个会话。

## 8. RAG 与向量存储

### 8.1 数据管道

标准流程为：文档读取（如 Tika）→ 清洗/分段 → `EmbeddingModel` 生成向量 → `VectorStore.add()` 写入 → `similaritySearch()` 检索 → 将结果注入提示或 Advisor → 模型生成并返回引用。

```java
List<Document> docs = List.of(new Document(content,
        Map.of("tenantId", tenantId.toString(), "knowledgeId", knowledgeId.toString())));
vectorStore.add(docs);
List<Document> hits = vectorStore.similaritySearch(
        SearchRequest.builder().query(question).topK(5)
                .filterExpression("tenantId == '" + tenantId + "'").build());
```

过滤表达式必须使用安全的参数化/白名单构造，不能直接拼接用户输入。当前实现将部分 metadata 转为字符串以兼容 Qdrant；新增字段时同步检查各 VectorStore 的类型支持。

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

Advisor 适合封装可复用的生成式模式，例如会话记忆、RAG、日志和上下文增强。顾问有顺序和优先级：先确定会话标识，再加载记忆/检索上下文，最后执行工具和模型调用。每个顾问都要说明输入、输出、失败行为和 token 成本。

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
3. 选择 `ChatClient`（默认）或直接 `ChatModel + Prompt`（需要底层控制时），将供应商差异隔离在 options/factory 层。
4. 工具按最小权限动态装配，建立风险登记、身份上下文、幂等与审计；高风险动作必须显式确认。
5. 对结构化输出、RAG 引用、流式状态和 MCP 错误增加契约测试。
6. 运行针对模块的 Maven 测试，再执行编译、静态检查和必要的端到端验证；记录模型/依赖版本。
7. 更新配置说明、SQL（如有）、MCP instructions 和相关业务文档，检查是否泄露密钥或跨租户数据。

## 13. 常见反模式

- 在 Controller 中直接 `new OpenAiChatModel` 或硬编码 API Key；
- 把用户输入、模型输出直接拼入 SQL、URL、命令或身份字段；
- 全局注册所有工具，或让模型自动调用资金/资产变更工具；
- 使用 WebFlux MCP starter 运行在 Spring MVC 应用中；
- 只解析自由文本正则，不校验结构化输出和来源；
- RAG 不带 tenant/knowledge 过滤，或把检索结果当作结算事实；
- 无限重试、无限历史和无限 token，导致费用失控；
- 在日志中打印完整 prompt、响应、Authorization 或 MCP 签名。

## 14. 参考资料

- [Spring AI 1.1.3 中文文档首页](https://www.spring-doc.cn/spring-ai/1.1.3/index.html)
- [Chat Client API](https://www.spring-doc.cn/spring-ai/1.1.3/api_chatclient.html)
- [Tool Calling](https://www.spring-doc.cn/spring-ai/1.1.3/api_tools.html)
- [Retrieval Augmented Generation](https://www.spring-doc.cn/spring-ai/1.1.3/api_retrieval-augmented-generation.html)
- [Vector Databases](https://www.spring-doc.cn/spring-ai/1.1.3/api_vectordbs.html)
- [MCP Overview](https://www.spring-doc.cn/spring-ai/1.1.3/api_mcp_mcp-overview.html)
- [Observability](https://www.spring-doc.cn/spring-ai/1.1.3/observability_index.html)
- [Model Evaluation](https://www.spring-doc.cn/spring-ai/1.1.3/api_testing.html)
