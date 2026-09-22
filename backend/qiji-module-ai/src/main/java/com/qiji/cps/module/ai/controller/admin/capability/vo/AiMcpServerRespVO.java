package com.qiji.cps.module.ai.controller.admin.capability.vo;

import com.qiji.cps.framework.common.pojo.PageParam;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class AiMcpServerRespVO {
    private Long id;
    private String code;
    private String name;
    private String type;
    private String location;
    private String transportProtocol;
    private String endpoint;
    private String authType;
    private String secretRef;
    private Boolean secretConfigured;
    private Integer timeoutMillis;
    private Integer status;
    private String healthStatus;
    private LocalDateTime lastHealthCheckAt;
    private String lastError;
    private LocalDateTime createTime;
}
