package com.qiji.cps.module.ai.controller.admin.capability.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class AiSkillVersionSaveReqVO { private Long id; private Long expectedRevision; private Long skillId; private Integer version; private String systemPrompt; private String inputSchema; private String outputTemplate; private String confirmationPolicy; private String publishStatus; private String changeLog; }
