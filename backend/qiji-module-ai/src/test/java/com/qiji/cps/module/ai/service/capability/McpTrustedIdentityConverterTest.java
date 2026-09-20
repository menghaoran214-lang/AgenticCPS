package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.framework.common.mcp.McpIdentityClaims;
import com.qiji.cps.framework.common.mcp.McpIdentityEnvelope;
import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import com.qiji.cps.module.ai.service.chat.AiChatIdentityContextService;
import com.qiji.cps.module.ai.service.mcp.AiMcpIdentitySigner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.mockito.ArgumentCaptor;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpTrustedIdentityConverterTest {
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }
    @Test void bodySuppliedIdentityIsNeverSigned() {
        AiMcpIdentitySigner signer = mock(AiMcpIdentitySigner.class);
        var converter = new McpTrustedIdentityConverter(signer, "cps");
        assertThrows(SecurityException.class, () -> converter.convert(new ToolContext(Map.of("LOGIN_USER_ID", 99L))));
        verifyNoInteractions(signer);
    }
    @Test void signsPersistedMemberWithRegisteredAudienceWithoutRequiringSelfTestMode() {
        TenantContextHolder.setTenantId(2L);
        AiMcpIdentitySigner signer = mock(AiMcpIdentitySigner.class);
        when(signer.sign(any())).thenReturn(new McpIdentityEnvelope("payload", "signature"));
        var conversation = new AiChatConversationDO().setId(3L).setUserId(4L).setMemberId(5L).setChatMode("STANDARD");
        var context = new ToolContext(new AiChatIdentityContextService().buildToolContext(conversation));
        new McpTrustedIdentityConverter(signer, "cps").convert(context);
        var claims = ArgumentCaptor.forClass(McpIdentityClaims.class);
        verify(signer).sign(claims.capture());
        assertEquals(5L, claims.getValue().memberId());
        assertEquals(2L, claims.getValue().tenantId());
        assertEquals("cps", claims.getValue().clientName());
    }
    @Test void trustedContextCannotCrossTenants() {
        TenantContextHolder.setTenantId(2L);
        var conversation = new AiChatConversationDO().setId(3L).setUserId(4L).setMemberId(5L);
        var context = new ToolContext(new AiChatIdentityContextService().buildToolContext(conversation));
        TenantContextHolder.setTenantId(9L);
        AiMcpIdentitySigner signer = mock(AiMcpIdentitySigner.class);
        assertThrows(SecurityException.class, () -> new McpTrustedIdentityConverter(signer, "cps").convert(context));
        verifyNoInteractions(signer);
    }
}
