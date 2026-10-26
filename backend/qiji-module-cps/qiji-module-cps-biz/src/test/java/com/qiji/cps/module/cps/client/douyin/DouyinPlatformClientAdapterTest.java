package com.qiji.cps.module.cps.client.douyin;

import com.qiji.cps.module.cps.client.CpsApiVendorClient;
import com.qiji.cps.module.cps.client.CpsPlatformClientFactory;
import com.qiji.cps.module.cps.client.CpsVendorException;
import com.qiji.cps.module.cps.client.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DouyinPlatformClientAdapterTest {

    @Mock
    private CpsPlatformClientFactory factory;

    @Mock
    private CpsApiVendorClient vendor;

    @Test
    @DisplayName("平台编码为 douyin")
    void platformCode() {
        DouyinPlatformClientAdapter adapter = new DouyinPlatformClientAdapter(factory);
        assertEquals("douyin", adapter.getPlatformCode());
    }

    @Test
    @DisplayName("商品搜索：正常委托激活的供应商")
    void searchGoods_delegatesToActiveVendor() {
        CpsVendorConfig config = CpsVendorConfig.builder().build();
        when(factory.getActiveVendorClient("douyin")).thenReturn(vendor);
        when(factory.getActiveVendorConfig("douyin")).thenReturn(config);

        CpsGoodsSearchRequest request = new CpsGoodsSearchRequest();
        request.setKeyword("保温杯");
        CpsGoodsSearchResult mockResult = CpsGoodsSearchResult.builder().total(10L).build();
        when(vendor.searchGoods(request, config)).thenReturn(mockResult);

        DouyinPlatformClientAdapter adapter = new DouyinPlatformClientAdapter(factory);
        CpsGoodsSearchResult result = adapter.searchGoods(request);

        assertNotNull(result);
        assertEquals(10L, result.getTotal());
        verify(vendor).searchGoods(request, config);
    }

    @Test
    @DisplayName("商品搜索：未找到供应商时返回空结果")
    void searchGoods_returnsEmptyWhenNoVendor() {
        when(factory.getActiveVendorClient("douyin")).thenReturn(null);

        DouyinPlatformClientAdapter adapter = new DouyinPlatformClientAdapter(factory);
        CpsGoodsSearchRequest request = new CpsGoodsSearchRequest();
        CpsGoodsSearchResult result = adapter.searchGoods(request);

        assertNotNull(result);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    @DisplayName("推广链接：正常委托激活的供应商")
    void generatePromotionLink_delegatesToActiveVendor() {
        CpsVendorConfig config = CpsVendorConfig.builder().build();
        when(factory.getActiveVendorClient("douyin")).thenReturn(vendor);
        when(factory.getActiveVendorConfig("douyin")).thenReturn(config);

        CpsPromotionLinkRequest request = new CpsPromotionLinkRequest();
        request.setGoodsId("dy_goods_123");
        CpsPromotionLinkResult mockResult = CpsPromotionLinkResult.builder()
                .shortUrl("https://v.douyin.com/abc/")
                .tpwd("8.88 d@u.cl 03/12 【好物推荐】")
                .build();
        when(vendor.generatePromotionLink(request, config)).thenReturn(mockResult);

        DouyinPlatformClientAdapter adapter = new DouyinPlatformClientAdapter(factory);
        CpsPromotionLinkResult result = adapter.generatePromotionLink(request);

        assertNotNull(result);
        assertEquals("https://v.douyin.com/abc/", result.getShortUrl());
        assertEquals("8.88 d@u.cl 03/12 【好物推荐】", result.getTpwd());
        verify(vendor).generatePromotionLink(request, config);
    }

    @Test
    @DisplayName("推广链接：未配置供应商时返回 null")
    void generatePromotionLink_returnsNullWhenNoVendor() {
        when(factory.getActiveVendorClient("douyin")).thenReturn(null);

        DouyinPlatformClientAdapter adapter = new DouyinPlatformClientAdapter(factory);
        CpsPromotionLinkRequest request = new CpsPromotionLinkRequest();
        assertNull(adapter.generatePromotionLink(request));
    }

    @Test
    @DisplayName("订单分页查询：委托供应商与异常处理")
    void queryOrderPage_delegatesAndHandlesUnavailable() {
        CpsVendorConfig config = CpsVendorConfig.builder().build();
        when(factory.getActiveVendorClient("douyin")).thenReturn(vendor);
        when(factory.getActiveVendorConfig("douyin")).thenReturn(config);

        CpsOrderQueryRequest request = new CpsOrderQueryRequest();
        CpsOrderPageResult mockResult = CpsOrderPageResult.cursor(List.of(), "cursor_dy_1", true);
        when(vendor.queryOrderPage(request, config)).thenReturn(mockResult);

        DouyinPlatformClientAdapter adapter = new DouyinPlatformClientAdapter(factory);
        CpsOrderPageResult pageResult = adapter.queryOrderPage(request);

        assertNotNull(pageResult);
        assertEquals("cursor_dy_1", pageResult.getNextCursor());
        verify(vendor).queryOrderPage(request, config);

        // 未配置时抛出不可用异常
        when(factory.getActiveVendorClient("douyin")).thenReturn(null);
        assertThrows(CpsVendorException.class, () -> adapter.queryOrderPage(request));
    }

    @Test
    @DisplayName("订单列表查询：正常委托与未配置兜底")
    void queryOrders_delegatesAndFallbacks() {
        CpsVendorConfig config = CpsVendorConfig.builder().build();
        when(factory.getActiveVendorClient("douyin")).thenReturn(vendor);
        when(factory.getActiveVendorConfig("douyin")).thenReturn(config);

        CpsOrderQueryRequest request = new CpsOrderQueryRequest();
        when(vendor.queryOrders(request, config)).thenReturn(List.of(
                CpsOrderDTO.builder().platformOrderId("dy_order_1").build()
        ));

        DouyinPlatformClientAdapter adapter = new DouyinPlatformClientAdapter(factory);
        List<CpsOrderDTO> orders = adapter.queryOrders(request);

        assertEquals(1, orders.size());
        assertEquals("dy_order_1", orders.get(0).getPlatformOrderId());

        when(factory.getActiveVendorClient("douyin")).thenReturn(null);
        assertEquals(Collections.emptyList(), adapter.queryOrders(request));
    }

    @Test
    @DisplayName("连接测试：正常委托与未配置返回 false")
    void testConnection() {
        CpsVendorConfig config = CpsVendorConfig.builder().build();
        when(factory.getActiveVendorClient("douyin")).thenReturn(vendor);
        when(factory.getActiveVendorConfig("douyin")).thenReturn(config);
        when(vendor.testConnection(config)).thenReturn(true);

        DouyinPlatformClientAdapter adapter = new DouyinPlatformClientAdapter(factory);
        assertTrue(adapter.testConnection());

        when(factory.getActiveVendorClient("douyin")).thenReturn(null);
        assertFalse(adapter.testConnection());
    }

}
