package com.qiji.cps.module.cps.client.official.douyin;

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
import java.math.RoundingMode;
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
 * 抖音联盟官方 API 供应商客户端（抖店/巨量百应开放平台）
 *
 * <p>对接抖音电商/精选联盟/抖客官方 OpenAPI：</p>
 * <ul>
 *   <li>网关地址：https://openapi-fxg.jinritemai.com</li>
 *   <li>精选联盟商品搜索：/buyin/kolMaterialsProductsSearch (buyin.kolMaterialsProductsSearch)</li>
 *   <li>抖客口令解析与转链：/buyin/doukeCommandParseAndShare (buyin.doukeCommandParseAndShare)</li>
 *   <li>抖客订单明细列表：/buyin/doukeOrderList (buyin.doukeOrderList)</li>
 *   <li>认证与签名：HMAC-SHA256 签名机制，支持 OAuth2 Access Token 鉴权</li>
 * </ul>
 *
 * @author CPS System
 */
@Slf4j
@Component
public class DouyinOfficialVendorClient extends AbstractOfficialVendorClient {

    private static final String SEARCH_PATH = "/buyin/kolMaterialsProductsSearch";
    private static final String PROMOTION_LINK_PATH = "/buyin/doukeCommandParseAndShare";
    private static final String ORDER_QUERY_PATH = "/buyin/doukeOrderList";

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HttpClient httpClient;

    public DouyinOfficialVendorClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public DouyinOfficialVendorClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getPlatformCode() {
        return CpsPlatformCodeEnum.DOUYIN.getCode();
    }

    @Override
    public CpsVendorGovernancePolicy getGovernancePolicy() {
        return CpsVendorGovernancePolicy.builder()
                .timeoutMillis(5000)
                .rateLimitPerMinute(120)
                .circuitBreakerOpenMillis(10000)
                .metricsEnabled(true)
                .build();
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
                CpsVendorConfigField.optional("authToken", true),
                CpsVendorConfigField.optional("defaultAdzoneId", false),
                CpsVendorConfigField.optional("apiBaseUrl", false),
                CpsVendorConfigField.optional("timeoutMs", false)
        ));
    }

    @Override
    protected String getSearchApiPath() {
        return SEARCH_PATH;
    }

    @Override
    protected String getPromotionLinkApiPath() {
        return PROMOTION_LINK_PATH;
    }

    @Override
    protected String getOrderQueryApiPath() {
        return ORDER_QUERY_PATH;
    }

    @Override
    protected String getTestConnectionApiPath() {
        return SEARCH_PATH;
    }

    // ==================== 商品搜索 (buyin.kolMaterialsProductsSearch) ====================

    @Override
    protected Map<String, Object> buildSearchParams(CpsGoodsSearchRequest request, CpsVendorConfig config) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (StringUtils.hasText(request.getKeyword())) {
            params.put("title", request.getKeyword());
        }
        params.put("page", request.getPageNo() != null ? request.getPageNo() : 1);
        params.put("page_size", request.getPageSize() != null ? request.getPageSize() : 20);

        if (request.getSortType() != null) {
            // 0: 综合, 1: 佣金率, 2: 价格升序, 3: 价格降序, 4: 销量
            params.put("sort_type", request.getSortType());
        }
        if (request.getPriceLowerLimit() != null) {
            params.put("price_min", request.getPriceLowerLimit().multiply(BigDecimal.valueOf(100)).longValue());
        }
        if (request.getPriceUpperLimit() != null) {
            params.put("price_max", request.getPriceUpperLimit().multiply(BigDecimal.valueOf(100)).longValue());
        }
        if (request.getMinCommissionRate() != null) {
            params.put("cos_ratio_min", request.getMinCommissionRate().doubleValue());
        }
        return params;
    }

    @Override
    protected CpsGoodsSearchResult parseSearchResponse(JsonNode response, CpsGoodsSearchRequest request) {
        JsonNode data = response.path("data");
        List<CpsGoodsItem> list = new ArrayList<>();

        JsonNode products = firstArray(data, "products", "list", "dataList");
        if (products != null && products.isArray()) {
            for (JsonNode item : products) {
                list.add(mapGoods(item));
            }
        }

        long total = firstLong(data, "/total", "/total_count");
        if (total <= 0) {
            total = list.size();
        }

        return CpsGoodsSearchResult.builder()
                .list(list)
                .total(total)
                .pageNo(request.getPageNo() != null ? request.getPageNo() : 1)
                .pageSize(request.getPageSize() != null ? request.getPageSize() : 20)
                .build();
    }

    private CpsGoodsItem mapGoods(JsonNode item) {
        String goodsId = firstText(item, "/product_id", "/productId", "/id");
        String title = firstText(item, "/title", "/product_name", "/name");
        String mainPic = firstText(item, "/cover", "/img_url", "/image", "/pic");

        BigDecimal originalPrice = toYuan(item, "market_price", "original_price");
        BigDecimal actualPrice = toYuan(item, "price", "discount_price");
        if (originalPrice == null && actualPrice != null) {
            originalPrice = actualPrice;
        }

        BigDecimal couponPrice = toYuan(item, "coupon_price", "coupon_amount");
        BigDecimal commissionRate = parseCommissionRate(item, "cos_ratio", "commission_rate");
        BigDecimal commissionAmount = toYuan(item, "cos_fee", "commission_amount");

        if (commissionAmount == null && actualPrice != null && commissionRate != null) {
            commissionAmount = actualPrice.multiply(commissionRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        long sales = firstLong(item, "/sales", "/month_sales", "/volume");
        String shopName = firstText(item, "/shop_name", "/shopName");
        String categoryName = firstText(item, "/first_cname", "/category_name", "/categoryName");

        String itemLink = firstText(item, "/detail_url", "/product_url");
        if (!StringUtils.hasText(itemLink) && StringUtils.hasText(goodsId)) {
            itemLink = "https://haohuo.jinritemai.com/views/product/item2?id=" + goodsId;
        }

        Map<String, Object> extraFields = new LinkedHashMap<>();
        if (item.has("first_cid")) {
            extraFields.put("firstCid", item.path("first_cid").asText());
        }
        if (item.has("second_cid")) {
            extraFields.put("secondCid", item.path("second_cid").asText());
        }

        return CpsGoodsItem.builder()
                .goodsId(goodsId)
                .platformCode(getPlatformCode())
                .vendorCode(getVendorCode())
                .title(title)
                .mainPic(mainPic)
                .originalPrice(originalPrice)
                .actualPrice(actualPrice)
                .couponPrice(couponPrice)
                .commissionRate(commissionRate)
                .commissionAmount(commissionAmount)
                .monthSales(sales)
                .shopName(shopName)
                .categoryName(categoryName)
                .itemLink(itemLink)
                .source(getVendorCode())
                .extraFields(extraFields)
                .rawPayload(item.toString())
                .build();
    }

    // ==================== 推广转链 (buyin.doukeCommandParseAndShare) ====================

    @Override
    protected Map<String, Object> buildPromotionLinkParams(CpsPromotionLinkRequest request, CpsVendorConfig config) {
        Map<String, Object> params = new LinkedHashMap<>();

        // 若有原始口令优先传入 command，否则使用商品链接
        if (StringUtils.hasText(request.getOriginalContent())) {
            params.put("command", request.getOriginalContent());
        } else if (StringUtils.hasText(request.getItemLink())) {
            params.put("product_url", request.getItemLink());
        } else if (StringUtils.hasText(request.getGoodsId())) {
            params.put("product_url", "https://haohuo.jinritemai.com/views/product/item2?id=" + request.getGoodsId());
        }

        String pid = StringUtils.hasText(request.getAdzoneId())
                ? request.getAdzoneId()
                : (config != null ? config.getDefaultAdzoneId() : null);
        if (StringUtils.hasText(pid)) {
            params.put("pid", pid);
        }

        if (StringUtils.hasText(request.getExternalId())) {
            params.put("external_info", request.getExternalId());
        }

        return params;
    }

    @Override
    protected CpsPromotionLinkResult parsePromotionLinkResponse(JsonNode response) {
        JsonNode data = response.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return null;
        }

        String tpwd = firstText(data, "/share_command", "/command", "/token", "/dy_password");
        String shortUrl = firstText(data, "/share_url", "/short_url", "/url");
        String longUrl = firstText(data, "/long_url", "/origin_url");
        String mobileUrl = firstText(data, "/deep_link", "/zlink", "/scheme_url");

        JsonNode productInfo = data.path("product_info");
        BigDecimal actualPrice = null;
        BigDecimal commissionRate = null;
        BigDecimal commissionAmount = null;

        if (!productInfo.isMissingNode() && !productInfo.isNull()) {
            actualPrice = toYuan(productInfo, "price", "discount_price");
            commissionRate = parseCommissionRate(productInfo, "cos_ratio", "commission_rate");
            commissionAmount = toYuan(productInfo, "cos_fee", "commission_amount");
        }

        Map<String, Object> extraFields = new LinkedHashMap<>();
        if (StringUtils.hasText(tpwd)) {
            extraFields.put("command", tpwd);
        }
        if (StringUtils.hasText(mobileUrl)) {
            extraFields.put("deepLink", mobileUrl);
        }

        return CpsPromotionLinkResult.builder()
                .shortUrl(shortUrl)
                .longUrl(longUrl)
                .tpwd(tpwd)
                .mobileUrl(mobileUrl)
                .actualPrice(actualPrice)
                .commissionRate(commissionRate)
                .commissionAmount(commissionAmount)
                .extraFields(extraFields)
                .rawPayload(response.toString())
                .build();
    }

    // ==================== 订单查询 (buyin.doukeOrderList) ====================

    @Override
    protected Map<String, Object> buildOrderQueryParams(CpsOrderQueryRequest request, CpsVendorConfig config) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (StringUtils.hasText(request.getStartTime())) {
            params.put("start_time", request.getStartTime());
        }
        if (StringUtils.hasText(request.getEndTime())) {
            params.put("end_time", request.getEndTime());
        }
        params.put("page", request.getPageNo() != null ? request.getPageNo() : 1);
        params.put("page_size", request.getPageSize() != null ? request.getPageSize() : 50);

        if (config != null && StringUtils.hasText(config.getDefaultAdzoneId())) {
            params.put("pid", config.getDefaultAdzoneId());
        }
        if (StringUtils.hasText(request.getPositionIndex())) {
            params.put("cursor", request.getPositionIndex());
        }
        return params;
    }

    @Override
    protected List<CpsOrderDTO> parseOrderQueryResponse(JsonNode response) {
        JsonNode data = response.path("data");
        List<CpsOrderDTO> list = new ArrayList<>();

        JsonNode orders = firstArray(data, "orders", "order_list", "list", "items");
        if (orders != null && orders.isArray()) {
            String cursor = firstText(data, "/cursor", "/next_cursor", "/next_page");
            for (JsonNode item : orders) {
                list.add(mapOrder(item, cursor));
            }
        }
        return list;
    }

    @Override
    public CpsOrderPageResult queryOrderPage(CpsOrderQueryRequest request, CpsVendorConfig config) {
        JsonNode response = executeRequest(getOrderQueryApiPath(), buildOrderQueryParams(request, config), config);
        if (response == null || !isSuccessResponse(response)) {
            throw new CpsVendorException("CPS vendor order query failed [official:douyin]: upstream rejected request");
        }
        List<CpsOrderDTO> orders = parseOrderQueryResponse(response);
        String cursor = firstText(response, "/data/cursor", "/cursor", "/data/next_cursor");
        Boolean hasMore = firstBoolean(response, "/data/has_more", "/has_more");

        if (cursor != null && (hasMore == null || hasMore)) {
            return CpsOrderPageResult.cursor(orders, cursor, true);
        }
        return CpsOrderPageResult.page(orders, null, false);
    }

    private CpsOrderDTO mapOrder(JsonNode item, String nextCursor) {
        String platformOrderId = firstText(item, "/order_id", "/orderId", "/id");
        String parentOrderId = firstText(item, "/parent_order_id", "/parentOrderId");
        String itemId = firstText(item, "/product_id", "/productId", "/goods_id");
        String itemTitle = firstText(item, "/product_name", "/productName", "/title");
        String itemPic = firstText(item, "/product_img", "/productImg", "/cover");

        BigDecimal finalPrice = toYuan(item, "pay_goods_amount", "payGoodsAmount", "total_pay_amount", "pay_amount");
        BigDecimal itemPrice = toYuan(item, "item_price", "price", "goods_price");
        if (itemPrice == null && finalPrice != null) {
            itemPrice = finalPrice;
        }

        BigDecimal commissionAmount = toYuan(item, "estimated_commission", "estimatedCommission", "cos_fee", "commission");
        BigDecimal commissionRate = parseCommissionRate(item, "commission_rate", "commissionRate", "cos_ratio");

        int quantity = firstInt(item, 1, "/item_num", "/itemNum", "/num");

        // 订单状态映射
        int rawStatus = parseOrderStatus(item);
        int refundTag = isRefunded(item, rawStatus) ? 1 : 0;

        String orderTime = formatDateTime(item, "order_time", "create_time");
        String payTime = formatDateTime(item, "pay_success_time", "pay_time");
        String settleTime = formatDateTime(item, "settle_time", "settled_time");

        String pid = firstText(item, "/pid", "/adzone_id");
        String externalId = firstText(item, "/external_info", "/external_id");

        Map<String, Object> extraFields = new LinkedHashMap<>();
        if (item.has("flow_point")) {
            extraFields.put("flowPoint", item.path("flow_point").asText());
        }
        if (item.has("shop_name")) {
            extraFields.put("shopName", item.path("shop_name").asText());
        }

        return CpsOrderDTO.builder()
                .platformOrderId(platformOrderId)
                .parentOrderId(parentOrderId)
                .platformCode(getPlatformCode())
                .vendorCode(getVendorCode())
                .itemId(itemId)
                .itemTitle(itemTitle)
                .itemPic(itemPic)
                .itemPrice(itemPrice)
                .finalPrice(finalPrice)
                .commissionRate(commissionRate)
                .commissionAmount(commissionAmount)
                .quantity(quantity)
                .platformStatus(rawStatus)
                .orderTime(orderTime)
                .payTime(payTime)
                .settleTime(settleTime)
                .adzoneId(pid)
                .externalId(externalId)
                .refundTag(refundTag)
                .nextPositionIndex(nextCursor)
                .extraFields(extraFields)
                .rawPayload(item.toString())
                .build();
    }

    private int parseOrderStatus(JsonNode item) {
        // flow_point 字段或 status 字段
        String flowPoint = firstText(item, "/flow_point", "/flowPoint", "/status_desc");
        if (StringUtils.hasText(flowPoint)) {
            if (flowPoint.contains("支付") || flowPoint.contains("PAY")) {
                return 1; // PAID
            }
            if (flowPoint.contains("收货") || flowPoint.contains("RECEIVE")) {
                return 2; // RECEIVED
            }
            if (flowPoint.contains("结算") || flowPoint.contains("SETTLE")) {
                return 3; // SETTLED
            }
            if (flowPoint.contains("退款") || flowPoint.contains("REFUND") || flowPoint.contains("失效")) {
                return -1; // INVALID
            }
        }

        int status = item.path("status").asInt(item.path("order_status").asInt(0));
        return switch (status) {
            case 1 -> 1;  // 已支付
            case 2 -> 2;  // 已收货
            case 3 -> 3;  // 已结算
            case 4, 5, -1 -> -1; // 退款/失效
            default -> status;
        };
    }

    private boolean isRefunded(JsonNode item, int rawStatus) {
        if (rawStatus == -1 || rawStatus == 4 || rawStatus == 5) {
            return true;
        }
        int refund = item.path("refund_tag").asInt(item.path("is_refund").asInt(0));
        return refund == 1;
    }

    // ==================== 连接测试 ====================

    @Override
    protected Map<String, Object> buildTestConnectionParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", "测试");
        params.put("page", 1);
        params.put("page_size", 1);
        return params;
    }

    // ==================== HTTP 请求执行 ====================

    @Override
    protected JsonNode executeRequest(String path, Map<String, Object> params, CpsVendorConfig config) {
        if (config == null || !StringUtils.hasText(config.getAppKey()) || !StringUtils.hasText(config.getAppSecret())) {
            throw new CpsVendorException("抖音联盟官方 API 缺少 appKey 或 appSecret");
        }

        try {
            // 从 path 中解析 method 名称，如 /buyin/kolMaterialsProductsSearch -> buyin.kolMaterialsProductsSearch
            String cleanPath = path.startsWith("/") ? path.substring(1) : path;
            String method = cleanPath.replace('/', '.');

            String paramJson = DouyinOpenApiSigner.serializeParamJson(params);
            Map<String, String> signedParams = DouyinOpenApiSigner.buildSignedParams(method, paramJson, config);

            String baseUrl = StringUtils.hasText(config.getApiBaseUrl())
                    ? config.getApiBaseUrl()
                    : DouyinOpenApiSigner.DEFAULT_BASE_URL;

            String requestUrl = DouyinOpenApiSigner.buildUrl(baseUrl, path, signedParams);

            int timeoutMs = resolveTimeoutMs(config);
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(requestUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[抖音联盟官方] HTTP请求异常: path={}, status={}", path, response.statusCode());
                return null;
            }

            return objectMapper.readTree(response.body());
        } catch (CpsVendorException e) {
            throw e;
        } catch (Exception e) {
            log.error("[抖音联盟官方] HTTP请求失败: path={}, error={}", path, e.getMessage());
            throw new CpsVendorException("抖音联盟官方 API 请求失败: " + e.getMessage(), e);
        }
    }

    @Override
    protected boolean isSuccessResponse(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return false;
        }
        int code = root.path("code").asInt(-1);
        String codeStr = root.path("code").asText("");
        boolean codeSuccess = code == 10000 || code == 0 || "10000".equals(codeStr) || "0".equals(codeStr);
        return codeSuccess && !root.has("error_response");
    }

    // ==================== 辅助方法 ====================

    private BigDecimal toYuan(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode f = node.path(field);
            if (!f.isMissingNode() && !f.isNull()) {
                String text = f.asText().trim();
                if (!text.isEmpty()) {
                    try {
                        BigDecimal val = new BigDecimal(text);
                        // 若为整数且大于等于100分（如 2990 表示 29.90元），转换为元
                        if (!text.contains(".") && val.compareTo(BigDecimal.valueOf(100)) >= 0) {
                            return val.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        }
                        return val;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return null;
    }

    private BigDecimal parseCommissionRate(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode f = node.path(field);
            if (!f.isMissingNode() && !f.isNull()) {
                String text = f.asText().trim();
                if (!text.isEmpty()) {
                    try {
                        BigDecimal val = new BigDecimal(text);
                        // 若为 0.20，转换为 20.0%
                        if (val.compareTo(BigDecimal.ONE) <= 0 && val.compareTo(BigDecimal.ZERO) > 0) {
                            return val.multiply(BigDecimal.valueOf(100));
                        }
                        return val;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return null;
    }

    private String firstText(JsonNode root, String... pointers) {
        for (String pointer : pointers) {
            JsonNode node = pointer.startsWith("/") ? root.at(pointer) : root.path(pointer);
            if (!node.isMissingNode() && !node.isNull() && StringUtils.hasText(node.asText())) {
                return node.asText().trim();
            }
        }
        return null;
    }

    private JsonNode firstArray(JsonNode root, String... fields) {
        if (root == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode node = field.startsWith("/") ? root.at(field) : root.path(field);
            if (node.isArray()) {
                return node;
            }
        }
        return null;
    }

    private long firstLong(JsonNode root, String... pointers) {
        for (String pointer : pointers) {
            JsonNode node = pointer.startsWith("/") ? root.at(pointer) : root.path(pointer);
            if (node.canConvertToLong()) {
                return node.asLong();
            }
        }
        return 0L;
    }

    private Boolean firstBoolean(JsonNode root, String... pointers) {
        for (String pointer : pointers) {
            JsonNode node = pointer.startsWith("/") ? root.at(pointer) : root.path(pointer);
            if (node.isBoolean()) {
                return node.asBoolean();
            }
            if (node.isTextual()) {
                String val = node.asText();
                if ("true".equalsIgnoreCase(val)) return true;
                if ("false".equalsIgnoreCase(val)) return false;
            }
        }
        return null;
    }

    private int firstInt(JsonNode root, int defaultValue, String... pointers) {
        for (String pointer : pointers) {
            JsonNode node = pointer.startsWith("/") ? root.at(pointer) : root.path(pointer);
            if (node.canConvertToInt()) {
                return node.asInt();
            }
        }
        return defaultValue;
    }

    private String formatDateTime(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode f = node.path(field);
            if (!f.isMissingNode() && !f.isNull()) {
                String val = f.asText().trim();
                if (val.isEmpty()) continue;
                if (val.contains("-") && val.contains(":")) {
                    return val;
                }
                try {
                    long epoch = Long.parseLong(val);
                    if (epoch > 0) {
                        if (epoch < 10_000_000_000L) {
                            epoch *= 1000;
                        }
                        return Instant.ofEpochMilli(epoch).atZone(SHANGHAI_ZONE).format(DATE_TIME_FORMATTER);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private int resolveTimeoutMs(CpsVendorConfig config) {
        if (config == null || config.getExtraConfig() == null) {
            return 5_000;
        }
        String raw = config.getExtraConfig().get("timeoutMs");
        try {
            return Math.max(500, Math.min(Integer.parseInt(raw), 30_000));
        } catch (Exception e) {
            return 5_000;
        }
    }

}
