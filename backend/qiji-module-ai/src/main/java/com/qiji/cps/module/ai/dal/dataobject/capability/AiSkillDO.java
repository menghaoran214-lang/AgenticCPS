package com.qiji.cps.module.ai.dal.dataobject.capability;

import com.qiji.cps.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("ai_skill")
@KeySequence("ai_skill_seq")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AiSkillDO extends TenantBaseDO {
    @TableId private Long id;
    private String code;
    private String name;
    private String category;
    private String description;
    private String channelScope;
    private String riskLevel;
    private Integer status;
    private Integer currentVersion;
}
