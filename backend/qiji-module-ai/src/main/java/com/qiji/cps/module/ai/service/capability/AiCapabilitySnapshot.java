package com.qiji.cps.module.ai.service.capability;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** Persisted, server-created capability contract. Never accepted from a chat request. */
@Data
public class AiCapabilitySnapshot {
    private Integer schemaVersion;
    private String roleCode;
    private Integer roleVersion;
    private String riskLevel;
    private Boolean allowMutation = false;
    private List<Long> knowledgeIds = new ArrayList<>();
    private List<Tool> tools = new ArrayList<>();
    private List<Skill> skills = new ArrayList<>();
    @Data
    public static class Tool {
        private String name;
        private Long serverId;
        private String serverCode;
        private String riskLevel;
        private String title;
        private String serverFingerprint;
        private String inputSchema;
        private Boolean requireConfirmation;
        private String skillCode;
        private Integer skillVersion;
    }
    @Data
    public static class Skill {
        private Long id;
        private String code;
        private Long versionId;
        private Integer version;
        private String systemPrompt;
        private String outputTemplate;
        private String inputSchema;
        private String confirmationPolicy;
        private String riskLevel;
    }
}
