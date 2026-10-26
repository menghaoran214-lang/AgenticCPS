package com.qiji.cps.module.cps.client.official.douyin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiji.cps.module.cps.client.dto.CpsVendorConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

/**
 * 抖音开放平台（抖店/巨量百应开放平台）OpenAPI 签名与请求辅助类
 *
 * <p>符合抖音电商开放平台官方规范：</p>
 * <ul>
 *   <li>网关地址：https://openapi-fxg.jinritemai.com</li>
 *   <li>公共参数：method, app_key, access_token, param_json, timestamp, v, sign, sign_method</li>
 *   <li>签名算法：hmac-sha256</li>
 *   <li>签名规则：参与签名的参数按 ASCII 升序排序，拼接为 key1value1key2value2...，
 *       前后拼接 app_secret 构成原始字符串：app_secret + sorted_str + app_secret，
 *       以 app_secret 为密钥计算 HMAC-SHA256 并转换为 64 位小写十六进制字符串。</li>
 * </ul>
 *
 * @author CPS System
 */
@Slf4j
public final class DouyinOpenApiSigner {

    public static final String DEFAULT_BASE_URL = "https://openapi-fxg.jinritemai.com";
    public static final String SIGN_METHOD_HMAC_SHA256 = "hmac-sha256";
    public static final String API_VERSION = "2";

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private DouyinOpenApiSigner() {
    }

    /**
     * 计算抖店开放平台 HMAC-SHA256 签名
     *
     * @param params    参与签名的参数表（不包含 sign 和 sign_method，不包含空值）
     * @param appSecret 开发者 AppSecret
     * @return 64 位小写十六进制签名串
     */
    public static String sign(Map<String, ?> params, String appSecret) {
        if (!StringUtils.hasText(appSecret)) {
            throw new IllegalArgumentException("抖音开放平台 AppSecret 不能为空");
        }
        try {
            // 1. 过滤空值与 sign/sign_method，按参数名 ASCII 升序排序
            TreeMap<String, String> sortedParams = new TreeMap<>();
            if (params != null) {
                for (Map.Entry<String, ?> entry : params.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (!StringUtils.hasText(key) || "sign".equalsIgnoreCase(key) || "sign_method".equalsIgnoreCase(key)) {
                        continue;
                    }
                    if (value == null) {
                        continue;
                    }
                    String strVal = String.valueOf(value).trim();
                    if (!strVal.isEmpty()) {
                        sortedParams.put(key, strVal);
                    }
                }
            }

            // 2. 拼接 key1value1key2value2...
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
                sb.append(entry.getKey()).append(entry.getValue());
            }

            // 3. 前后拼接 appSecret: appSecret + sortedStr + appSecret
            String rawString = appSecret + sb + appSecret;

            // 4. HMAC-SHA256 加密
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hashBytes = mac.doFinal(rawString.getBytes(StandardCharsets.UTF_8));

            // 5. 转换为 16 进制小写
            return bytesToHex(hashBytes);
        } catch (Exception e) {
            throw new IllegalStateException("抖音 OpenAPI 签名生成失败", e);
        }
    }

    /**
     * 构建包含签名在内的完整公共请求参数映射
     *
     * @param method    调用的 API 方法名称，如 buyin.kolMaterialsProductsSearch
     * @param paramJson 业务参数 JSON 串（必须为紧凑 JSON 格式）
     * @param config    供应商运行时配置
     * @return 完整的请求参数映射（包含 sign 和 sign_method）
     */
    public static Map<String, String> buildSignedParams(String method, String paramJson, CpsVendorConfig config) {
        if (config == null || !StringUtils.hasText(config.getAppKey()) || !StringUtils.hasText(config.getAppSecret())) {
            throw new IllegalArgumentException("抖音官方配置缺少 appKey 或 appSecret");
        }

        Map<String, String> params = new TreeMap<>();
        params.put("app_key", config.getAppKey());
        params.put("method", method);
        params.put("param_json", StringUtils.hasText(paramJson) ? paramJson : "{}");
        params.put("timestamp", ZonedDateTime.now(SHANGHAI_ZONE).format(TIME_FORMATTER));
        params.put("v", API_VERSION);

        if (StringUtils.hasText(config.getAuthToken())) {
            params.put("access_token", config.getAuthToken());
        }

        // 计算 sign
        String signature = sign(params, config.getAppSecret());
        params.put("sign", signature);
        params.put("sign_method", SIGN_METHOD_HMAC_SHA256);

        return params;
    }

    /**
     * 将业务参数对象转换为紧凑型 JSON 字符串
     */
    public static String serializeParamJson(Object bizParams) {
        if (bizParams == null) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(bizParams);
        } catch (Exception e) {
            log.warn("[抖音OpenAPI] 业务参数序列化失败，回退为空对象: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 构建带有 Query 参数的请求完整 URL
     */
    public static String buildUrl(String baseUrl, String path, Map<String, String> params) {
        String base = StringUtils.hasText(baseUrl) ? baseUrl.replaceAll("/+$", "") : DEFAULT_BASE_URL;
        String requestPath = path.startsWith("/") ? path : "/" + path;
        StringBuilder urlBuilder = new StringBuilder(base).append(requestPath);

        if (params != null && !params.isEmpty()) {
            urlBuilder.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) {
                    urlBuilder.append("&");
                }
                urlBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                first = false;
            }
        }
        return urlBuilder.toString();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

}
