package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.framework.common.mcp.McpIdentityClaims;
import com.qiji.cps.framework.common.mcp.McpIdentityTransportKeys;
import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.module.ai.service.chat.AiChatIdentityContextService;
import com.qiji.cps.module.ai.service.mcp.AiMcpIdentitySigner;
import com.qiji.cps.module.ai.util.AiUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import java.util.Map;

/** Only a server-created context can produce an identity envelope for local CPS MCP. */
final class McpTrustedIdentityConverter implements ToolContextToMcpMetaConverter {
    private final AiMcpIdentitySigner signer;
    private final String audience;
    McpTrustedIdentityConverter(AiMcpIdentitySigner signer, String audience) {
        this.signer = signer; this.audience = audience;
    }
    @Override public Map<String, Object> convert(ToolContext context) {
        if (!AiChatIdentityContextService.isTrustedLocalToolContext(context))
            throw new SecurityException("Trusted conversation identity is required");
        Map<String, Object> values = context.getContext();
        Long tenant = requiredLong(values, AiUtils.TOOL_CONTEXT_TENANT_ID);
        if (!tenant.equals(TenantContextHolder.getRequiredTenantId()))
            throw new SecurityException("Conversation tenant does not match");
        var claims = new McpIdentityClaims(requiredLong(values, AiUtils.TOOL_CONTEXT_LOGIN_USER_ID), tenant,
                requiredLong(values, AiUtils.TOOL_CONTEXT_ACTOR_USER_ID),
                requiredLong(values, AiUtils.TOOL_CONTEXT_CONVERSATION_ID), audience,
                Boolean.TRUE.equals(values.get(AiUtils.TOOL_CONTEXT_ALLOW_MUTATION)), null, null, null,
                requiredText(values, AiUtils.TOOL_CONTEXT_TRACE_ID));
        return Map.of(McpIdentityTransportKeys.META_IDENTITY_ENVELOPE, signer.sign(claims),
                McpIdentityTransportKeys.META_SELF_TEST_INVOCATION, true);
    }
    private static Long requiredLong(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number) || number.longValue() <= 0)
            throw new SecurityException("Trusted identity is incomplete");
        return number.longValue();
    }
    private static String requiredText(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new SecurityException("Trusted identity is incomplete");
        return text;
    }
}
