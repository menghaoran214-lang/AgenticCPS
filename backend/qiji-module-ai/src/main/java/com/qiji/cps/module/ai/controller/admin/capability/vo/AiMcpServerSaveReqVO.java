package com.qiji.cps.module.ai.controller.admin.capability.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class AiMcpServerSaveReqVO {
    private Long id;
    private String code;
    private String name;
    private String type;
    private String location;
    private String transportProtocol;
    private String endpoint;
    private String authType;
    private String secretRef;
    /** Directly entered secret. It is encrypted by the entity type handler and never returned. */
    private String secretValue;
    private Integer timeoutMillis;
    private Integer status;
}
