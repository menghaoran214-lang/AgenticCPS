package com.qiji.cps.module.ai.dal.dataobject.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qiji.cps.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;

/** Append-only role definitions, independent of the mutable current role row. */
@Data
@TableName("ai_chat_role_version")
public class AiChatRoleVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long roleId;
    private Integer version;
    private String publishStatus;
    private String definitionSnapshot;
}
