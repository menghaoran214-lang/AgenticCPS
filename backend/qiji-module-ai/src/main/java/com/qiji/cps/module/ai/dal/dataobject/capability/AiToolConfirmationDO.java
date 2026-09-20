package com.qiji.cps.module.ai.dal.dataobject.capability;
import com.baomidou.mybatisplus.annotation.*;
import com.qiji.cps.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("ai_tool_confirmation")
public class AiToolConfirmationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long conversationId;
    private Long ownerId;
    private String toolName;
    private String arguments;
    private String status;
    private LocalDateTime expiresAt;
}
