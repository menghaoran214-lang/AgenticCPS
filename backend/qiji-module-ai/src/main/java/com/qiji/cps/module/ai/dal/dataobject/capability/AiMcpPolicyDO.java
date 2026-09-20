package com.qiji.cps.module.ai.dal.dataobject.capability;

import com.qiji.cps.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("ai_mcp_policy")
@KeySequence("ai_mcp_policy_seq")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AiMcpPolicyDO extends TenantBaseDO {
    @TableId private Long id;
    private String code;
    private String name;
    private Long roleId;
    private Long skillId;
    private String mcpServerIds;
    private String mcpToolIds;
    private String riskLevel;
    private Integer requireConfirmation;
    private Integer status;
}
