package com.qiji.cps.module.ai.controller.admin.capability.vo;
import java.util.List;
import lombok.Data;
@Data
public class AiSkillTestRespVO {
    private Long skillVersionId;
    private String systemPrompt;
    private String inputSchema;
    private String outputTemplate;
    private String input;
    private List<AiSkillBindingRespVO> bindings;
    private Integer excludedBindingCount;
    private String executionMode;
    private String output;
}
