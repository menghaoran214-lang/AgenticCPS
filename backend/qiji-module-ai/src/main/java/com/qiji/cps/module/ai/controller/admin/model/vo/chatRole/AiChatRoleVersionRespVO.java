package com.qiji.cps.module.ai.controller.admin.model.vo.chatRole;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiChatRoleVersionRespVO {
    private Long id;
    private Long roleId;
    private Integer version;
    private String publishStatus;
    private String definitionSnapshot;
    private String creator;
    private LocalDateTime createTime;
}
