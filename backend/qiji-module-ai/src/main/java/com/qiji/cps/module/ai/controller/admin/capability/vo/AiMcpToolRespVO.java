package com.qiji.cps.module.ai.controller.admin.capability.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class AiMcpToolRespVO { private Long id; private Long serverId; private String name; private String title; private String description; private String inputSchema; private String outputSchema; private String sourceVersion; private String syncStatus; private String riskLevel; private Integer status; private LocalDateTime createTime; }
