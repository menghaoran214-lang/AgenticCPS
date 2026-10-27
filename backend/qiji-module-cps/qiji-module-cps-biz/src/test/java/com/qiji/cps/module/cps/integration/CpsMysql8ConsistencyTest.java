package com.qiji.cps.module.cps.integration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.qiji.cps.framework.mybatis.core.dataobject.BaseDO;
import com.qiji.cps.framework.tenant.config.TenantProperties;
import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.qiji.cps.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import com.qiji.cps.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import com.qiji.cps.module.member.controller.app.auth.vo.AppAuthLoginReqVO;
import com.qiji.cps.module.member.dal.dataobject.user.MemberUserDO;
import com.qiji.cps.module.member.dal.mysql.user.MemberUserMapper;
import com.qiji.cps.module.member.service.auth.MemberAuthServiceImpl;
import com.qiji.cps.module.member.service.user.MemberUserServiceImpl;
import com.qiji.cps.module.system.api.logger.LoginLogApi;
import com.qiji.cps.module.pay.dal.dataobject.wallet.PayWalletTransactionDO;
import com.qiji.cps.module.pay.dal.mysql.wallet.PayWalletTransactionMapper;
import com.qiji.cps.module.cps.dal.dataobject.order.CpsOrderDO;
import com.qiji.cps.module.cps.dal.dataobject.rebate.*;
import com.qiji.cps.module.cps.dal.mysql.order.CpsOrderMapper;
import com.qiji.cps.module.cps.dal.mysql.rebate.*;
import com.qiji.cps.module.cps.dal.mysql.freeze.CpsFreezeRecordMapper;
import com.qiji.cps.module.cps.service.rebate.asset.*;
import com.qiji.cps.module.cps.enums.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.qiji.cps.framework.test.core.util.AssertUtils.assertServiceException;
import static com.qiji.cps.module.member.enums.ErrorCodeConstants.*;

/** Real MySQL 8 + production mappers/tenant plugin/type handlers, no application startup. */
@EnabledIfEnvironmentVariable(named = "CPS_MYSQL8_INTEGRATION", matches = "1")
class CpsMysql8ConsistencyTest {
    static JdbcTemplate jdbc;
    static SqlSessionTemplate sessions;
    static DataSourceTransactionManager tx;
    static final String MOBILE = "13800000000";
    static final String[] TABLES = {"member_user", "pay_wallet_transaction", "pay_order_extension", "cps_order",
            "cps_rebate_account", "cps_rebate_record", "cps_freeze_record", "cps_rebate_debt",
            "cps_rebate_asset_ledger"};

    @BeforeAll static void setup() throws Exception {
        // Fail closed before connecting. No configurable host/schema and no Tencent credentials.
        assertEquals("true", System.getenv("GITHUB_ACTIONS"));
        assertEquals("github-hosted", System.getenv("RUNNER_ENVIRONMENT"));
        UnpooledDataSource ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                "jdbc:mysql://127.0.0.1:3306/cps_mapper_ci?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
                "root", "mapper-ci-only");
        jdbc = new JdbcTemplate(ds);
        assertTrue(jdbc.queryForObject("SELECT VERSION()", String.class).startsWith("8.0."));
        assertEquals("cps_mapper_ci", jdbc.queryForObject("SELECT DATABASE()", String.class));
        Path root = Path.of(System.getenv("GITHUB_WORKSPACE"));
        String system = Files.readString(root.resolve("backend/sql/mysql/ruoyi-vue-pro.sql"));
        createTable(system, "system_schema_version");
        execute(Files.readString(root.resolve("backend/sql/migrations/V20261028_01__member_core_mysql8.sql")));
        execute(Files.readString(root.resolve("backend/sql/migrations/V20261028_06__member_tenant_interceptor_alignment.sql")));
        execute(Files.readString(root.resolve("backend/sql/migrations/V20261028_05__pay_core_mysql8.sql")));
        if (!"1".equals(System.getenv("CPS_MYSQL8_BASELINE")))
            execute(Files.readString(root.resolve("backend/sql/module/member-update.sql")));
        String cps = Files.readString(root.resolve("backend/sql/module/cps-all-in-one.sql"));
        for (String table : TABLES)
            if (table.startsWith("cps_")) createTable(cps, table);
        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("isolated-mysql8", new SpringManagedTransactionFactory(), ds));
        GlobalConfig global = new GlobalConfig();
        global.setDbConfig(new GlobalConfig.DbConfig());
        global.setMetaObjectHandler(new MetaObjectHandler() {
            public void insertFill(MetaObject m) {
                setFieldValByName("createTime", LocalDateTime.now(), m);
                setFieldValByName("updateTime", LocalDateTime.now(), m);
                setFieldValByName("creator", "ci", m);
                setFieldValByName("updater", "ci", m);
            }
            public void updateFill(MetaObject m) {
                setFieldValByName("updateTime", LocalDateTime.now(), m);
                setFieldValByName("updater", "ci", m);
            }
        });
        GlobalConfigUtils.setGlobalConfig(config, global);
        MybatisPlusInterceptor plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        plugin.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor());
        config.addInterceptor(plugin);
        for (Class<?> mapper : List.of(MemberUserMapper.class, PayWalletTransactionMapper.class,
                CpsOrderMapper.class, CpsRebateAccountMapper.class, CpsRebateRecordMapper.class,
                CpsFreezeRecordMapper.class, CpsRebateDebtMapper.class, CpsRebateAssetLedgerMapper.class))
            config.addMapper(mapper);
        config.addMapper(com.qiji.cps.module.pay.dal.mysql.order.PayOrderExtensionMapper.class);
        SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(config);
        sessions = new SqlSessionTemplate(factory);
        tx = new DataSourceTransactionManager(ds);
    }

    static void createTable(String sql, String table) {
        Matcher m = Pattern.compile("CREATE TABLE(?: IF NOT EXISTS)? `" + table + "`\\s*\\(.*?;",
                Pattern.DOTALL).matcher(sql);
        assertTrue(m.find(), "Canonical DDL missing: " + table);
        jdbc.execute(m.group());
    }
    static void execute(String sql) {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return null;
        });
    }
    @BeforeEach void clean() {
        for (String table : TABLES) jdbc.execute("DELETE FROM " + table);
        TenantContextHolder.setTenantId(1L);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    static <T> T mapper(Class<T> type) { return sessions.getMapper(type); }

    MemberUserDO member(String password) {
        MemberUserDO user = new MemberUserDO().setMobile(MOBILE).setPassword(password)
                .setNickname("ci").setStatus(0).setTagIds(List.of(3L, 9L));
        mapper(MemberUserMapper.class).insert(user);
        return user;
    }
    PayWalletTransactionDO wallet(long id, String no) {
        PayWalletTransactionDO row = new PayWalletTransactionDO();
        row.setWalletId(id); row.setNo(no); row.setBizId("same-biz");
        row.setBizType(1); row.setTitle("synthetic"); row.setPrice(100); row.setBalance(100);
        mapper(PayWalletTransactionMapper.class).insert(row);
        return row;
    }
    PayWalletTransactionDO scoped(long walletId) throws Exception {
        // Exercise the legacy query itself on the baseline, not just a missing signature.
        try {
            return (PayWalletTransactionDO) PayWalletTransactionMapper.class
                    .getMethod("selectByBiz", Long.class, String.class, Integer.class)
                    .invoke(mapper(PayWalletTransactionMapper.class), walletId, "same-biz", 1);
        } catch (NoSuchMethodException missing) {
            if (!"1".equals(System.getenv("CPS_MYSQL8_BASELINE"))) throw missing;
            return (PayWalletTransactionDO) PayWalletTransactionMapper.class
                    .getMethod("selectByBiz", String.class, Integer.class)
                    .invoke(mapper(PayWalletTransactionMapper.class), "same-biz", 1);
        }
    }

    @Test void walletBusinessLookupMatchesWalletAndTenantUniqueKey() throws Exception {
        PayWalletTransactionDO a = wallet(10, "W1");
        PayWalletTransactionDO b = wallet(20, "W2");
        assertEquals(a.getId(), scoped(10).getId());
        assertEquals(b.getId(), scoped(20).getId());
        assertNull(scoped(30));
        assertThrows(RuntimeException.class, () -> wallet(10, "duplicate"));
        TenantContextHolder.setTenantId(2L);
        assertNull(scoped(10));
        PayWalletTransactionDO other = wallet(10, "W-other");
        assertEquals(other.getId(), scoped(10).getId());
        TenantContextHolder.setTenantId(1L);
        assertEquals(a.getId(), scoped(10).getId());
    }

    @Test void repeatedDeleteAndReregisterKeepsAllHistoryAndOnlyOneActiveMobile() {
        MemberUserMapper users = mapper(MemberUserMapper.class);
        for (int i = 0; i < 4; i++) {
            MemberUserDO user = member("hash-" + i);
            assertEquals(user.getId(), users.selectByMobile(MOBILE).getId());
            assertEquals(List.of(3L, 9L), users.selectById(user.getId()).getTagIds());
            assertThrows(RuntimeException.class, () -> member("duplicate"));
            assertEquals(1, users.deleteById(user.getId()));
            assertNull(users.selectByMobile(MOBILE));
        }
        MemberUserDO active = member("active");
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM member_user", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM member_user WHERE deleted=1", Integer.class));
        TenantContextHolder.setTenantId(2L);
        assertNull(users.selectByMobile(MOBILE));
        member("tenant-2");
        TenantContextHolder.setTenantId(1L);
        assertEquals(active.getId(), users.selectByMobile(MOBILE).getId());
        assertEquals(1L, users.selectCount(null));
    }

    @Test void loginUsesMysqlMemberMapperLongListHandlerAndRealBcrypt() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        MemberUserDO user = member(encoder.encode("synthetic-password"));
        MemberUserServiceImpl userService = new MemberUserServiceImpl();
        ReflectionTestUtils.setField(userService, "memberUserMapper", mapper(MemberUserMapper.class));
        ReflectionTestUtils.setField(userService, "passwordEncoder", encoder);
        MemberAuthServiceImpl auth = new MemberAuthServiceImpl();
        ReflectionTestUtils.setField(auth, "userService", userService);
        OAuth2TokenCommonApi tokens = mock(OAuth2TokenCommonApi.class);
        ReflectionTestUtils.setField(auth, "oauth2TokenApi", tokens);
        ReflectionTestUtils.setField(auth, "loginLogApi", mock(LoginLogApi.class));
        OAuth2AccessTokenRespDTO token = new OAuth2AccessTokenRespDTO();
        token.setUserId(user.getId()); token.setAccessToken("synthetic-token");
        when(tokens.createAccessToken(any())).thenReturn(token);
        AppAuthLoginReqVO req = new AppAuthLoginReqVO();
        req.setMobile(MOBILE); req.setPassword("synthetic-password");
        assertEquals("synthetic-token", auth.login(req).getAccessToken());
        assertNotNull(userService.getUser(user.getId()).getLoginDate());
        verify(tokens).createAccessToken(argThat(t -> user.getId().equals(t.getUserId())));
        clearInvocations(tokens);
        req.setPassword("wrong");
        assertServiceException(() -> auth.login(req), AUTH_LOGIN_BAD_CREDENTIALS);
        req.setPassword("synthetic-password");
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> auth.login(req), AUTH_LOGIN_BAD_CREDENTIALS);
        TenantContextHolder.setTenantId(1L);
        jdbc.update("UPDATE member_user SET status=1 WHERE id=?", user.getId());
        assertServiceException(() -> auth.login(req), AUTH_LOGIN_USER_DISABLED);
        jdbc.update("UPDATE member_user SET status=0,deleted=1 WHERE id=?", user.getId());
        assertServiceException(() -> auth.login(req), AUTH_LOGIN_BAD_CREDENTIALS);
        verifyNoInteractions(tokens);
    }

    CpsRebateAssetService assetService(CpsRebateAssetLedgerMapper ledger) {
        CpsRebateAssetServiceImpl service = new CpsRebateAssetServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", mapper(CpsOrderMapper.class));
        ReflectionTestUtils.setField(service, "rebateRecordMapper", mapper(CpsRebateRecordMapper.class));
        ReflectionTestUtils.setField(service, "accountMapper", mapper(CpsRebateAccountMapper.class));
        ReflectionTestUtils.setField(service, "freezeRecordMapper", mapper(CpsFreezeRecordMapper.class));
        ReflectionTestUtils.setField(service, "debtMapper", mapper(CpsRebateDebtMapper.class));
        ReflectionTestUtils.setField(service, "ledgerMapper", ledger);
        ReflectionTestUtils.setField(service, "moneyConverter", new CpsMoneyConverter());
        ReflectionTestUtils.setField(service, "policyService", mock(CpsRebateAssetPolicyService.class));
        ProxyFactory proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        return (CpsRebateAssetService) proxy.getProxy();
    }
    long orderFixture() {
        // Fixtures via SQL leave production mapper updates and transaction orchestration under test.
        jdbc.update("INSERT INTO cps_order (id,platform_code,platform_order_id,member_id,order_status,settle_time,tenant_id) VALUES (24,'ci','P24',33,'settled',NOW(),1)");
        jdbc.update("INSERT INTO cps_rebate_record (id,order_id,member_id,platform_code,platform_order_id,rebate_amount,rebate_type,rebate_status,tenant_id) VALUES (25,24,33,'ci','P24',5.00,?, ?,1)",
                CpsRebateTypeEnum.REBATE.getType(), CpsRebateStatusEnum.PENDING.getStatus());
        return 24;
    }
    @Test void realOrderRebateTransactionCreditsOnceAndPreservesCentLedger() {
        long id = orderFixture();
        CpsRebateAssetService service = assetService(mapper(CpsRebateAssetLedgerMapper.class));
        assertEquals(500, service.creditOrderRebate(id, "credit-24").availableBalanceCent());
        assertEquals(500, service.creditOrderRebate(id, "credit-24").availableBalanceCent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM cps_rebate_asset_ledger", Integer.class));
        assertEquals(new BigDecimal("5.00"), mapper(CpsRebateAccountMapper.class).selectByMemberId(33L).getAvailableBalance());
        assertEquals("rebate_received", mapper(CpsOrderMapper.class).selectById(id).getOrderStatus());
        CpsRebateAssetLedgerDO ledger = mapper(CpsRebateAssetLedgerMapper.class)
                .selectByBusinessAndIdempotencyKey("ORDER_REBATE_CREDIT", "credit-24");
        assertEquals(500L, ledger.getAvailableChangeCent());
        assertEquals(ledger.getAvailableAfterCent() - ledger.getAvailableBeforeCent(), ledger.getAvailableChangeCent());
        TenantContextHolder.setTenantId(2L);
        assertNull(mapper(CpsRebateAssetLedgerMapper.class).selectByBusinessAndIdempotencyKey("ORDER_REBATE_CREDIT", "credit-24"));
        assertThrows(IllegalStateException.class, () -> service.creditOrderRebate(id, "other-tenant"));
    }

    @Test void lateLedgerFailureRollsBackOrderAccountAndRebateOnRealMysql() {
        long id = orderFixture();
        CpsRebateAssetLedgerMapper ledger = spy(mapper(CpsRebateAssetLedgerMapper.class));
        doThrow(new IllegalStateException("synthetic ledger failure")).when(ledger).insert(any(CpsRebateAssetLedgerDO.class));
        CpsRebateAssetService service = assetService(ledger);
        assertThrows(IllegalStateException.class, () -> service.creditOrderRebate(id, "fail-24"));
        assertEquals("settled", mapper(CpsOrderMapper.class).selectById(id).getOrderStatus());
        assertEquals(CpsRebateStatusEnum.PENDING.getStatus(), mapper(CpsRebateRecordMapper.class).selectById(25L).getRebateStatus());
        assertEquals(0L, mapper(CpsRebateAccountMapper.class).selectCount(null));
        assertEquals(0L, mapper(CpsRebateAssetLedgerMapper.class).selectCount(null));
    }
    @Test void paymentJsonTypeHandlerRoundTripsAndRemainsTenantScoped() {
        var extensions = mapper(com.qiji.cps.module.pay.dal.mysql.order.PayOrderExtensionMapper.class);
        var row = com.qiji.cps.module.pay.dal.dataobject.order.PayOrderExtensionDO.builder()
                .no("P-json").orderId(24L).status(0)
                .channelExtras(Map.of("walletId", "10", "note", "中文")).build();
        extensions.insert(row);
        assertEquals(row.getChannelExtras(), extensions.selectByNo("P-json").getChannelExtras());
        assertEquals("OBJECT", jdbc.queryForObject("SELECT JSON_TYPE(channel_extras) FROM pay_order_extension WHERE id=?", String.class, row.getId()));
        TenantContextHolder.setTenantId(2L);
        assertNull(extensions.selectByNo("P-json"));
    }

    @Test void concurrentWalletInsertsAcceptExactlyOneAndRejectOnlyDuplicateKeys() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                final int index = i;
                results.add(pool.submit(() -> {
                    TenantContextHolder.setTenantId(1L);
                    try {
                        ready.countDown();
                        if (!start.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                        wallet(10L, "W-concurrent-" + index);
                        return true;
                    } catch (RuntimeException ex) {
                        Throwable cause = ex;
                        while (cause != null) {
                            if (cause instanceof java.sql.SQLException sql && sql.getErrorCode() == 1062)
                                return false;
                            cause = cause.getCause();
                        }
                        throw ex;
                    } finally { TenantContextHolder.clear(); }
                }));
            }
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) if (result.get(30, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pay_wallet_transaction", Integer.class));
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test void memberFreshInitializationMatchesUpgradeSchema() throws Exception {
        if ("1".equals(System.getenv("CPS_MYSQL8_BASELINE"))) return;
        Path root = Path.of(System.getenv("GITHUB_WORKSPACE"));
        jdbc.execute("CREATE DATABASE cps_member_init_ci CHARACTER SET utf8mb4");
        try {
            var ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                    "jdbc:mysql://127.0.0.1:3306/cps_member_init_ci?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
                    "root", "mapper-ci-only");
            var fresh = new JdbcTemplate(ds);
            String system = Files.readString(root.resolve("backend/sql/mysql/ruoyi-vue-pro.sql"));
            Matcher table = Pattern.compile("CREATE TABLE `system_schema_version`\\s*\\(.*?;", Pattern.DOTALL).matcher(system);
            assertTrue(table.find()); fresh.execute(table.group());
            String init = Files.readString(root.resolve("backend/sql/module/member-all.sql"));
            fresh.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                        new org.springframework.core.io.ByteArrayResource(init.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                return null;
            });
            String columns = "SELECT table_name,column_name,column_type,is_nullable,extra,generation_expression FROM information_schema.columns WHERE table_schema=? AND table_name LIKE 'member_%' ORDER BY table_name,ordinal_position";
            assertEquals(jdbc.queryForList(columns, "cps_mapper_ci"), jdbc.queryForList(columns, "cps_member_init_ci"));
            String indexes = "SELECT table_name,index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=? AND table_name LIKE 'member_%' ORDER BY table_name,index_name,seq_in_index";
            assertEquals(jdbc.queryForList(indexes, "cps_mapper_ci"), jdbc.queryForList(indexes, "cps_member_init_ci"));
            assertEquals("v2.0.1", fresh.queryForObject("SELECT version FROM system_schema_version WHERE module='member'", String.class));
        } finally { jdbc.execute("DROP DATABASE cps_member_init_ci"); }
    }

    @AfterAll static void prepareDeterministicSyntheticRestoreFixture() {
        if ("1".equals(System.getenv("CPS_MYSQL8_BASELINE"))) return;
        CpsMysql8ConsistencyTest test = new CpsMysql8ConsistencyTest();
        test.clean();
        try {
            for (int i = 0; i < 3; i++) {
                MemberUserDO history = test.member("synthetic-history-hash");
                mapper(MemberUserMapper.class).deleteById(history.getId());
            }
            test.member(new BCryptPasswordEncoder(4).encode("synthetic-password"));
            test.wallet(10L, "W-restore-1");
            test.wallet(20L, "W-restore-2");
            var extension = com.qiji.cps.module.pay.dal.dataobject.order.PayOrderExtensionDO.builder()
                    .no("P-restore-json").orderId(24L).status(0)
                    .channelExtras(Map.of("walletId", "10", "note", "恢复演练")).build();
            mapper(com.qiji.cps.module.pay.dal.mysql.order.PayOrderExtensionMapper.class).insert(extension);
            test.assetService(mapper(CpsRebateAssetLedgerMapper.class))
                    .creditOrderRebate(test.orderFixture(), "restore-credit-24");
            TenantContextHolder.setTenantId(2L);
            test.member("synthetic-other-tenant");
        } finally { TenantContextHolder.clear(); }
    }

}
