package com.qiji.cps.module.cps.client.official.meituan;

import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 美团 API Gateway 签名实现，规则与官方 sign-java 示例保持一致。
 *
 * <p>规范参考：
 * <ul>
 *   <li>文档：https://media.meituan.com/pc/index.html#/help?path=API%E6%8E%A5%E5%85%A5%E6%8C%87%E5%8D%97</li>
 *   <li>Demo: shepherd-sign-java-demo (SignUtil / MessageDigestUtil / HttpUtil)</li>
 *   <li>签名串格式：HTTP_METHOD + "\n" + Content-MD5 + "\n" + 已签名请求头 + resource</li>
 *   <li>Content-MD5: 请求体 UTF-8 字节进行 MD5 摘要后再进行 Base64 编码</li>
 *   <li>Headers: 参与签名的 Header（如 S-Ca-App, S-Ca-Timestamp）按字典序升序排序，每行 key:value\n 拼接</li>
 *   <li>S-Ca-Signature-Headers: 所有参与签名的 Header 名称按字典序升序以英文逗号拼接</li>
 *   <li>Resource: path + 升序拼接的 query 参数（格式：?k1=v1&k2=v2，无 query 则不追加问号）</li>
 *   <li>Signature: 以 appSecret 为密钥对 StringToSign 计算 HmacSHA256，再进行 Base64 编码</li>
 * </ul>
 * </p>
 */
public final class MeituanApiGatewaySigner {

    public static final String HEADER_APP = "S-Ca-App";
    public static final String HEADER_TIMESTAMP = "S-Ca-Timestamp";
    public static final String HEADER_SIGNATURE = "S-Ca-Signature";
    public static final String HEADER_SIGNATURE_HEADERS = "S-Ca-Signature-Headers";
    public static final String HEADER_CONTENT_MD5 = "Content-MD5";
    public static final String HEADER_ACCEPT = "Accept";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";

    public static final List<String> DEFAULT_SIGNATURE_HEADERS = List.of(HEADER_APP, HEADER_TIMESTAMP);

    private MeituanApiGatewaySigner() {
    }

    /**
     * 计算请求体 MD5 摘要并进行 Base64 编码（与 shepherd 示例中的 MessageDigestUtil.base64AndMD5 一致）
     *
     * @param body 请求体字符串
     * @return Base64 编码的 MD5 摘要
     */
    public static String base64AndMd5(String body) {
        if (body == null) {
            return "";
        }
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 算法不可用", e);
        }
    }

    /**
     * 构建包含鉴权签名的完整公共请求头
     *
     * @param appKey        美团联盟 AppKey
     * @param appSecret     美团联盟 AppSecret
     * @param method        HTTP 方法（GET / POST 等）
     * @param canonicalPath 规范化请求路径（包含 basePath，例如 /cps_open/common/api/v1/query_coupon）
     * @param extraHeaders  外部自定义请求头（可选）
     * @param queryParams   Query 参数表（可选）
     * @param body          请求体 JSON 串（有请求体时计算 Content-MD5）
     * @return 包含 S-Ca-App, S-Ca-Timestamp, Content-MD5, S-Ca-Signature-Headers, S-Ca-Signature 的完整 Header 映射
     */
    public static Map<String, String> buildSignedHeaders(String appKey, String appSecret,
                                                         String method, String canonicalPath,
                                                         Map<String, String> extraHeaders,
                                                         Map<String, String> queryParams,
                                                         String body) {
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(appSecret)) {
            throw new IllegalArgumentException("美团 API 缺少 appKey 或 appSecret");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_ACCEPT, "application/json");
        headers.put(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);

        if (StringUtils.hasText(body)) {
            headers.put(HEADER_CONTENT_MD5, base64AndMd5(body));
        }

        headers.put(HEADER_APP, appKey);
        headers.put(HEADER_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }

        // 收集参与签名的 header 名称，默认包含 S-Ca-App 和 S-Ca-Timestamp
        List<String> signatureHeaders = new ArrayList<>(DEFAULT_SIGNATURE_HEADERS);
        if (extraHeaders != null) {
            for (String key : extraHeaders.keySet()) {
                if (key != null && (key.startsWith("S-Ca-") || key.startsWith("X-Ca-"))
                        && !signatureHeaders.contains(key)) {
                    signatureHeaders.add(key);
                }
            }
        }

        String sigHeadersValue = signatureHeaderValue(signatureHeaders);
        headers.put(HEADER_SIGNATURE_HEADERS, sigHeadersValue);

        String signature = sign(appSecret, method, canonicalPath, headers, queryParams, signatureHeaders);
        headers.put(HEADER_SIGNATURE, signature);

        return headers;
    }

    public static String sign(String secret, String method, String path,
                              Map<String, String> headers,
                              Map<String, String> queryParams,
                              List<String> signatureHeaders) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(
                    buildStringToSign(method, path, headers, queryParams, signatureHeaders)
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("美团 API Gateway 签名失败", e);
        }
    }

    public static String buildStringToSign(String method, String path,
                                           Map<String, String> headers,
                                           Map<String, String> queryParams,
                                           List<String> signatureHeaders) {
        StringBuilder result = new StringBuilder(method.toUpperCase()).append('\n');
        result.append(headers == null ? "" : value(headers, HEADER_CONTENT_MD5)).append('\n');

        if (headers != null && signatureHeaders != null) {
            List<String> names = new ArrayList<>(signatureHeaders);
            names.removeIf(name -> name == null || name.isBlank()
                    || HEADER_SIGNATURE.equalsIgnoreCase(name)
                    || HEADER_CONTENT_MD5.equalsIgnoreCase(name)
                    || HEADER_SIGNATURE_HEADERS.equalsIgnoreCase(name));
            names.sort(String.CASE_INSENSITIVE_ORDER);
            for (String name : names) {
                result.append(name).append(':').append(valueIgnoreCase(headers, name)).append('\n');
            }
        }

        result.append(path == null ? "" : path);
        if (queryParams != null && queryParams.entrySet().stream()
                .anyMatch(entry -> entry.getKey() != null && !entry.getKey().isBlank())) {
            result.append('?');
            boolean first = true;
            Map<String, String> sortedQuery = new TreeMap<>();
            queryParams.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    sortedQuery.put(key, value);
                }
            });
            for (Map.Entry<String, String> entry : sortedQuery.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                if (!first) {
                    result.append('&');
                }
                result.append(entry.getKey());
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    result.append('=').append(entry.getValue());
                }
                first = false;
            }
        }
        return result.toString();
    }

    public static String signatureHeaderValue(List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>(headers);
        names.removeIf(name -> name == null || name.isBlank()
                || HEADER_SIGNATURE.equalsIgnoreCase(name)
                || HEADER_CONTENT_MD5.equalsIgnoreCase(name)
                || HEADER_SIGNATURE_HEADERS.equalsIgnoreCase(name));
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(",", names);
    }

    private static String value(Map<String, String> headers, String name) {
        return valueIgnoreCase(headers, name);
    }

    private static String valueIgnoreCase(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue() == null ? "" : entry.getValue();
            }
        }
        return "";
    }
}
