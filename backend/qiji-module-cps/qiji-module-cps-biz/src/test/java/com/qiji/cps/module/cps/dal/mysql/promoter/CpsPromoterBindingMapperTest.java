package com.qiji.cps.module.cps.dal.mysql.promoter;

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

import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Import(CpsPromoterBindingMapperTest.TenantConfig.class)
class CpsPromoterBindingMapperTest extends BaseDbUnitTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class TenantConfig {
        @Bean
        TenantLineInnerInterceptor tenantLineInnerInterceptor(MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor tenant = new TenantLineInnerInterceptor(
                    new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, tenant, 0);
            return tenant;
        }
    }

    @Resource private CpsPromoterRelationMapper mapper;
    @Resource private JdbcTemplate jdbc;

    @BeforeEach
    void createFixture() {
        jdbc.execute("""
                CREATE TABLE cps_promoter_relation (
                    id BIGINT PRIMARY KEY, member_id BIGINT, parent_member_id BIGINT,
                    grandparent_member_id BIGINT, bind_time TIMESTAMP,
                    update_time TIMESTAMP, deleted BIT DEFAULT 0, tenant_id BIGINT NOT NULL)
                """);
        jdbc.update("INSERT INTO cps_promoter_relation(id, member_id, tenant_id) VALUES (3,103,1),(4,104,2)");
    }

    @AfterEach
    void removeFixture() {
        TenantContextHolder.clear();
        jdbc.execute("DROP TABLE cps_promoter_relation");
    }

    @Test
    void concurrentStationsHaveOneWinnerAndCannotUpdateAnotherTenant() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> bind(102L, ready, start));
            Future<Integer> second = executor.submit(() -> bind(999L, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
        }
        Long parent = jdbc.queryForObject("SELECT parent_member_id FROM cps_promoter_relation WHERE id=3", Long.class);
        assertTrue(Long.valueOf(102L).equals(parent) || Long.valueOf(999L).equals(parent));
        TenantContextHolder.setTenantId(1L);
        assertEquals(0, mapper.bindIfUnbound(3L, parent, null, LocalDateTime.now()));
        assertEquals(0, mapper.bindIfUnbound(4L, 102L, null, LocalDateTime.now()));
        assertNull(jdbc.queryForObject("SELECT parent_member_id FROM cps_promoter_relation WHERE id=4", Long.class));
    }

    private int bind(Long parent, CountDownLatch ready, CountDownLatch start) throws Exception {
        TenantContextHolder.setTenantId(1L);
        try {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            return mapper.bindIfUnbound(3L, parent, null, LocalDateTime.now());
        } finally {
            TenantContextHolder.clear();
        }
    }
}
