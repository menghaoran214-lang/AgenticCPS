package com.qiji.cps.module.ai.service.chat.tool;

import lombok.Data;

@Data
public class AiChatToolExecutionEvent {

    private String eventType;
    private String executionId;
    private String toolName;
    private String intent;
    private String label;
    private String status;
    private String message;
    private String resultPayload;
    /** 工具来源：LOCAL、MCP 或 SELF_MCP。 */
    private String source;
    private String mcpClientName;
    private String skillCode;
    private Integer skillVersion;

}
