package com.qiji.cps.module.ai.controller.admin.capability.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class AiSkillVersionRespVO { private Long id; private Long revision; private Long skillId; private Integer version; private String systemPrompt; private String inputSchema; private String outputTemplate; private String confirmationPolicy; private String publishStatus; private String changeLog; private LocalDateTime publishedAt; private LocalDateTime createTime; }
