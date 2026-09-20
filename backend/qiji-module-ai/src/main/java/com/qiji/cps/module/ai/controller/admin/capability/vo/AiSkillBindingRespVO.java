package com.qiji.cps.module.ai.controller.admin.capability.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class AiSkillBindingRespVO { private Long id; private Long skillVersionId; private String bindingType; private String localToolName; private Long mcpServerId; private String mcpToolName; private Long knowledgeBaseId; private Integer enabled; }
