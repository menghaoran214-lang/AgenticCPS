package com.qiji.cps.module.ai.controller.admin.capability.vo;

import java.util.List;
import lombok.Data;

@Data
public class AiSkillSaveReqVO {
    private Long id;
    private String code;
    private String name;
    private String category;
    private String description;
    private String channelScope;
    private String riskLevel;
    private Integer status;
    /** 业务目标说明（用于基于内置 skill-creator 派生初始版本） */
    private String goal;
    /** 初始版本系统提示词 */
    private String systemPrompt;
    /** 初始版本输入契约 JSON Schema */
    private String inputSchema;
    /** 初始版本输出模板 */
    private String outputTemplate;
    /** 初始版本确认策略（NONE/REQUIRED） */
    private String confirmationPolicy;
    /** 初始版本变更说明 */
    private String changeLog;
    /** 初始版本能力绑定 */
    private List<AiSkillBindingSaveReqVO> bindings;
}
