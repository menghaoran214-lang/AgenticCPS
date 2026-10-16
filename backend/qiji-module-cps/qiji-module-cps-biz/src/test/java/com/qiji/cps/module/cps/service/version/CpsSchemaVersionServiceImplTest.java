package com.qiji.cps.module.cps.service.version;

import com.qiji.cps.module.cps.dal.dataobject.version.CpsSchemaVersionDO;
import com.qiji.cps.module.cps.dal.mysql.version.CpsSchemaVersionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CpsSchemaVersionServiceImplTest {

    @InjectMocks
    private CpsSchemaVersionServiceImpl service;

    @Mock
    private CpsSchemaVersionMapper schemaVersionMapper;

    @Test
    @DisplayName("getLatestSchemaVersion - 默认查询 cps 模块最新版本与结构更新时间")
    void getLatestSchemaVersion_defaultModule() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 16, 14, 50, 0);
        CpsSchemaVersionDO mockDO = CpsSchemaVersionDO.builder()
                .id(1L)
                .module("cps")
                .version("v2.1.0")
                .schemaUpdateTime(now)
                .scriptSource("cps-all-in-one.sql")
                .description("CPS联盟返利系统全量基线结构")
                .status("SUCCESS")
                .build();

        when(schemaVersionMapper.selectLatestByModule("cps")).thenReturn(mockDO);

        CpsSchemaVersionDO result = service.getLatestSchemaVersion(null);

        assertNotNull(result);
        assertEquals("cps", result.getModule());
        assertEquals("v2.1.0", result.getVersion());
        assertEquals(now, result.getSchemaUpdateTime());
        assertEquals("SUCCESS", result.getStatus());
        verify(schemaVersionMapper, times(1)).selectLatestByModule("cps");
    }

    @Test
    @DisplayName("listSchemaVersions - 查询指定模块的历史版本列表")
    void listSchemaVersions_success() {
        LocalDateTime t1 = LocalDateTime.of(2026, 10, 16, 14, 50, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 18, 16, 49, 4);

        List<CpsSchemaVersionDO> mockList = List.of(
                CpsSchemaVersionDO.builder().id(2L).module("cps").version("v2.1.0").schemaUpdateTime(t1).build(),
                CpsSchemaVersionDO.builder().id(1L).module("cps").version("v2.0.1").schemaUpdateTime(t2).build()
        );

        when(schemaVersionMapper.selectListByModule("cps")).thenReturn(mockList);

        List<CpsSchemaVersionDO> result = service.listSchemaVersions("cps");

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("v2.1.0", result.get(0).getVersion());
        assertEquals("v2.0.1", result.get(1).getVersion());
        verify(schemaVersionMapper, times(1)).selectListByModule("cps");
    }

}
