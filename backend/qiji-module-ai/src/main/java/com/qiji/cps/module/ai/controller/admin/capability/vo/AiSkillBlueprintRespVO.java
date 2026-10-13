package com.qiji.cps.module.ai.controller.admin.capability.vo;

import java.util.List;
import lombok.Data;

@Data
public class AiSkillBlueprintRespVO {
    private String creatorSkillCode;
    private Integer creatorSkillVersion;
    private String generationMode;
    private String code;
    private String name;
    private String category;
    private String channelScope;
    private String description;
    private String riskLevel;
    private String systemPrompt;
    private String inputSchema;
    private String outputTemplate;
    private String confirmationPolicy;
    private String changeLog;
    private List<AiSkillBindingSaveReqVO> bindings;
    private List<String> complianceRules;
}
