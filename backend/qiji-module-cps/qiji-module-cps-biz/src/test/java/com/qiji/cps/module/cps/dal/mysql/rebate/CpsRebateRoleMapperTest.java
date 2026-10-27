package com.qiji.cps.module.cps.dal.mysql.rebate;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.qiji.cps.framework.mybatis.core.util.MyBatisUtils;
import com.qiji.cps.framework.tenant.config.TenantProperties;
import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.qiji.cps.framework.test.core.ut.BaseDbUnitTest;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

@Import(CpsRebateRoleMapperTest.TenantConfig.class)
class CpsRebateRoleMapperTest extends BaseDbUnitTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class TenantConfig {
        @Bean
        TenantLineInnerInterceptor tenantLineInnerInterceptor(MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor tenant = new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, tenant, 0);
            return tenant;
        }
    }
    @Resource private TenantLineInnerInterceptor tenantLineInnerInterceptor;
    @Resource private CpsRebateRecordMapper mapper;
    @Resource private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private void insert(long id, long memberId, String role, long tenantId) {
        jdbc.update("INSERT INTO cps_rebate_record(id,member_id,order_id,rebate_type,split_role,tenant_id) VALUES (?,?,22,'rebate',?,?)",
                id, memberId, role, tenantId);
    }

    @Test
    void buyerLookupCannotReturnTheEarlierStationRecord() {
        insert(1, 900001, "PARENT_L1", 1);
        insert(2, 900002, "SELF", 1);
        assertEquals(900002L, mapper.selectByOrderIdAndType(22L, "rebate").getMemberId());
        assertEquals("SELF", mapper.selectByOrderIdAndType(22L, "rebate").getSplitRole());
    }

    @Test
    void stationOnlyRecordIsNotAConsumerRebate() {
        insert(1, 900001, "PARENT_L1", 1);
        assertNull(mapper.selectByOrderIdAndType(22L, "rebate"));
    }

    @Test
    void historicalNullRoleRemainsReadable() {
        insert(1, 900002, null, 1);
        assertEquals(900002L, mapper.selectByOrderIdAndType(22L, "rebate").getMemberId());
    }

    @Test
    void consumerLookupRemainsTenantIsolated() {
        insert(1, 999999, "SELF", 2);
        insert(2, 900002, "SELF", 1);
        assertEquals(900002L, mapper.selectByOrderIdAndType(22L, "rebate").getMemberId());
    }
}
