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
                Objects.toString(server.getAuthType(), ""), Objects.toString(server.getTimeoutMillis(), ""));
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(material);
    }

    protected McpSyncClient createClient(AiMcpServerDO server) {
        validateConnection(server);
        URI uri = URI.create(server.getEndpoint());
        String origin = uri.getScheme() + "://" + uri.getRawAuthority();
        String path = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        Duration timeout = Duration.ofMillis(server.getTimeoutMillis() == null ? 10000 : server.getTimeoutMillis());
        HttpRequest.Builder request = HttpRequest.newBuilder().timeout(timeout);
        if ("BEARER".equals(server.getAuthType())) {
            String token = System.getenv(server.getSecretRef().substring(4));
            if (token == null || token.isBlank()) throw new IllegalArgumentException("MCP 密钥引用未配置");
            request.header("Authorization", "Bearer " + token);
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
        if (!Set.of("NONE", "BEARER").contains(Objects.toString(server.getAuthType(), "NONE")))
            throw new IllegalArgumentException("认证方式不支持");
        if ("BEARER".equals(server.getAuthType()) && (server.getSecretRef() == null
                || !server.getSecretRef().matches("env:AI_MCP_TENANT_" + TenantContextHolder.getRequiredTenantId() + "_[A-Z0-9_]+")))
            throw new IllegalArgumentException("密钥必须引用当前租户的 env:AI_MCP_TENANT_<租户编号>_* 环境变量");
        if (!"BEARER".equals(server.getAuthType()) && server.getSecretRef() != null && !server.getSecretRef().isBlank())
            throw new IllegalArgumentException("无认证服务不能设置密钥");
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
