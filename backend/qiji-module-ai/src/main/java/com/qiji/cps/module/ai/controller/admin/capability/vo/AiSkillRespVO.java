package com.qiji.cps.module.ai.controller.admin.capability.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AiSkillRespVO {
    private Long id;
    private String code;
    private String name;
    private String category;
    private String description;
    private String channelScope;
    private String riskLevel;
    private Integer status;
    private Integer currentVersion;
    private Boolean builtIn;
    private String creatorSkillCode;
    private LocalDateTime createTime;
}
