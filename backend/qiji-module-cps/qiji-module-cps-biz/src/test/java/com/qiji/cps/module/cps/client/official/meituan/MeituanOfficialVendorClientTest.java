package com.qiji.cps.module.cps.client.official.meituan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiji.cps.module.cps.client.CpsVendorCapability;
import com.qiji.cps.module.cps.client.dto.*;
import com.qiji.cps.module.cps.enums.CpsPlatformCodeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeituanOfficialVendorClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MeituanOfficialVendorClient client = new MeituanOfficialVendorClient();

    @Test
    @DisplayName("基础元数据与能力集校验")
    void metadataAndCapabilities() {
        assertEquals(CpsPlatformCodeEnum.MEITUAN.getCode(), client.getPlatformCode());
        assertEquals("official", client.getVendorCode());
        assertTrue(client.getCapabilities().containsAll(Set.of(
                CpsVendorCapability.GOODS_SEARCH,
                CpsVendorCapability.PROMOTION_LINK,
                CpsVendorCapability.ORDER_QUERY,
                CpsVendorCapability.CONNECTION_TEST
        )));
    }

    @Test
    @DisplayName("配置契约与参数验证")
    void configSchemaValidation() {
        var schema = client.getConfigSchema();
        assertNotNull(schema);

        // 缺少必填参数
        var invalidResult = schema.validate(CpsVendorConfig.builder()
                .appKey("test_key")
                .build());
        assertFalse(invalidResult.isValid());

        // 必填参数完整
        var validResult = schema.validate(CpsVendorConfig.builder()
                .appKey("test_app_key")
                .appSecret("test_app_secret")
                .defaultAdzoneId("sid_001")
                .build());
        assertTrue(validResult.isValid());

        // 脱敏摘要
        Map<String, String> masked = schema.maskedSummary(CpsVendorConfig.builder()
                .appKey("key_123456789")
                .appSecret("secret_987654321")
                .defaultAdzoneId("sid_001")
                .build());
        assertEquals("***", masked.get("appKey"));
        assertEquals("***", masked.get("appSecret"));
        assertEquals("sid_001", masked.get("defaultAdzoneId"));
    }

    @Test
    @DisplayName("治理策略校验")
    void governancePolicy() {
        var policy = client.getGovernancePolicy();
        assertNotNull(policy);
        assertEquals(5000, policy.getTimeoutMillis());
        assertEquals(120, policy.getRateLimitPerMinute());
        assertEquals(10000, policy.getCircuitBreakerOpenMillis());
        assertTrue(policy.isMetricsEnabled());
    }

    @Test
    @DisplayName("美团 API Gateway 签名与 Header 构建测试")
    void shepherdSignCalculation() {
        String appKey = "clwapptest";
        String appSecret = "ea429eaec5859dbe3e059878dd1f39cb";
        String body = "{\"enable\":true}";

        // 1. Content-MD5 Base64 计算
        String md5 = MeituanApiGatewaySigner.base64AndMd5(body);
        assertNotNull(md5);
        assertFalse(md5.isBlank());

        // 2. buildSignedHeaders 一站式生成
        Map<String, String> headers = MeituanApiGatewaySigner.buildSignedHeaders(
                appKey, appSecret, "POST", "/cps_open/common/api/v1/query_coupon",
                Map.of(), Map.of(), body);

        assertEquals("application/json", headers.get("Accept"));
        assertEquals("application/json; charset=UTF-8", headers.get("Content-Type"));
        assertEquals(md5, headers.get("Content-MD5"));
        assertEquals(appKey, headers.get("S-Ca-App"));
        assertNotNull(headers.get("S-Ca-Timestamp"));
        assertEquals("S-Ca-App,S-Ca-Timestamp", headers.get("S-Ca-Signature-Headers"));
        assertNotNull(headers.get("S-Ca-Signature"));
        assertFalse(headers.get("S-Ca-Signature").isBlank());
    }

    @Test
    @DisplayName("商品搜索：请求参数组装")
    void buildSearchParams() {
        CpsGoodsSearchRequest request = new CpsGoodsSearchRequest();
        request.setKeyword("肯德基");
        request.setPageNo(2);
        request.setPageSize(20);
        request.setSortType(1); // 销量
        request.setPriceLowerLimit(new BigDecimal("10.00"));
        request.setPriceUpperLimit(new BigDecimal("50.00"));

        CpsVendorConfig config = CpsVendorConfig.builder()
                .appKey("key")
                .appSecret("secret")
                .extraConfig(Map.of("platform", "2", "bizLine", "1", "cityId", "110100"))
                .build();

        Map<String, Object> params = client.buildSearchParams(request, config);
        assertEquals("肯德基", params.get("searchText"));
        assertEquals(2, params.get("pageNo"));
        assertEquals(20, params.get("pageSize"));
        assertEquals(2, params.get("platform"));
        assertEquals(1, params.get("bizLine"));
        assertEquals("110100", params.get("cityId"));
        assertEquals(2, params.get("sortField")); // 销量排序
        assertEquals(new BigDecimal("10.00"), params.get("priceMin"));
        assertEquals(new BigDecimal("50.00"), params.get("priceMax"));
    }

    @Test
    @DisplayName("商品搜索：响应解析")
    void parseSearchResponse() throws Exception {
        String json = """
                {
                  "code": 0,
                  "msg": "success",
                  "data": {
                    "total": 58,
                    "searchId": "sid_next_12345",
                    "dataList": [
                      {
                        "productViewSign": "pvs_kfc_001",
                        "skuViewId": "sku_kfc_001",
                        "categoryName": "西式快餐",
                        "couponPackDetail": {
                          "name": "肯德基 经典香辣鸡腿堡套餐",
                          "headUrl": "https://p0.meituan.net/kfc.jpg",
                          "originalPrice": "38.50",
                          "sellPrice": "19.90",
                          "saleVolume": "25000",
                          "startTime": 1770000000000,
                          "endTime": 1780000000000
                        },
                        "commissionInfo": {
                          "commission": "1.99",
                          "commissionPercent": "10.0"
                        },
                        "brandInfo": {
                          "brandName": "肯德基"
                        }
                      }
                    ]
                  }
                }
                """;

        JsonNode response = objectMapper.readTree(json);
        CpsGoodsSearchRequest request = new CpsGoodsSearchRequest();
        request.setPageNo(1);
        request.setPageSize(20);
        CpsGoodsSearchResult result = client.parseSearchResponse(response, request);

        assertNotNull(result);
        assertEquals(58L, result.getTotal());
        assertEquals("sid_next_12345", result.getNextPageId());
        assertEquals(1, result.getList().size());

        CpsGoodsItem item = result.getList().get(0);
        assertEquals("pvs_kfc_001", item.getGoodsId());
        assertEquals("meituan", item.getPlatformCode());
        assertEquals("official", item.getVendorCode());
        assertEquals("肯德基 经典香辣鸡腿堡套餐", item.getTitle());
        assertEquals("https://p0.meituan.net/kfc.jpg", item.getMainPic());
        assertEquals(new BigDecimal("38.50"), item.getOriginalPrice());
        assertEquals(new BigDecimal("19.90"), item.getActualPrice());
        assertEquals(new BigDecimal("18.60"), item.getCouponPrice());
        assertEquals(0, new BigDecimal("0.1").compareTo(item.getCommissionRate()));
        assertEquals(new BigDecimal("1.99"), item.getCommissionAmount());
        assertEquals(25000L, item.getMonthSales());
        assertEquals("肯德基", item.getBrandName());
        assertEquals("西式快餐", item.getCategoryName());
    }

    @Test
    @DisplayName("推广链接：商品转链与参数组装")
    void buildPromotionLinkParams_goods() {
        CpsPromotionLinkRequest request = new CpsPromotionLinkRequest();
        request.setGoodsId("pvs_meituan_999");
        request.setAdzoneId("sid_channel_01");

        CpsVendorConfig config = CpsVendorConfig.builder()
                .appKey("key")
                .appSecret("secret")
                .extraConfig(Map.of("platform", "1", "bizLine", "5", "linkType", "2"))
                .build();

        Map<String, Object> params = client.buildPromotionLinkParams(request, config);
        assertEquals("pvs_meituan_999", params.get("productViewSign"));
        assertEquals("sid_channel_01", params.get("sid"));
        assertEquals(2, params.get("linkType"));
        assertEquals(1, params.get("platform"));
        assertEquals(5, params.get("bizLine"));
        assertNull(params.get("text"));
        assertNull(params.get("actId"));
    }

    @Test
    @DisplayName("推广链接：活动物料与原文转链参数组装")
    void buildPromotionLinkParams_activityAndText() {
        // 1. 活动物料 actId
        CpsPromotionLinkRequest actRequest = new CpsPromotionLinkRequest();
        actRequest.setGoodsId("act:33");
        actRequest.setAdzoneId("sid_act");
        Map<String, Object> actParams = client.buildPromotionLinkParams(actRequest, null);
        assertEquals("33", actParams.get("actId"));
        assertNull(actParams.get("productViewSign"));

        // 2. 目标链接 text
        CpsPromotionLinkRequest textRequest = new CpsPromotionLinkRequest();
        textRequest.setItemLink("https://click.meituan.com/t?t=1&c=2&p=xxxx");
        textRequest.setAdzoneId("sid_text");
        Map<String, Object> textParams = client.buildPromotionLinkParams(textRequest, null);
        assertEquals("https://click.meituan.com/t?t=1&c=2&p=xxxx", textParams.get("text"));
        assertNull(textParams.get("productViewSign"));
        assertNull(textParams.get("actId"));
    }

    @Test
    @DisplayName("推广链接：响应解析包含 referralLinkMap 各格式链接")
    void parsePromotionLinkResponse() throws Exception {
        String json = """
                {
                  "code": 0,
                  "message": "success",
                  "data": {
                    "productViewSign": "pvs_123",
                    "skuViewId": "sku_123",
                    "referralLinkMap": {
                      "1": "https://click.meituan.com/t?t=1&c=2&p=long_url",
                      "2": "http://dpurl.cn/short_url",
                      "3": "imeituan://www.meituan.com/deeplink",
                      "4": "pages/index/index?query=mini",
                      "5": "￥美团专享优惠券￥"
                    }
                  }
                }
                """;

        JsonNode response = objectMapper.readTree(json);
        CpsPromotionLinkResult result = client.parsePromotionLinkResponse(response);

        assertNotNull(result);
        assertEquals("http://dpurl.cn/short_url", result.getShortUrl());
        assertEquals("https://click.meituan.com/t?t=1&c=2&p=long_url", result.getLongUrl());
        assertEquals("imeituan://www.meituan.com/deeplink", result.getMobileUrl());
        assertEquals("￥美团专享优惠券￥", result.getTpwd());
        assertEquals("pages/index/index?query=mini", result.getExtraFields().get("wxMiniPath"));
        assertEquals("imeituan://www.meituan.com/deeplink", result.getExtraFields().get("deeplink"));
        assertEquals("pvs_123", result.getExtraFields().get("productViewSign"));
    }

    @Test
    @DisplayName("订单查询：请求参数组装")
    void buildOrderQueryParams() {
        CpsOrderQueryRequest request = new CpsOrderQueryRequest();
        request.setStartTime("2026-10-26 00:00:00");
        request.setEndTime("2026-10-26 12:00:00");
        request.setQueryType(1); // 付款时间
        request.setPositionIndex("scroll_cursor_999");
        request.setPageSize(50);

        CpsVendorConfig config = CpsVendorConfig.builder()
                .appKey("key")
                .appSecret("secret")
                .extraConfig(Map.of("platform", "1", "businessLine", "1,2", "tradeType", "1"))
                .build();

        Map<String, Object> params = client.buildOrderQueryParams(request, config);
        assertEquals(1, params.get("platform"));
        assertEquals(List.of(1, 2), params.get("businessLine"));
        assertEquals(1, params.get("queryTimeType"));
        assertEquals(1, params.get("tradeType"));
        assertEquals(2, params.get("searchType"));
        assertEquals("scroll_cursor_999", params.get("scrollId"));
        assertEquals(50, params.get("limit"));
        assertNotNull(params.get("startTime"));
        assertNotNull(params.get("endTime"));
    }

    @Test
    @DisplayName("订单查询：响应解析与字段状态映射")
    void parseOrderQueryResponse() throws Exception {
        String json = """
                {
                  "code": 0,
                  "message": "success",
                  "data": {
                    "scrollId": "scroll_next_001",
                    "hasNext": true,
                    "dataList": [
                      {
                        "orderId": "MT_ORDER_20261026001",
                        "productId": "MT_PROD_888",
                        "productName": "美团外卖-大快朵颐双人餐",
                        "payPrice": "68.00",
                        "profit": "6.80",
                        "commissionRate": "10.0",
                        "quantity": 2,
                        "status": 3,
                        "payTime": 1770000000000,
                        "orderTime": 1770000000000,
                        "sid": "sid_channel_01",
                        "businessLine": 1,
                        "tradeType": 1
                      }
                    ]
                  }
                }
                """;

        JsonNode response = objectMapper.readTree(json);
        List<CpsOrderDTO> orders = client.parseOrderQueryResponse(response);

        assertNotNull(orders);
        assertEquals(1, orders.size());

        CpsOrderDTO order = orders.get(0);
        assertEquals("MT_ORDER_20261026001", order.getPlatformOrderId());
        assertEquals("meituan", order.getPlatformCode());
        assertEquals("official", order.getVendorCode());
        assertEquals("MT_PROD_888", order.getItemId());
        assertEquals("美团外卖-大快朵颐双人餐", order.getItemTitle());
        assertEquals(new BigDecimal("68.00"), order.getFinalPrice());
        assertEquals(new BigDecimal("6.80"), order.getCommissionAmount());
        assertEquals(0, new BigDecimal("0.1").compareTo(order.getCommissionRate()));
        assertEquals(2, order.getQuantity());
        assertEquals(3, order.getPlatformStatus()); // SETTLED
        assertEquals("sid_channel_01", order.getAdzoneId());
        assertEquals("scroll_next_001", order.getNextPositionIndex());
        assertEquals(0, order.getRefundTag());
    }

    @Test
    @DisplayName("连接测试成功")
    @SuppressWarnings("unchecked")
    void testConnection_success() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"code\":0,\"message\":\"success\",\"data\":{}}");
        org.mockito.Mockito.doReturn(mockResponse).when(mockHttpClient).send(any(), any());

        MeituanOfficialVendorClient testClient = new MeituanOfficialVendorClient(mockHttpClient);
        CpsVendorConfig config = CpsVendorConfig.builder()
                .appKey("test_key")
                .appSecret("test_secret")
                .build();

        boolean ok = testClient.testConnection(config);
        assertTrue(ok);
    }
}
