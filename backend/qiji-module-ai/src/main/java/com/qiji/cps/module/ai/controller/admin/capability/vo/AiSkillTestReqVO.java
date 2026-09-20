package com.qiji.cps.module.ai.controller.admin.capability.vo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data
public class AiSkillTestReqVO {
    @NotNull private Long skillVersionId;
    @Size(max = 16000) private String input;
    private Boolean executeModel = false;
    private Long modelId;
}
