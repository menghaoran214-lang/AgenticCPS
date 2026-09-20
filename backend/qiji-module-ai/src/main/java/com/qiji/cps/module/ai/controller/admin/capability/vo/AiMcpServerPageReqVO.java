package com.qiji.cps.module.ai.controller.admin.capability.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class AiMcpServerPageReqVO extends PageParam { private String code; private String name; private String transportProtocol; private Integer status; private String healthStatus; }
