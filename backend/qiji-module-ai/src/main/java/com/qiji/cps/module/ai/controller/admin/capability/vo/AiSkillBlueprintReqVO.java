package com.qiji.cps.module.ai.controller.admin.capability.vo;

import lombok.Data;

@Data
public class AiSkillBlueprintReqVO {
    private String code;
    private String name;
    private String category;
    private String channelScope;
    private String riskLevel;
    private String goal;
    private Boolean useAi;
    private Long modelId;
}
