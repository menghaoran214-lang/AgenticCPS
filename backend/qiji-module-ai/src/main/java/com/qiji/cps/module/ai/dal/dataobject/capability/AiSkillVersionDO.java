package com.qiji.cps.module.ai.dal.dataobject.capability;

import com.qiji.cps.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.time.LocalDateTime;

@TableName("ai_skill_version")
@KeySequence("ai_skill_version_seq")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AiSkillVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long skillId;
    private Integer version;
    private Long revision;
    private String systemPrompt;
    private String inputSchema;
    private String outputTemplate;
    private String confirmationPolicy;
    private String publishStatus;
    private String changeLog;
    private LocalDateTime publishedAt;
}
