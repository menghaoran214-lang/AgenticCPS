package com.qiji.cps.module.ai.controller.admin.capability.vo;

import java.util.List;
import lombok.Data;

@Data
public class AiSkillCreatorOptionsRespVO {
    private AiSkillRespVO creatorSkill;
    private Integer creatorVersion;
    private List<LocalToolOption> localTools;
    private List<McpServerOption> mcpServers;
    private List<McpToolOption> mcpTools;
    private List<KnowledgeOption> knowledgeBases;
    private List<String> complianceRules;

    @Data
    public static class LocalToolOption {
        private String name;
        private String description;
        private String riskLevel;
    }

    @Data
    public static class McpServerOption {
        private Long id;
        private String code;
        private String name;
        private String healthStatus;
    }

    @Data
    public static class McpToolOption {
        private Long id;
        private Long serverId;
        private String serverName;
        private String name;
        private String title;
        private String description;
        private String riskLevel;
    }

    @Data
    public static class KnowledgeOption {
        private Long id;
        private String name;
        private String description;
    }
}
