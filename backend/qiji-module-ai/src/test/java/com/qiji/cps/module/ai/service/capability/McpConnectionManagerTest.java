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
