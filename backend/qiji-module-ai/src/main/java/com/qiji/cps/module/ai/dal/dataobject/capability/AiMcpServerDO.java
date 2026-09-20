package com.qiji.cps.module.ai.dal.dataobject.capability;

import com.qiji.cps.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.time.LocalDateTime;

@TableName("ai_mcp_server")
@KeySequence("ai_mcp_server_seq")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AiMcpServerDO extends TenantBaseDO {
    @TableId private Long id;
    private String code;
    private String name;
    private String type;
    private String location;
    private String transportProtocol;
    private String endpoint;
    private String authType;
    private String secretRef;
    private Integer timeoutMillis;
    private Integer status;
    private String healthStatus;
    private LocalDateTime lastHealthCheckAt;
    private String lastError;
}
