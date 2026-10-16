package com.qiji.cps.module.cps.client.taobao;

import com.qiji.cps.module.cps.client.CpsApiVendorClient;
import com.qiji.cps.module.cps.client.CpsPlatformClientFactory;
import com.qiji.cps.module.cps.client.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaobaoPlatformClientAdapterTest {

    @Mock private CpsPlatformClientFactory factory;
    @Mock private CpsApiVendorClient primaryVendor;
    @Mock private CpsApiVendorClient fallbackVendor;
    private TaobaoPlatformClientAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TaobaoPlatformClientAdapter();
        ReflectionTestUtils.setField(adapter, "factory", factory);
    }

    @Test
    void searchGoods_returnsPrimaryResultWhenNotEmpty() {
        CpsVendorConfig primaryConfig = CpsVendorConfig.builder().vendorCode("dataoke").platformCode("taobao").build();
        CpsGoodsSearchRequest request = new CpsGoodsSearchRequest();
        request.setKeyword("手机");
        CpsGoodsSearchResult primaryResult = CpsGoodsSearchResult.builder()
                .list(List.of(CpsGoodsItem.builder().goodsId("1001").title("苹果手机").build()))
                .total(1L)
                .build();

        when(factory.getActiveVendorClient("taobao")).thenReturn(primaryVendor);
        when(factory.getActiveVendorConfig("taobao")).thenReturn(primaryConfig);
        when(primaryVendor.searchGoods(request, primaryConfig)).thenReturn(primaryResult);

        CpsGoodsSearchResult result = adapter.searchGoods(request);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
        assertEquals("1001", result.getList().get(0).getGoodsId());
        verify(factory, never()).getVendorClient("haodanku", "taobao");
    }

    @Test
    void searchGoods_fallsBackToHaodankuWhenPrimaryDataokeReturnsEmpty() {
        CpsVendorConfig primaryConfig = CpsVendorConfig.builder().vendorCode("dataoke").platformCode("taobao").build();
        CpsVendorConfig fallbackConfig = CpsVendorConfig.builder().vendorCode("haodanku").platformCode("taobao").build();
        CpsGoodsSearchRequest request = new CpsGoodsSearchRequest();
        request.setKeyword("鼠标垫");

        CpsGoodsSearchResult emptyPrimaryResult = CpsGoodsSearchResult.builder()
                .list(Collections.emptyList())
                .total(0L)
                .build();
        CpsGoodsSearchResult fallbackResult = CpsGoodsSearchResult.builder()
                .list(List.of(CpsGoodsItem.builder().goodsId("hdk-2001").title("超大鼠标垫").build()))
                .total(1L)
                .build();

        when(factory.getActiveVendorClient("taobao")).thenReturn(primaryVendor);
        when(factory.getActiveVendorConfig("taobao")).thenReturn(primaryConfig);
        when(primaryVendor.getVendorCode()).thenReturn("dataoke");
        when(primaryVendor.searchGoods(request, primaryConfig)).thenReturn(emptyPrimaryResult);

        when(factory.getVendorClient("haodanku", "taobao")).thenReturn(fallbackVendor);
        when(factory.getVendorConfig("haodanku", "taobao")).thenReturn(fallbackConfig);
        when(fallbackVendor.searchGoods(request, fallbackConfig)).thenReturn(fallbackResult);

        CpsGoodsSearchResult result = adapter.searchGoods(request);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
        assertEquals("hdk-2001", result.getList().get(0).getGoodsId());
        verify(fallbackVendor).searchGoods(request, fallbackConfig);
    }

    @Test
    void generatePromotionLink_fallsBackToDataokeWhenPrimaryFails() {
        CpsVendorConfig primaryConfig = CpsVendorConfig.builder().vendorCode("haodanku").platformCode("taobao").build();
        CpsVendorConfig fallbackConfig = CpsVendorConfig.builder().vendorCode("dataoke").platformCode("taobao").build();
        CpsPromotionLinkRequest request = new CpsPromotionLinkRequest();
        request.setGoodsId("hdk-2001");

        CpsPromotionLinkResult fallbackLinkResult = CpsPromotionLinkResult.builder()
                .shortUrl("https://s.click.taobao.com/test")
                .tpwd("￥test￥")
                .build();

        when(factory.getActiveVendorClient("taobao")).thenReturn(primaryVendor);
        when(factory.getActiveVendorConfig("taobao")).thenReturn(primaryConfig);
        when(primaryVendor.getVendorCode()).thenReturn("haodanku");
        when(primaryVendor.generatePromotionLink(request, primaryConfig)).thenReturn(null);

        when(factory.getVendorClient("dataoke", "taobao")).thenReturn(fallbackVendor);
        when(factory.getVendorConfig("dataoke", "taobao")).thenReturn(fallbackConfig);
        when(fallbackVendor.generatePromotionLink(request, fallbackConfig)).thenReturn(fallbackLinkResult);

        CpsPromotionLinkResult result = adapter.generatePromotionLink(request);

        assertNotNull(result);
        assertEquals("https://s.click.taobao.com/test", result.getShortUrl());
        verify(fallbackVendor).generatePromotionLink(request, fallbackConfig);
    }
}
