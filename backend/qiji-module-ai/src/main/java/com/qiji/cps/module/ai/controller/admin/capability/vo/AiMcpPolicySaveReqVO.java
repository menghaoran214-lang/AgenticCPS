package com.qiji.cps.module.ai.controller.admin.capability.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class AiMcpPolicySaveReqVO { private Long id; private String code; private String name; private Long roleId; private Long skillId; private String mcpServerIds; private String mcpToolIds; private String riskLevel; private Integer requireConfirmation; private Integer status; }
