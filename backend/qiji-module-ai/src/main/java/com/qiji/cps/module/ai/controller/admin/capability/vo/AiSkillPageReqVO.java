package com.qiji.cps.module.ai.controller.admin.capability.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class AiSkillPageReqVO extends PageParam { private String code; private String name; private String category; private String riskLevel; private Integer status; }
