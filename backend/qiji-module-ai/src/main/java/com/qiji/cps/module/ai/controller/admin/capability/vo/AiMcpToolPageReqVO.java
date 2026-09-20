package com.qiji.cps.module.ai.controller.admin.capability.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class AiMcpToolPageReqVO extends PageParam { private Long serverId; private String name; private String riskLevel; private Integer status; }
