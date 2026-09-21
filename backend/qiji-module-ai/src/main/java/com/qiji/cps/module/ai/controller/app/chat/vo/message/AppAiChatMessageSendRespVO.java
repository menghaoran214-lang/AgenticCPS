package com.qiji.cps.module.ai.controller.app.chat.vo.message;

import com.qiji.cps.module.ai.framework.ai.core.webserch.AiWebSearchResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "会员端 - AI 聊天消息发送 Response VO")
@Data
public class AppAiChatMessageSendRespVO {
    private String eventType;
    private String requestId;
    private String stage;
    private Integer completedItems;
    private Integer totalItems;
    private Long elapsedMs;
    private Boolean degraded;
    private String message;
    private String taskId;
    private ToolExecution toolExecution;
    private ModelUsage usage;
    private ModelDebugTrace modelTrace;
    private Message send;
    private Message receive;

    @Data
    public static class Message {
        private Long id;
        private String type;
        private String content;
        private String reasoningContent;
        private List<Long> segmentIds;
        private List<AppAiChatMessageRespVO.KnowledgeSegment> segments;
        private List<AiWebSearchResponse.WebPage> webSearchPages;
        private List<java.util.Map<String, Object>> blocks;
        private ModelUsage usage;
        private LocalDateTime createTime;
    }

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

    @Data
    public static class ModelUsage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private Long cacheReadInputTokens;
        private Long cacheWriteInputTokens;
    }

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
