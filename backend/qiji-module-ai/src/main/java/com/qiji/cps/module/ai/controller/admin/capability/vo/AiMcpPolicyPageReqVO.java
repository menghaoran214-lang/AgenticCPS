package com.qiji.cps.module.ai.controller.admin.capability.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class AiMcpPolicyPageReqVO extends PageParam {
    private String code;
    private String name;
    private Integer status;
}
