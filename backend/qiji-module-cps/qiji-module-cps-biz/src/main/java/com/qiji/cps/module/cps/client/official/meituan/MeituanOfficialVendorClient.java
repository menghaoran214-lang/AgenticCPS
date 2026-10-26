package com.qiji.cps.module.cps.client.official.meituan;

import com.fasterxml.jackson.databind.JsonNode;
import com.qiji.cps.module.cps.client.CpsVendorCapability;
import com.qiji.cps.module.cps.client.CpsVendorConfigField;
import com.qiji.cps.module.cps.client.CpsVendorConfigSchema;
import com.qiji.cps.module.cps.client.CpsVendorException;
import com.qiji.cps.module.cps.client.CpsVendorGovernancePolicy;
import com.qiji.cps.module.cps.client.common.AbstractOfficialVendorClient;
import com.qiji.cps.module.cps.client.dto.*;
import com.qiji.cps.module.cps.enums.CpsPlatformCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 美团联盟官方 API 客户端（基于美团开放平台与 Shepherd API 网关签名接入）
 *
 * <p>核心接入接口：
 * <ul>
 *   <li>商品查询（到家/到店/外卖）：/query_coupon</li>
 *   <li>推广链接生成（活动/商品/长短链/小程序）：/get_referral_link</li>
 *   <li>订单明细查询（按支付/更新时间分批滚动拉取）：/query_order</li>
 * </ul>
 * </p>
 *
 * @author CPS System
 */
@Slf4j
@Component
public class MeituanOfficialVendorClient extends AbstractOfficialVendorClient {

    static final String DEFAULT_BASE_URL = "https://media.meituan.com/cps_open/common/api/v1";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HttpClient httpClient;

    public MeituanOfficialVendorClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public MeituanOfficialVendorClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getPlatformCode() {
        return CpsPlatformCodeEnum.MEITUAN.getCode();
    }

    @Override
    public Set<CpsVendorCapability> getCapabilities() {
        return EnumSet.of(
                CpsVendorCapability.GOODS_SEARCH,
                CpsVendorCapability.PROMOTION_LINK,
                CpsVendorCapability.ORDER_QUERY,
                CpsVendorCapability.CONNECTION_TEST
        );
    }

    @Override
    public CpsVendorConfigSchema getConfigSchema() {
        return new CpsVendorConfigSchema(List.of(
                CpsVendorConfigField.required("appKey", true),
                CpsVendorConfigField.required("appSecret", true),
                CpsVendorConfigField.optional("apiBaseUrl", false),
                CpsVendorConfigField.optional("defaultAdzoneId", false),
                CpsVendorConfigField.optional("timeoutMs", false),
                CpsVendorConfigField.optional("platform", false),
                CpsVendorConfigField.optional("bizLine", false),
                CpsVendorConfigField.optional("cityId", false),
                CpsVendorConfigField.optional("linkType", false),
                CpsVendorConfigField.optional("businessLine", false),
                CpsVendorConfigField.optional("tradeType", false)
        ));
    }

    @Override
    public CpsVendorGovernancePolicy getGovernancePolicy() {
        return CpsVendorGovernancePolicy.builder()
                .timeoutMillis(5000)
                .rateLimitPerMinute(120)
                .circuitBreakerOpenMillis(10000)
                .metricsEnabled(true)
                .maskedDiagnosticsEnabled(true)
                .build();
    }

    @Override
    protected String getSearchApiPath() {
        return "/query_coupon";
    }

    @Override
    protected String getPromotionLinkApiPath() {
        return "/get_referral_link";
    }

    @Override
    protected String getOrderQueryApiPath() {
        return "/query_order";
    }

    @Override
    protected String getTestConnectionApiPath() {
        return "/query_coupon";
    }

    @Override
    protected Map<String, Object> buildSearchParams(CpsGoodsSearchRequest request, CpsVendorConfig config) {
        Map<String, Object> params = new LinkedHashMap<>();
        put(params, "searchText", request.getKeyword());
        put(params, "pageNo", request.getPageNo());
        put(params, "pageSize", request.getPageSize());
        put(params, "platform", integerConfig(config, "platform", 1));
        put(params, "bizLine", integerConfig(config, "bizLine", null));
        put(params, "cityId", configValue(config, "cityId"));
        put(params, "sortField", request.getSortType() == null || request.getSortType() == 0 ? null
                : request.getSortType() == 1 ? 2 : request.getSortType() == 2 ? 1 : 3);
        if (request.getPriceLowerLimit() != null) {
            put(params, "priceMin", request.getPriceLowerLimit());
        }
        if (request.getPriceUpperLimit() != null) {
            put(params, "priceMax", request.getPriceUpperLimit());
        }
        return params;
    }

    @Override
    protected CpsGoodsSearchResult parseSearchResponse(JsonNode response, CpsGoodsSearchRequest request) {
        JsonNode data = response.path("data");
        List<CpsGoodsItem> items = new ArrayList<>();
        for (JsonNode item : arrayNode(data, "dataList", "list", "items")) {
            items.add(mapGoods(item));
        }
        if (data.isArray()) {
            items.clear();
            data.forEach(item -> items.add(mapGoods(item)));
        }
        long total = firstLong(response, "/total", "/data/total", "/data/totalCount");
        return CpsGoodsSearchResult.builder().list(items).total(total > 0 ? total : items.size())
                .nextPageId(firstText(response, "/searchId", "/data/searchId"))
                .pageNo(request.getPageNo()).pageSize(request.getPageSize()).build();
    }

    @Override
    protected Map<String, Object> buildPromotionLinkParams(CpsPromotionLinkRequest request, CpsVendorConfig config) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (StringUtils.hasText(request.getItemLink()) || StringUtils.hasText(request.getOriginalContent())) {
            put(params, "text", StringUtils.hasText(request.getItemLink())
                    ? request.getItemLink() : request.getOriginalContent());
        } else if (StringUtils.hasText(request.getGoodsId())) {
            String goodsId = request.getGoodsId().trim();
            if (goodsId.startsWith("act:") || goodsId.startsWith("act_") || goodsId.startsWith("activity:")) {
                String actId = goodsId.substring(goodsId.indexOf(':') > 0 ? goodsId.indexOf(':') + 1 : goodsId.indexOf('_') + 1);
                put(params, "actId", actId);
            } else {
                put(params, "productViewSign", goodsId);
            }
        }
        put(params, "sid", firstText(request.getAdzoneId(), firstText(request.getExternalId(), config == null ? null : config.getDefaultAdzoneId())));
        put(params, "linkType", integerConfig(config, "linkType", 1));
        put(params, "platform", integerConfig(config, "platform", 1));
        put(params, "bizLine", integerConfig(config, "bizLine", null));
        return params;
    }

    @Override
    protected CpsPromotionLinkResult parsePromotionLinkResponse(JsonNode response) {
        JsonNode data = response.path("data");
        String longUrl = data.isTextual() ? data.asText() : firstText(response,
                "/referralLinkMap/1", "/data/referralLinkMap/1", "/data/link", "/data/url");
        String shortUrl = firstText(response, "/referralLinkMap/2", "/data/referralLinkMap/2");
        if (!StringUtils.hasText(shortUrl) && StringUtils.hasText(longUrl)) {
            shortUrl = longUrl;
        } else if (!StringUtils.hasText(longUrl) && StringUtils.hasText(shortUrl)) {
            longUrl = shortUrl;
        }
        String mobileUrl = firstText(response, "/referralLinkMap/3", "/data/referralLinkMap/3");
        String wxMiniPath = firstText(response, "/referralLinkMap/4", "/data/referralLinkMap/4");
        String tpwd = firstText(response, "/referralLinkMap/5", "/data/referralLinkMap/5");

        Map<String, Object> extra = new LinkedHashMap<>();
        if (data.isObject()) {
            data.fields().forEachRemaining(entry -> extra.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class)));
        }
        put(extra, "skuViewId", firstText(response, "/skuViewId", "/data/skuViewId"));
        put(extra, "productViewSign", firstText(response, "/productViewSign", "/data/productViewSign"));
        if (StringUtils.hasText(wxMiniPath)) {
            put(extra, "wxMiniPath", wxMiniPath);
        }
        if (StringUtils.hasText(mobileUrl)) {
            put(extra, "deeplink", mobileUrl);
        }
        return CpsPromotionLinkResult.builder()
                .longUrl(longUrl)
                .shortUrl(shortUrl)
                .mobileUrl(mobileUrl)
                .tpwd(tpwd)
                .extraFields(extra)
                .rawPayload(toJson(response))
                .build();
    }

    @Override
    protected Map<String, Object> buildOrderQueryParams(CpsOrderQueryRequest request, CpsVendorConfig config) {
        Map<String, Object> params = new LinkedHashMap<>();
        put(params, "platform", integerConfig(config, "platform", 1));
        put(params, "businessLine", listConfig(config, "businessLine"));
        put(params, "queryTimeType", request.getQueryType() != null && request.getQueryType() == 4 ? 2 : 1);
        put(params, "tradeType", integerConfig(config, "tradeType", 1));
        put(params, "searchType", 2);
        put(params, "scrollId", request.getPositionIndex());
        put(params, "startTime", epochSeconds(request.getStartTime()));
        put(params, "endTime", epochSeconds(request.getEndTime()));
        put(params, "page", 1);
        put(params, "limit", request.getPageSize() != null ? Math.min(request.getPageSize(), 100) : 20);
        return params;
    }

    @Override
    protected List<CpsOrderDTO> parseOrderQueryResponse(JsonNode response) {
        JsonNode data = response.path("data");
        JsonNode list = data.path("dataList");
        if (!list.isArray()) {
            list = response.path("dataList");
        }
        if (!list.isArray() && data.isArray()) {
            list = data;
        }
        List<CpsOrderDTO> orders = new ArrayList<>();
        if (list.isArray()) {
            list.forEach(item -> orders.add(mapOrder(item, firstText(response, "/scrollId", "/data/scrollId"))));
        }
        return orders;
    }

    @Override
    public CpsOrderPageResult queryOrderPage(CpsOrderQueryRequest request, CpsVendorConfig config) {
        JsonNode response = executeRequest(getOrderQueryApiPath(), buildOrderQueryParams(request, config), config);
        if (response == null || !isSuccessResponse(response)) {
            throw new CpsVendorException("CPS vendor order query failed [official:meituan]: upstream rejected request");
        }
        List<CpsOrderDTO> orders = parseOrderQueryResponse(response);
        String scrollId = firstText(response, "/scrollId", "/data/scrollId");
        Boolean hasMore = firstBoolean(response, "/hasNext", "/data/hasNext");
        if (scrollId != null && (hasMore == null || hasMore)) {
            return CpsOrderPageResult.cursor(orders, scrollId, true);
        }
        return CpsOrderPageResult.page(orders, null, false);
    }

    @Override
    public List<CpsOrderDTO> queryOrders(CpsOrderQueryRequest request, CpsVendorConfig config) {
        JsonNode response = executeRequest(getOrderQueryApiPath(), buildOrderQueryParams(request, config), config);
        if (response == null || !isSuccessResponse(response)) {
            log.warn("[美团联盟官方] 订单批量查询失败");
            return Collections.emptyList();
        }
        return parseOrderQueryResponse(response);
    }

    @Override
    protected Map<String, Object> buildTestConnectionParams() {
        return Map.of("searchText", "美食", "pageNo", 1, "pageSize", 1);
    }

    @Override
    protected JsonNode executeRequest(String path, Map<String, Object> params, CpsVendorConfig config) {
        if (config == null || !StringUtils.hasText(config.getAppKey()) || !StringUtils.hasText(config.getAppSecret())) {
            throw new CpsVendorException("美团官方 API 缺少 appKey 或 appSecret");
        }
        try {
            String baseUrl = StringUtils.hasText(config.getApiBaseUrl()) ? config.getApiBaseUrl().trim() : DEFAULT_BASE_URL;
            URI baseUri = URI.create(baseUrl);
            String basePath = baseUri.getPath() != null ? baseUri.getPath().replaceAll("/+$", "") : "";
            String requestPath = path.startsWith("/") ? path : "/" + path;
            String fullPath = requestPath.startsWith(basePath) ? requestPath : basePath + requestPath;
            String url = baseUri.getScheme() + "://" + baseUri.getAuthority() + fullPath;
            String canonicalPath = fullPath;

            String body = objectMapper.writeValueAsString(params == null ? Map.of() : params);
            Map<String, String> signedHeaders = MeituanApiGatewaySigner.buildSignedHeaders(
                    config.getAppKey(), config.getAppSecret(), "POST", canonicalPath,
                    Map.of(), Map.of(), body);

            int timeoutMs = timeoutMs(config);
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            for (Map.Entry<String, String> entry : signedHeaders.entrySet()) {
                if (StringUtils.hasText(entry.getValue())) {
                    reqBuilder.header(entry.getKey(), entry.getValue());
                }
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[美团联盟官方] HTTP状态异常: status={}, body={}", response.statusCode(), response.body());
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (CpsVendorException e) {
            throw e;
        } catch (Exception e) {
            throw new CpsVendorException("美团官方 API 请求失败", e);
        }
    }

    @Override
    protected boolean isSuccessResponse(JsonNode root) {
        return root != null && root.path("code").asInt(-1) == 0;
    }

    private CpsGoodsItem mapGoods(JsonNode item) {
        JsonNode detail = item.path("couponPackDetail");
        BigDecimal original = decimal(detail, "originalPrice");
        BigDecimal actual = decimal(detail, "sellPrice");
        BigDecimal commissionRate = decimal(item.path("commissionInfo"), "commissionPercent");
        if (commissionRate != null) commissionRate = commissionRate.divide(BigDecimal.valueOf(100));
        return CpsGoodsItem.builder()
                .goodsId(firstText(item, "/productViewSign", "/skuViewId", "/couponPackDetail/productViewSign", "/couponPackDetail/skuViewId"))
                .platformCode(getPlatformCode())
                .vendorCode(getVendorCode())
                .title(text(detail, "name"))
                .mainPic(text(detail, "headUrl"))
                .originalPrice(original)
                .actualPrice(actual)
                .couponPrice(original != null && actual != null ? original.subtract(actual) : null)
                .commissionRate(commissionRate)
                .commissionAmount(decimal(item.path("commissionInfo"), "commission"))
                .monthSales(parseLong(text(detail, "saleVolume")))
                .brandName(text(item.path("brandInfo"), "brandName"))
                .categoryName(text(item, "categoryName"))
                .couponStartTime(formatEpoch(detail.path("startTime")))
                .couponEndTime(formatEpoch(detail.path("endTime")))
                .source(getVendorCode())
                .extraFields(goodsExtra(item))
                .rawPayload(toJson(item))
                .build();
    }

    private CpsOrderDTO mapOrder(JsonNode item, String scrollId) {
        int status = item.path("status").asInt(0);
        if (status != 0) {
            status = switch (status) {
                case 2 -> 1;
                case 3, 6, 7 -> 3;
                case 4, 5, 8 -> -1;
                default -> status;
            };
        }
        if (status == 0) {
            status = switch (item.path("couponStatus").asInt(0)) {
                case 1 -> 1;
                case 2 -> 2;
                case 3 -> 3;
                case 4 -> -1;
                default -> 0;
            };
        }
        BigDecimal profit = firstDecimal(item, "profit", "cpaProfit", "couponFee");
        BigDecimal rate = decimal(item, "commissionRate");
        if (rate != null) rate = rate.divide(BigDecimal.valueOf(100));

        JsonNode orderTimeNode = !item.path("orderTime").isMissingNode() && !item.path("orderTime").isNull()
                ? item.path("orderTime") : item.path("payTime");
        JsonNode payTimeNode = item.path("payTime");
        int quantity = item.path("quantity").asInt(1);
        if (quantity <= 0) {
            quantity = 1;
        }

        return CpsOrderDTO.builder()
                .platformOrderId(firstText(item, "/orderId", "/orderViewId", "/itemOrderId"))
                .platformCode(getPlatformCode())
                .vendorCode(getVendorCode())
                .itemId(firstText(item, "/productId", "/productViewSign"))
                .itemTitle(firstText(item, "/productName", "/itemName", "/title"))
                .finalPrice(firstDecimal(item, "payPrice", "basicAmount", "actualPrice"))
                .commissionRate(rate)
                .commissionAmount(profit)
                .quantity(quantity)
                .platformStatus(status)
                .orderTime(formatEpoch(orderTimeNode))
                .payTime(formatEpoch(payTimeNode))
                .adzoneId(text(item, "sid"))
                .refundTag(status == -1 || status == 4 || status == 5 || status == 8 ? 1 : 0)
                .nextPositionIndex(scrollId)
                .extraFields(orderExtra(item))
                .rawPayload(toJson(item))
                .build();
    }

    private Map<String, Object> orderExtra(JsonNode item) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("businessLine", item.path("businessLine").asInt());
        extra.put("tradeType", item.path("tradeType").asInt());
        String productViewSign = text(item, "productViewSign");
        if (StringUtils.hasText(productViewSign)) {
            extra.put("productViewSign", productViewSign);
        }
        String updateTime = formatEpoch(item.path("updateTime"));
        if (updateTime != null) {
            extra.put("updateTime", updateTime);
        }
        return extra;
    }

    private int timeoutMs(CpsVendorConfig config) {
        String raw = config.getExtraConfig() == null ? null : config.getExtraConfig().get("timeoutMs");
        try {
            return Math.max(500, Math.min(Integer.parseInt(raw), 30_000));
        } catch (Exception ignored) {
            return 5_000;
        }
    }

    private Integer integerConfig(CpsVendorConfig config, String key, Integer fallback) {
        String value = configValue(config, key);
        try {
            return value == null ? fallback : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private List<Integer> listConfig(CpsVendorConfig config, String key) {
        String raw = configValue(config, key);
        if (!StringUtils.hasText(raw)) return null;
        List<Integer> result = new ArrayList<>();
        for (String value : raw.split(",")) {
            try {
                result.add(Integer.valueOf(value.trim()));
            } catch (Exception ignored) {
            }
        }
        return result.isEmpty() ? null : result;
    }

    private String configValue(CpsVendorConfig config, String key) {
        return config == null || config.getExtraConfig() == null ? null : config.getExtraConfig().get(key);
    }

    private void put(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String text(JsonNode node, String field) {
        return node == null || node.path(field).isMissingNode() || node.path(field).isNull() ? null : node.path(field).asText();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        try {
            String value = text(node, field);
            return StringUtils.hasText(value) ? new BigDecimal(value) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal firstDecimal(JsonNode node, String... fields) {
        for (String field : fields) {
            BigDecimal value = decimal(node, field);
            if (value != null) return value;
        }
        return null;
    }

    private long firstLong(JsonNode root, String... pointers) {
        for (String pointer : pointers) {
            JsonNode value = root.at(pointer);
            if (value.canConvertToLong()) return value.asLong();
        }
        return 0L;
    }

    private Boolean firstBoolean(JsonNode root, String... pointers) {
        for (String pointer : pointers) {
            JsonNode value = root.at(pointer);
            if (value.isBoolean()) return value.asBoolean();
        }
        return null;
    }

    private String firstText(JsonNode root, String... pointers) {
        for (String pointer : pointers) {
            JsonNode value = root.at(pointer);
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText())) return value.asText();
        }
        return null;
    }

    private List<JsonNode> arrayNode(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isArray()) {
                List<JsonNode> list = new ArrayList<>();
                value.forEach(list::add);
                return list;
            }
        }
        return List.of();
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return node == null ? null : node.toString();
        }
    }

    private Map<String, Object> goodsExtra(JsonNode item) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("platform", item.path("platform").asInt(1));
        if (!item.path("bizLine").isNull() && !item.path("bizLine").isMissingNode()) {
            extra.put("bizLine", item.path("bizLine").asInt());
        }
        return extra;
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private Long epochSeconds(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return java.time.LocalDateTime.parse(value, DATE_TIME).atZone(SHANGHAI).toEpochSecond();
        } catch (Exception e) {
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private String formatEpoch(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            long value = node.asLong();
            if (value <= 0) return null;
            if (value < 10_000_000_000L) value *= 1000;
            return Instant.ofEpochMilli(value).atZone(SHANGHAI).format(DATE_TIME);
        } catch (Exception e) {
            return node.asText(null);
        }
    }
}
