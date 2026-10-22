package com.qiji.cps.module.cps.job;

import com.qiji.cps.framework.tenant.core.job.TenantJob;
import com.qiji.cps.module.cps.service.selection.CpsSelectionThemeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CpsGoodsPriceTrackingJobTest {

    @InjectMocks
    private CpsGoodsPriceTrackingJob job;

    @Mock
    private CpsSelectionThemeService selectionThemeService;

    @Test
    void executeRunsWithTenantJobAnnotationAndDelegatesToService() throws Exception {
        assertNotNull(CpsGoodsPriceTrackingJob.class.getMethod("execute", String.class)
                .getAnnotation(TenantJob.class));

        when(selectionThemeService.trackTrackedItemPrices(50))
                .thenReturn("定时价格跟踪完成：共扫描 3 个商品，成功 3 个，失败 0 个");

        String result = job.execute("{\"batchSize\":50}");
        assertEquals("定时价格跟踪完成：共扫描 3 个商品，成功 3 个，失败 0 个", result);
        verify(selectionThemeService).trackTrackedItemPrices(50);
    }

    @Test
    void executeWithNullParamDefaultsTo50() {
        when(selectionThemeService.trackTrackedItemPrices(50))
                .thenReturn("未发现开启价格趋势跟踪的选品商品");

        String result = job.execute(null);
        assertEquals("未发现开启价格趋势跟踪的选品商品", result);
        verify(selectionThemeService).trackTrackedItemPrices(50);
    }
}
