package com.qiji.cps.module.ai.dal.dataobject.capability;

import com.qiji.cps.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("ai_mcp_tool")
@KeySequence("ai_mcp_tool_seq")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AiMcpToolDO extends TenantBaseDO {
    @TableId private Long id;
    private Long serverId;
    private String name;
    private String title;
    private String description;
    private String inputSchema;
    private String outputSchema;
    private String sourceVersion;
    private String syncStatus;
    private String riskLevel;
    private Integer status;
}
