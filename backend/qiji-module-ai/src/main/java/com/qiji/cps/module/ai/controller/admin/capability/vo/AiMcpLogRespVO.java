package com.qiji.cps.module.ai.controller.admin.capability.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class AiMcpLogRespVO { private Long id; private String mcpClientName; private String roleCode; private String roleVersion; private String skillCode; private String skillVersion; private String policyResult; private String rejectReason; private String toolName; private String result; private Integer durationMs; private LocalDateTime createTime; }
