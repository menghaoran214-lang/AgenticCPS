package com.qiji.cps.module.cps.client.official.douyin;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DouyinOfficialVendorClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DouyinOfficialVendorClient client = new DouyinOfficialVendorClient();

    @Test
    @DisplayName("基础元数据与能力集校验")
    void metadataAndCapabilities() {
        assertEquals(CpsPlatformCodeEnum.DOUYIN.getCode(), client.getPlatformCode());
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

        // 必填项缺失
        var invalidResult = schema.validate(CpsVendorConfig.builder()
                .appKey("test_key")
                .build());
        assertFalse(invalidResult.isValid());

        // 必填项完整
        var validResult = schema.validate(CpsVendorConfig.builder()
                .appKey("test_key")
                .appSecret("test_secret")
                .authToken("token_123")
                .defaultAdzoneId("pid_douke_001")
                .build());
        assertTrue(validResult.isValid());

        // 脱敏摘要
        Map<String, String> masked = schema.maskedSummary(CpsVendorConfig.builder()
                .appKey("test_key_long_string")
                .appSecret("my_secret_123456")
                .authToken("secret_token_abcdef")
                .defaultAdzoneId("pid_001")
                .build());
        assertEquals("***", masked.get("appSecret"));
        assertEquals("***", masked.get("authToken"));
        assertEquals("***", masked.get("appKey"));
        assertEquals("pid_001", masked.get("defaultAdzoneId"));
    }

    @Test
    @DisplayName("抖音 OpenAPI 签名计算测试")
    void signCalculation() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("method", "buyin.kolMaterialsProductsSearch");
        params.put("app_key", "mock_app_key");
        params.put("timestamp", "2026-10-26 12:00:00");
        params.put("v", "2");
        params.put("param_json", "{\"page\":1,\"page_size\":20,\"title\":\"手机\"}");

        String appSecret = "mock_secret_abcdef";
        String sign = DouyinOpenApiSigner.sign(params, appSecret);

        assertNotNull(sign);
        assertEquals(64, sign.length()); // 256 位 = 64 个十六进制字符

        // 校验幂等性与大小写 (全部为小写 hex)
        assertEquals(sign, sign.toLowerCase());
        assertEquals(sign, DouyinOpenApiSigner.sign(params, appSecret));

        // sign 和 sign_method 不参与计算
        params.put("sign", "ignore_me");
        params.put("sign_method", "hmac-sha256");
        assertEquals(sign, DouyinOpenApiSigner.sign(params, appSecret));
    }

    @Test
    @DisplayName("构建完整签名请求参数与 URL 组装")
    void buildSignedParamsAndUrl() {
        CpsVendorConfig config = CpsVendorConfig.builder()
                .appKey("key_123")
                .appSecret("secret_456")
                .authToken("access_token_789")
                .apiBaseUrl("https://openapi-fxg.jinritemai.com")
                .build();

        String paramJson = "{\"title\":\"测试商品\"}";
        Map<String, String> signedParams = DouyinOpenApiSigner.buildSignedParams(
                "buyin.kolMaterialsProductsSearch", paramJson, config);

        assertEquals("key_123", signedParams.get("app_key"));
        assertEquals("buyin.kolMaterialsProductsSearch", signedParams.get("method"));
        assertEquals(paramJson, signedParams.get("param_json"));
        assertEquals("2", signedParams.get("v"));
        assertEquals("hmac-sha256", signedParams.get("sign_method"));
        assertEquals("access_token_789", signedParams.get("access_token"));
        assertNotNull(signedParams.get("timestamp"));
        assertNotNull(signedParams.get("sign"));

        String url = DouyinOpenApiSigner.buildUrl("https://openapi-fxg.jinritemai.com",
                "/buyin/kolMaterialsProductsSearch", signedParams);
        assertTrue(url.startsWith("https://openapi-fxg.jinritemai.com/buyin/kolMaterialsProductsSearch?"));
        assertTrue(url.contains("app_key=key_123"));
        assertTrue(url.contains("method=buyin.kolMaterialsProductsSearch"));
    }

    @Test
    @DisplayName("商品搜索：请求参数组装")
    void buildSearchParams() {
        CpsGoodsSearchRequest request = new CpsGoodsSearchRequest();
        request.setKeyword("运动鞋");
        request.setPageNo(2);
        request.setPageSize(30);
        request.setSortType(1);
        request.setPriceLowerLimit(new BigDecimal("99.00"));
        request.setPriceUpperLimit(new BigDecimal("299.00"));
        request.setMinCommissionRate(new BigDecimal("15.5"));

        CpsVendorConfig config = CpsVendorConfig.builder()
                .appKey("key")
                .appSecret("secret")
                .build();

        Map<String, Object> params = client.buildSearchParams(request, config);
        assertEquals("运动鞋", params.get("title"));
        assertEquals(2, params.get("page"));
        assertEquals(30, params.get("page_size"));
        assertEquals(1, params.get("sort_type"));
        assertEquals(9900L, params.get("price_min"));
        assertEquals(29900L, params.get("price_max"));
        assertEquals(15.5, params.get("cos_ratio_min"));
    }

    @Test
    @DisplayName("商品搜索：响应解析 (支持分转元与佣金率计算)")
    void parseSearchResponse() throws Exception {
        String json = """
                {
                  "code": 10000,
                  "msg": "success",
                  "data": {
                    "total": 128,
                    "products": [
                      {
                        "product_id": "3589912345678",
                        "title": "抖音爆款高品质保温杯",
                        "cover": "https://p3.douyinpic.com/item/1.jpg",
                        "price": 2990,
                        "market_price": 5990,
                        "coupon_price": 1000,
                        "cos_ratio": 20.0,
                        "cos_fee": 398,
                        "sales": 15000,
                        "shop_name": "官方旗舰店",
                        "first_cname": "家居日用",
                        "detail_url": "https://haohuo.jinritemai.com/views/product/item2?id=3589912345678"
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
        assertEquals(128L, result.getTotal());
        assertEquals(1, result.getList().size());

        CpsGoodsItem item = result.getList().get(0);
        assertEquals("3589912345678", item.getGoodsId());
        assertEquals("douyin", item.getPlatformCode());
        assertEquals("official", item.getVendorCode());
        assertEquals("抖音爆款高品质保温杯", item.getTitle());
        assertEquals(new BigDecimal("29.90"), item.getActualPrice());
        assertEquals(new BigDecimal("59.90"), item.getOriginalPrice());
        assertEquals(new BigDecimal("10.00"), item.getCouponPrice());
        assertEquals(new BigDecimal("20.0"), item.getCommissionRate());
        assertEquals(new BigDecimal("3.98"), item.getCommissionAmount());
        assertEquals(15000L, item.getMonthSales());
        assertEquals("官方旗舰店", item.getShopName());
        assertEquals("家居日用", item.getCategoryName());
    }

    @Test
    @DisplayName("推广转链：请求参数与响应解析 (口令、短链、唤起深度链接)")
    void buildAndParsePromotionLink() throws Exception {
        CpsPromotionLinkRequest request = new CpsPromotionLinkRequest();
        request.setGoodsId("3589912345678");
        request.setAdzoneId("dy_pid_888");
        request.setExternalId("user_uid_1001");
        request.setOriginalContent("7.99 复制打开抖音查看【保温杯】");

        CpsVendorConfig config = CpsVendorConfig.builder()
                .defaultAdzoneId("default_pid_000")
                .build();

        Map<String, Object> params = client.buildPromotionLinkParams(request, config);
        assertEquals("7.99 复制打开抖音查看【保温杯】", params.get("command"));
        assertEquals("dy_pid_888", params.get("pid"));
        assertEquals("user_uid_1001", params.get("external_info"));

        String json = """
                {
                  "code": 10000,
                  "msg": "success",
                  "data": {
                    "share_command": "8.88 d@u.cl 03/12 【好物推荐】",
                    "share_url": "https://v.douyin.com/eXample/",
                    "long_url": "https://haohuo.jinritemai.com/views/product/item2?id=3589912345678&pid=dy_pid_888",
                    "deep_link": "snssdk1128://ec_goods_detail?id=3589912345678",
                    "product_info": {
                      "price": 2990,
                      "cos_ratio": 20.0,
                      "cos_fee": 398
                    }
                  }
                }
                """;

        JsonNode response = objectMapper.readTree(json);
        CpsPromotionLinkResult linkResult = client.parsePromotionLinkResponse(response);

        assertNotNull(linkResult);
        assertEquals("8.88 d@u.cl 03/12 【好物推荐】", linkResult.getTpwd());
        assertEquals("https://v.douyin.com/eXample/", linkResult.getShortUrl());
        assertEquals("snssdk1128://ec_goods_detail?id=3589912345678", linkResult.getMobileUrl());
        assertEquals(new BigDecimal("29.90"), linkResult.getActualPrice());
        assertEquals(new BigDecimal("20.0"), linkResult.getCommissionRate());
        assertEquals(new BigDecimal("3.98"), linkResult.getCommissionAmount());
    }

    @Test
    @DisplayName("订单查询：请求参数与响应解析 (状态与佣金映射)")
    void buildAndParseOrderQuery() throws Exception {
        CpsOrderQueryRequest request = new CpsOrderQueryRequest();
        request.setStartTime("2026-10-25 00:00:00");
        request.setEndTime("2026-10-26 00:00:00");
        request.setPageNo(1);
        request.setPageSize(50);

        CpsVendorConfig config = CpsVendorConfig.builder()
                .defaultAdzoneId("dy_pid_888")
                .build();

        Map<String, Object> params = client.buildOrderQueryParams(request, config);
        assertEquals("2026-10-25 00:00:00", params.get("start_time"));
        assertEquals("2026-10-26 00:00:00", params.get("end_time"));
        assertEquals(1, params.get("page"));
        assertEquals(50, params.get("page_size"));
        assertEquals("dy_pid_888", params.get("pid"));

        String json = """
                {
                  "code": 10000,
                  "msg": "success",
                  "data": {
                    "cursor": "cursor_next_12345",
                    "orders": [
                      {
                        "order_id": "4987654321098765432",
                        "parent_order_id": "4987654321098765430",
                        "product_id": "3589912345678",
                        "product_name": "抖音爆款高品质保温杯",
                        "product_img": "https://p3.douyinpic.com/item/1.jpg",
                        "pay_goods_amount": 2990,
                        "estimated_commission": 398,
                        "commission_rate": 20.0,
                        "item_num": 1,
                        "flow_point": "PAY_SUCCESS",
                        "status": 1,
                        "order_time": "2026-10-25 15:30:00",
                        "pay_success_time": "2026-10-25 15:30:05",
                        "pid": "dy_pid_888",
                        "external_info": "member_uid_99",
                        "refund_tag": 0
                      },
                      {
                        "order_id": "4987654321098765433",
                        "product_id": "3589912345679",
                        "product_name": "退款订单测试",
                        "pay_goods_amount": 1000,
                        "flow_point": "REFUND",
                        "status": 4,
                        "refund_tag": 1
                      }
                    ]
                  }
                }
                """;

        JsonNode response = objectMapper.readTree(json);
        List<CpsOrderDTO> orders = client.parseOrderQueryResponse(response);

        assertEquals(2, orders.size());

        CpsOrderDTO order1 = orders.get(0);
        assertEquals("4987654321098765432", order1.getPlatformOrderId());
        assertEquals("douyin", order1.getPlatformCode());
        assertEquals("official", order1.getVendorCode());
        assertEquals("3589912345678", order1.getItemId());
        assertEquals(new BigDecimal("29.90"), order1.getFinalPrice());
        assertEquals(new BigDecimal("3.98"), order1.getCommissionAmount());
        assertEquals(new BigDecimal("20.0"), order1.getCommissionRate());
        assertEquals(1, order1.getPlatformStatus()); // PAID
        assertEquals(0, order1.getRefundTag());
        assertEquals("dy_pid_888", order1.getAdzoneId());
        assertEquals("member_uid_99", order1.getExternalId());
        assertEquals("cursor_next_12345", order1.getNextPositionIndex());

        CpsOrderDTO order2 = orders.get(1);
        assertEquals(-1, order2.getPlatformStatus()); // INVALID / REFUNDED
        assertEquals(1, order2.getRefundTag());
    }

    @Test
    @DisplayName("响应成功判断与错误码校验")
    void isSuccessResponse() throws Exception {
        assertTrue(client.isSuccessResponse(objectMapper.readTree("{\"code\": 10000, \"msg\": \"success\"}")));
        assertTrue(client.isSuccessResponse(objectMapper.readTree("{\"code\": 0, \"msg\": \"success\"}")));
        assertFalse(client.isSuccessResponse(objectMapper.readTree("{\"code\": 40002, \"msg\": \"invalid app_key\"}")));
        assertFalse(client.isSuccessResponse(objectMapper.readTree("{\"error_response\": {\"code\": 500}}")));
        assertFalse(client.isSuccessResponse(null));
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
    @DisplayName("连接测试成功：Mock HTTP 200 与平台成功响应")
    @SuppressWarnings("unchecked")
    void testConnection_success() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"code\":10000,\"msg\":\"success\",\"data\":{\"products\":[]}}");
        org.mockito.Mockito.doReturn(mockResponse).when(mockHttpClient).send(any(), any());

        DouyinOfficialVendorClient testClient = new DouyinOfficialVendorClient(mockHttpClient);
        CpsVendorConfig config = CpsVendorConfig.builder()
                .appKey("test_app_key")
                .appSecret("test_app_secret")
                .build();

        boolean ok = testClient.testConnection(config);
        assertTrue(ok);
    }

    @Test
    @DisplayName("连接测试失败：上游返回业务错误码")
    @SuppressWarnings("unchecked")
    void testConnection_failure() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"code\":40002,\"msg\":\"invalid app_key\"}");
        org.mockito.Mockito.doReturn(mockResponse).when(mockHttpClient).send(any(), any());

        DouyinOfficialVendorClient testClient = new DouyinOfficialVendorClient(mockHttpClient);
        CpsVendorConfig config = CpsVendorConfig.builder()
                .appKey("wrong_key")
                .appSecret("wrong_secret")
                .build();

        boolean ok = testClient.testConnection(config);
        assertFalse(ok);
    }

}
