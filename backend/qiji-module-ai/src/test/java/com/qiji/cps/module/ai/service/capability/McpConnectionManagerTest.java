package com.qiji.cps.module.ai.service.capability;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiMcpServerDO;
import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class McpConnectionManagerTest {
 @Test void tenantCannotReferenceAnotherTenantsSecret() {
  TenantContextHolder.setTenantId(2L);
  try {
   var server = new AiMcpServerDO().setTransportProtocol("SSE").setAuthType("BEARER")
     .setEndpoint("https://example.com/sse").setSecretRef("env:AI_MCP_TENANT_1_HAINA_API_KEY");
   assertThrows(IllegalArgumentException.class,()->McpConnectionManager.validateConnection(server));
   server.setSecretRef("env:AI_MCP_TENANT_2_HAINA_API_KEY");
   assertDoesNotThrow(()->McpConnectionManager.validateConnection(server));
  } finally {TenantContextHolder.clear();}
 }
 @Test void apiKeyMayBeEnteredDirectly() {
  var server = new AiMcpServerDO().setTransportProtocol("SSE").setAuthType("API_KEY")
    .setEndpoint("https://example.com/sse").setSecretValue("haina-key");
  assertDoesNotThrow(() -> McpConnectionManager.validateConnection(server));
 }
 @Test void unsupportedAuthTypeIsRejected() {
  var server = new AiMcpServerDO().setTransportProtocol("SSE").setAuthType("BASIC")
    .setEndpoint("https://example.com/sse");
  assertThrows(IllegalArgumentException.class, () -> McpConnectionManager.validateConnection(server));
 }
 @Test void describesUpstreamFailuresWithoutLeakingTransportDetails() {
  var failure = new IllegalStateException(
    "Invalid SSE response. Status code: 503 Line: upstream connect error; Authorization: secret-value");
  assertEquals("上游服务暂时不可用（HTTP 503），请检查上游服务状态或网络",
    McpConnectionManager.describeFailure(failure));
  assertEquals("上游拒绝认证（HTTP 401），请检查认证方式和密钥",
    McpConnectionManager.describeFailure(new IllegalStateException("Status code: 401")));
  assertEquals("连接超时，请检查 Endpoint、网络和超时设置",
    McpConnectionManager.describeFailure(new IllegalStateException("connection timeout")));
 }
 @Test void signedLocalIdentityCannotBeRedirectedToAnExternalEndpoint() {
  AiMcpServerDO server=new AiMcpServerDO().setCode("cps").setLocation("LOCAL").setTransportProtocol("SSE")
    .setAuthType("NONE").setEndpoint("https://example.com/mcp");
  assertThrows(IllegalArgumentException.class,()->McpConnectionManager.validateConnection(server));
 }
 @Test void healthUpdatesDoNotReinitializeAConnection() {
  McpSyncClient client=mock(McpSyncClient.class);
  McpConnectionManager manager=new McpConnectionManager(){protected McpSyncClient createClient(AiMcpServerDO s){return client;}};
  AiMcpServerDO server=new AiMcpServerDO().setId(3L).setStatus(0);
  try { TenantContextHolder.setTenantId(1L);manager.getClient(server);server.setUpdateTime(java.time.LocalDateTime.now());manager.getClient(server);verify(client,times(1)).initialize(); }
  finally {manager.close();TenantContextHolder.clear();}
 }
 @Test void connectionsAreTenantScopedAndInvalidationClosesOnlyCurrentTenant() {
  McpSyncClient a=mock(McpSyncClient.class), b=mock(McpSyncClient.class);
  McpConnectionManager manager=new McpConnectionManager(){ protected McpSyncClient createClient(AiMcpServerDO s){return TenantContextHolder.getRequiredTenantId()==1L?a:b;} };
  AiMcpServerDO server=new AiMcpServerDO().setId(3L).setStatus(0);
  try { TenantContextHolder.setTenantId(1L);assertSame(a,manager.getClient(server));TenantContextHolder.setTenantId(2L);assertSame(b,manager.getClient(server));manager.invalidate(3L);verify(b).close();verify(a,never()).close(); } finally {manager.close();TenantContextHolder.clear();}
 }
 @Test void disabledServerNeverConnects() {McpConnectionManager manager=new McpConnectionManager();assertThrows(IllegalArgumentException.class,()->manager.getClient(new AiMcpServerDO().setStatus(1)));}
}
