package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.module.ai.controller.admin.capability.vo.AiMcpLogPageReqVO;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AiCapabilityAuditDbTest {
    private JdbcTemplate jdbc;
    private AiCapabilityAuditService writer;
    private AiMcpAuditQueryService reader;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE cps_mcp_access_log (id bigint auto_increment PRIMARY KEY, tenant_id bigint NOT NULL, "
                + "member_id bigint, actor_user_id bigint, actor_user_type varchar(16), conversation_id bigint, "
                + "tool_name varchar(64) NOT NULL, invocation_source varchar(32), status int NOT NULL, duration_ms int, "
                + "role_code varchar(64), role_version int, skill_code varchar(64), skill_version int, policy_result varchar(32), "
                + "reject_reason varchar(128), mcp_client_name varchar(128), create_time timestamp DEFAULT CURRENT_TIMESTAMP, "
                + "deleted int DEFAULT 0, request_params text, response_data text)");
        writer = new AiCapabilityAuditService(); reader = new AiMcpAuditQueryService();
        ReflectionTestUtils.setField(writer,"jdbc",jdbc); ReflectionTestUtils.setField(reader,"jdbc",jdbc);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); jdbc.execute("DROP ALL OBJECTS"); }

    @Test void auditRowsAndAggregatesRespectTenantAndExcludePolicyChecksFromCallCounts() {
        var conversation = new AiChatConversationDO().setId(9L).setUserId(42L).setOwnerUserType("ADMIN");
        var snapshot = new AiCapabilitySnapshot().setRoleCode("selector").setRoleVersion(3);
        var tool = new AiCapabilitySnapshot.Tool().setName("search").setServerId(2L).setServerCode("catalog")
                .setSkillCode("selection").setSkillVersion(7);
        TenantContextHolder.setTenantId(1L);
        writer.record(conversation,snapshot,tool,"ALLOW",null,0);
        writer.record(conversation,snapshot,tool,"SUCCEEDED",null,123);
        TenantContextHolder.setTenantId(2L);
        writer.record(conversation,snapshot,tool,"FAILED","TOOL_UNAVAILABLE",900);
        TenantContextHolder.setTenantId(1L);
        var page = reader.page(new AiMcpLogPageReqVO());
        assertEquals(2,page.getTotal());
        assertTrue(page.getList().stream().allMatch(row -> "catalog".equals(row.getMcpClientName())));
        var stats = reader.stats(new AiMcpLogPageReqVO());
        assertEquals(1,((Number)stats.get("total")).intValue());
        assertEquals(1,((Number)stats.get("succeeded")).intValue());
        assertEquals(123,((Number)stats.get("p95DurationMs")).intValue());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cps_mcp_access_log WHERE request_params IS NOT NULL OR response_data IS NOT NULL",Integer.class));
    }
}
