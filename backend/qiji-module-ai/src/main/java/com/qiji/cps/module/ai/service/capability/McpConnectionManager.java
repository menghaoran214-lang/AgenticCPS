package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiMcpServerDO;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lazy, tenant-scoped connections. No user-supplied executable or plaintext credentials. */
@Component
public class McpConnectionManager {
    private record Key(Long tenantId, Long serverId) { }
    private record Entry(String fingerprint, McpSyncClient client) { }
    private final Map<Key, Entry> clients = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Key, Object> locks = new java.util.concurrent.ConcurrentHashMap<>();

    public McpSyncClient getClient(AiMcpServerDO server) {
        if (!Integer.valueOf(0).equals(server.getStatus())) throw new IllegalArgumentException("MCP 服务已停用");
        Key key = new Key(TenantContextHolder.getRequiredTenantId(), server.getId());
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
        String fingerprint = fingerprint(server);
        Entry entry = clients.get(key);
        if (entry != null && fingerprint.equals(entry.fingerprint())) return entry.client();
        if (entry != null) { clients.remove(key); closeQuietly(entry.client()); }
        McpSyncClient client = createClient(server);
        try { client.initialize(); } catch (RuntimeException ex) { closeQuietly(client); throw new IllegalStateException("MCP 连接失败，请检查配置和服务状态"); }
        clients.put(key, new Entry(fingerprint, client));
        return client;
        }
    }

    /** Connection identity excludes operational health fields and resolved secret values. */
    public static String fingerprint(AiMcpServerDO server) {
        String material = String.join("|", Objects.toString(server.getCode(), ""),
                Objects.toString(server.getLocation(), ""), Objects.toString(server.getEndpoint(), ""),
                Objects.toString(server.getTransportProtocol(), ""), Objects.toString(server.getSecretRef(), ""),
                Objects.toString(server.getAuthType(), ""), Objects.toString(server.getTimeoutMillis(), ""),
                server.getSecretValue() == null ? "" : cn.hutool.crypto.digest.DigestUtil.sha256Hex(server.getSecretValue()));
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(material);
    }

    protected McpSyncClient createClient(AiMcpServerDO server) {
        validateConnection(server);
        URI uri = URI.create(server.getEndpoint());
        String origin = uri.getScheme() + "://" + uri.getRawAuthority();
        String path = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        Duration timeout = Duration.ofMillis(server.getTimeoutMillis() == null ? 10000 : server.getTimeoutMillis());
        HttpRequest.Builder request = HttpRequest.newBuilder().timeout(timeout);
        if ("BEARER".equals(server.getAuthType()) || "API_KEY".equals(server.getAuthType())) {
            String token = resolveSecret(server);
            if ("BEARER".equals(server.getAuthType())) request.header("Authorization", "Bearer " + token);
            else request.header("X-API-Key", token);
        }
        McpClientTransport transport = "SSE".equals(server.getTransportProtocol())
                ? HttpClientSseClientTransport.builder(origin).sseEndpoint(path).connectTimeout(timeout).requestBuilder(request).build()
                : HttpClientStreamableHttpTransport.builder(origin).endpoint(path).connectTimeout(timeout).requestBuilder(request).build();
        return McpClient.sync(transport).requestTimeout(timeout).initializationTimeout(timeout)
                .clientInfo(new McpSchema.Implementation("capability-center", "1.0")).build();
    }

    public McpSyncClient createInspectionClient(AiMcpServerDO server) {
        McpSyncClient client = createClient(server);
        try {
            client.initialize();
            return client;
        } catch (RuntimeException failure) {
            closeQuietly(client);
            throw failure;
        }
    }

    public static void validateConnection(AiMcpServerDO server) {
        if (!Set.of("SSE", "STREAMABLE_HTTP").contains(Objects.toString(server.getTransportProtocol(), "")))
            throw new IllegalArgumentException("仅支持 SSE 或 STREAMABLE_HTTP");
        URI uri;
        try { uri = URI.create(server.getEndpoint()); } catch (RuntimeException e) { throw new IllegalArgumentException("MCP 地址无效"); }
        if (!Set.of("https", "http").contains(Objects.toString(uri.getScheme(), "")) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("MCP 地址必须为无凭证、无查询参数的 HTTP 地址");
        if ("LOCAL".equals(server.getLocation()) && "cps".equals(server.getCode())
                && !Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("本地 CPS 身份签名仅允许发送到回环地址");
        if (server.getTimeoutMillis() != null && (server.getTimeoutMillis() < 1000 || server.getTimeoutMillis() > 60000))
            throw new IllegalArgumentException("超时必须在 1000 至 60000 毫秒之间");
        String authType = Objects.toString(server.getAuthType(), "NONE");
        if (!Set.of("NONE", "BEARER", "API_KEY").contains(authType))
            throw new IllegalArgumentException("认证方式不支持");
        if ("BEARER".equals(authType) && !isTenantSecretRef(server.getSecretRef()))
            throw new IllegalArgumentException("Bearer 密钥必须引用当前租户的 env:AI_MCP_TENANT_<租户编号>_* 环境变量");
        if ("API_KEY".equals(authType) && isBlank(server.getSecretValue()) && !isTenantSecretRef(server.getSecretRef()))
            throw new IllegalArgumentException("API Key 请直接填写密钥，或引用当前租户的环境变量");
        if ("NONE".equals(authType) && ((!isBlank(server.getSecretValue()))
                || (server.getSecretRef() != null && !server.getSecretRef().isBlank())))
            throw new IllegalArgumentException("无认证服务不能设置密钥");
    }

    private static boolean isTenantSecretRef(String secretRef) {
        return secretRef != null && secretRef.matches("env:AI_MCP_TENANT_"
                + TenantContextHolder.getRequiredTenantId() + "_[A-Z0-9_]+");
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }

    private static String resolveSecret(AiMcpServerDO server) {
        if (!isBlank(server.getSecretValue())) return server.getSecretValue();
        String token = System.getenv(server.getSecretRef().substring(4));
        if (isBlank(token)) throw new IllegalArgumentException("MCP 密钥未配置");
        return token;
    }

    /**
     * Converts transport exceptions into a short, safe operator-facing diagnosis.
     * Upstream exception text may contain authorization headers or endpoint details.
     */
    static String describeFailure(Throwable failure) {
        String text = collectMessages(failure).toLowerCase(Locale.ROOT);
        if (text.contains("上游服务暂时不可用") || text.contains("上游拒绝认证")
                || text.contains("上游 endpoint 不存在") || text.contains("上游服务限流")
                || text.contains("连接超时") || text.contains("连接被拒绝"))
            return collectMessages(failure).trim();
        Matcher status = Pattern.compile("status code\\s*[:=]\\s*(\\d{3})").matcher(text);
        if (status.find()) {
            int code = Integer.parseInt(status.group(1));
            if (code == 401 || code == 403) return "上游拒绝认证（HTTP " + code + "），请检查认证方式和密钥";
            if (code == 404) return "上游 Endpoint 不存在（HTTP 404），请检查地址与传输协议";
            if (code == 429) return "上游服务限流（HTTP 429），请稍后重试";
            if (code >= 500) return "上游服务暂时不可用（HTTP " + code + "），请检查上游服务状态或网络";
            return "上游返回异常（HTTP " + code + "），请检查 MCP 服务配置";
        }
        if (text.contains("timeout") || text.contains("timed out") || text.contains("connection timeout"))
            return "连接超时，请检查 Endpoint、网络和超时设置";
        if (text.contains("connection refused") || text.contains("connectexception"))
            return "连接被拒绝，请确认 MCP 服务已启动并可从当前服务器访问";
        if (text.contains("mcp 地址") || text.contains("传输协议") || text.contains("认证方式")
                || text.contains("密钥") || text.contains("超时必须"))
            return "MCP 连接配置无效，请检查 Endpoint、传输协议和认证配置";
        if (text.contains("服务不存在") || text.contains("服务已停用"))
            return "MCP 服务不存在或已停用";
        return "连接或 MCP 握手失败，请检查 Endpoint、传输协议和认证配置";
    }

    private static String collectMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            if (current.getMessage() != null) messages.append(' ').append(current.getMessage());
        }
        return messages.toString();
    }

    public void invalidate(Long id) {
        Key key = new Key(TenantContextHolder.getRequiredTenantId(), id);
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            Entry entry = clients.remove(key);
            if (entry != null) closeQuietly(entry.client());
        }
    }
    @PreDestroy public synchronized void close() { clients.values().forEach(entry -> closeQuietly(entry.client())); clients.clear(); }
    private static void closeQuietly(McpSyncClient client) { try { client.close(); } catch (RuntimeException ignored) { } }
}
