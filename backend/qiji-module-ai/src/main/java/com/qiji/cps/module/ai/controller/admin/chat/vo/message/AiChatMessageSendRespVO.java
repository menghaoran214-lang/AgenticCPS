package com.qiji.cps.module.ai.controller.admin.chat.vo.message;

import com.qiji.cps.module.ai.framework.ai.core.webserch.AiWebSearchResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - AI 聊天消息发送 Response VO")
@Data
public class AiChatMessageSendRespVO {

    @Schema(description = "流式事件类型", example = "MESSAGE_DELTA")
    private String eventType;

    /** 客户端可见的请求追踪标识，不包含内部工具或身份信息。 */
    private String requestId;
    /** 当前业务阶段，例如 RETRIEVING、GENERATING。 */
    private String stage;
    private Integer completedItems;
    private Integer totalItems;
    private Long elapsedMs;
    private Boolean degraded;
    /** 用户友好的阶段提示。 */
    private String message;
    /** 超过长任务阈值时由后台任务实现填充。 */
    private String taskId;

    /** 模型调用消耗，仅返回供应商提供的 usage 元数据。 */
    private ModelUsage usage;

    /** 调试模式下的单次模型调用轨迹，不写入聊天消息表。 */
    private ModelDebugTrace modelTrace;

    @Schema(description = "工具执行状态，仅工具事件返回")
    private ToolExecution toolExecution;

    @Schema(description = "发送消息", requiredMode = Schema.RequiredMode.REQUIRED)
    private Message send;

    @Schema(description = "接收消息", requiredMode = Schema.RequiredMode.REQUIRED)
    private Message receive;

    @Schema(description = "消息")
    @Data
    public static class Message {

        @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
        private Long id;

        @Schema(description = "消息类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "role")
        private String type; // 参见 MessageType 枚举类

        @Schema(description = "聊天内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "你好，你好啊")
        private String content;

        @Schema(description = "推理内容", example = "要达到这个目标，你需要...")
        private String reasoningContent;

        @Schema(description = "知识库段落编号数组", example = "[1,2,3]")
        private List<Long> segmentIds;

        @Schema(description = "知识库段落数组")
        private List<AiChatMessageRespVO.KnowledgeSegment> segments;

        @Schema(description = "联网搜索的网页内容数组")
        private List<AiWebSearchResponse.WebPage> webSearchPages;

        @Schema(description = "结构化 UI 块")
        private List<java.util.Map<String, Object>> blocks;

        /** 模型调用消耗，通常仅 assistant 消息有值。 */
        private ModelUsage usage;

        @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
        private LocalDateTime createTime;

    }

    @Schema(description = "用户友好的工具执行信息")
    @Data
    public static class ToolExecution {
        private String executionId;
        private String toolName;
        private String intent;
        private String label;
        private String status;
        private String message;
        private String source;
        private String mcpClientName;
        private String skillCode;
        private Integer skillVersion;
    }

    @Schema(description = "模型 token 使用量")
    @Data
    public static class ModelUsage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private Long cacheReadInputTokens;
        private Long cacheWriteInputTokens;
    }

    @Schema(description = "模型调试轨迹（仅调试模式返回）")
    @Data
    public static class ModelDebugTrace {
        private String traceId;
        private Integer round;
        private String model;
        private String status;
        private Long durationMs;
        private List<ModelDebugMessage> input;
        private String output;
        private String reasoning;
        private ModelUsage usage;
    }

    @Data
    public static class ModelDebugMessage {
        private String role;
        private String content;
    }

}
