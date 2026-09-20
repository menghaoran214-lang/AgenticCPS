package com.qiji.cps.module.ai.controller.admin.capability.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class AiMcpLogPageReqVO extends PageParam { private String roleCode; private String skillCode; private String toolName; private String result; }
