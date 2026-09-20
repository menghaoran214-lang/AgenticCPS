package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.module.ai.controller.admin.capability.vo.AiMcpLogPageReqVO;
import com.qiji.cps.module.ai.controller.admin.capability.vo.AiMcpLogRespVO;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The shared CPS audit table is queried with explicit tenant scope and a safe column allowlist. */
@Service
public class AiMcpAuditQueryService {
    @Resource private JdbcTemplate jdbc;

    public PageResult<AiMcpLogRespVO> page(AiMcpLogPageReqVO request) {
        Filter filter = filter(request);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM cps_mcp_access_log" + filter.where(),
                Long.class, filter.args().toArray());
        List<Object> parameters = new ArrayList<>(filter.args());
        int size = Math.min(100, Math.max(1, request.getPageSize()));
        parameters.add(size);
        parameters.add((long) (Math.max(1, request.getPageNo()) - 1) * size);
        List<AiMcpLogRespVO> rows = jdbc.query("SELECT id, role_code, role_version, skill_code, skill_version, "
                + "policy_result, reject_reason, tool_name, mcp_client_name, status, duration_ms, create_time FROM cps_mcp_access_log"
                + filter.where() + " ORDER BY id DESC LIMIT ? OFFSET ?", (rs, index) -> {
            AiMcpLogRespVO row = new AiMcpLogRespVO();
            row.setId(rs.getLong("id")); row.setRoleCode(rs.getString("role_code"));
            row.setMcpClientName(rs.getString("mcp_client_name"));
            row.setRoleVersion(rs.getString("role_version")); row.setSkillCode(rs.getString("skill_code"));
            row.setSkillVersion(rs.getString("skill_version")); row.setPolicyResult(rs.getString("policy_result"));
            String reason = rs.getString("reject_reason");
            row.setRejectReason(reason != null && reason.matches("[A-Z0-9_]{1,128}") ? reason : null);
            row.setToolName(rs.getString("tool_name")); row.setResult(rs.getInt("status") == 1 ? "SUCCESS" : "FAILED");
            row.setDurationMs(rs.getInt("duration_ms"));
            var timestamp = rs.getTimestamp("create_time");
            row.setCreateTime(timestamp == null ? null : timestamp.toLocalDateTime());
            return row;
        }, parameters.toArray());
        return new PageResult<>(rows, total == null ? 0L : total);
    }

    public Map<String, Object> stats(AiMcpLogPageReqVO request) {
        Filter filter = filter(request);
        // ALLOW and CONFIRM_REQUIRED are policy decisions, not completed tool invocations.
        Map<String,Object> result = new java.util.LinkedHashMap<>(jdbc.queryForMap("SELECT COUNT(*) AS total, COALESCE(SUM(status = 1), 0) AS succeeded, "
                + "COALESCE(SUM(status = 0), 0) AS failed, COALESCE(AVG(duration_ms), 0) AS averageDurationMs "
                + "FROM cps_mcp_access_log" + filter.where()
                + " AND (policy_result IS NULL OR policy_result IN ('SUCCEEDED', 'FAILED', 'DENY'))",
                filter.args().toArray()));
        result.put("roleUsage", usage(filter,"role_code"));
        result.put("skillUsage", usage(filter,"skill_code"));
        result.put("denied",jdbc.queryForObject("SELECT COUNT(*) FROM cps_mcp_access_log" + filter.where()
                + " AND policy_result = 'DENY'",Long.class,filter.args().toArray()));
        result.put("p95DurationMs",jdbc.queryForObject("SELECT COALESCE(MIN(duration_ms),0) FROM ("
                + "SELECT duration_ms, CUME_DIST() OVER (ORDER BY duration_ms) AS percentile FROM cps_mcp_access_log"
                + filter.where() + " AND (policy_result IS NULL OR policy_result IN ('SUCCEEDED','FAILED'))) latency "
                + "WHERE percentile >= 0.95",Integer.class,filter.args().toArray()));
        return result;
    }

    private List<Map<String,Object>> usage(Filter filter,String column) {
        // Column names are fixed private call sites, values remain bound parameters.
        return jdbc.queryForList("SELECT " + column + " AS code, COUNT(*) AS total FROM cps_mcp_access_log"
                + filter.where() + " AND " + column + " IS NOT NULL AND policy_result IN ('SUCCEEDED','FAILED')"
                + " GROUP BY " + column + " ORDER BY total DESC LIMIT 100",filter.args().toArray());
    }

    private Filter filter(AiMcpLogPageReqVO request) {
        StringBuilder where = new StringBuilder(" WHERE tenant_id = ? AND deleted = 0");
        List<Object> args = new ArrayList<>(); args.add(TenantContextHolder.getRequiredTenantId());
        add(where, args, "role_code", request.getRoleCode());
        add(where, args, "skill_code", request.getSkillCode());
        add(where, args, "tool_name", request.getToolName());
        if (request.getResult() != null && !request.getResult().isBlank()) {
            if (!List.of("SUCCESS", "FAILED").contains(request.getResult()))
                throw new IllegalArgumentException("Invalid audit result filter");
            where.append(" AND status = ?"); args.add("SUCCESS".equals(request.getResult()) ? 1 : 0);
        }
        return new Filter(where.toString(), args);
    }
    private static void add(StringBuilder where, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) { where.append(" AND ").append(column).append(" = ?"); args.add(value); }
    }
    private record Filter(String where, List<Object> args) { }
}
