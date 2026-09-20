package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.module.ai.controller.admin.capability.vo.AiMcpLogPageReqVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiMcpAuditQueryServiceTest {
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }
    @Test void auditStatisticsAlwaysBindTheCurrentTenantAndUseSafeParameters() {
        TenantContextHolder.setTenantId(17L);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AiMcpAuditQueryService service = new AiMcpAuditQueryService();
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        AiMcpLogPageReqVO request = new AiMcpLogPageReqVO();
        request.setToolName("' OR tenant_id = 3 --");
        service.stats(request);
        verify(jdbc).queryForMap(contains("WHERE tenant_id = ? AND deleted = 0 AND tool_name = ?"),
                eq(17L), eq(request.getToolName()));
    }
    @Test void missingTenantFailsBeforeQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AiMcpAuditQueryService service = new AiMcpAuditQueryService();
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        assertThrows(RuntimeException.class, () -> service.page(new AiMcpLogPageReqVO()));
        verifyNoInteractions(jdbc);
    }
}
