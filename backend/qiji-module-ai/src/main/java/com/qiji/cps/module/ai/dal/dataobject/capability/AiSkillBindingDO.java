package com.qiji.cps.module.ai.dal.dataobject.capability;

import com.qiji.cps.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("ai_skill_binding")
@KeySequence("ai_skill_binding_seq")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AiSkillBindingDO extends TenantBaseDO {
    @TableId private Long id;
    private Long skillVersionId;
    private String bindingType;
    private String localToolName;
    private Long mcpServerId;
    private String mcpToolName;
    private Long knowledgeBaseId;
    private Integer enabled;
}
