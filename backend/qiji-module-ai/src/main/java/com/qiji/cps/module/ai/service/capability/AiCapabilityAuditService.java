package com.qiji.cps.module.ai.service.capability;
import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class AiCapabilityAuditService {
    @Resource private JdbcTemplate jdbc;
    public void record(AiChatConversationDO conversation, AiCapabilitySnapshot snapshot,
                       AiCapabilitySnapshot.Tool tool, String decision, String reason, int duration) {
        jdbc.update("INSERT INTO cps_mcp_access_log (tenant_id, member_id, actor_user_id, actor_user_type, "
                + "conversation_id, tool_name, invocation_source, status, duration_ms, role_code, role_version, "
                + "skill_code, skill_version, policy_result, reject_reason, mcp_client_name) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                TenantContextHolder.getRequiredTenantId(), conversation.getMemberId(), conversation.getUserId(),
                conversation.getOwnerUserType(), conversation.getId(), tool.getName(), "AI_CAPABILITY_V2",
                "SUCCEEDED".equals(decision) || "ALLOW".equals(decision) ? 1 : 0, duration, snapshot.getRoleCode(), snapshot.getRoleVersion(),
                tool.getSkillCode(), tool.getSkillVersion(), decision, reason,
                tool.getServerId() == null ? "local" : tool.getServerCode());
    }
}
