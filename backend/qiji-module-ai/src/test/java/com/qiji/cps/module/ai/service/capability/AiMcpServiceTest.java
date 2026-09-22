package com.qiji.cps.module.ai.service.capability;
import com.qiji.cps.module.ai.dal.mysql.capability.*;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
import com.qiji.cps.module.ai.controller.admin.capability.vo.*;
import com.qiji.cps.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiMcpServiceTest {
 @Test void missingServerNeverReportsHealthy() { AiMcpServiceImpl s=new AiMcpServiceImpl(); AiMcpServerMapper m=mock(AiMcpServerMapper.class); McpRegistryService registry=mock(McpRegistryService.class); ReflectionTestUtils.setField(s,"serverMapper",m); ReflectionTestUtils.setField(s,"registry",registry); assertThrows(IllegalArgumentException.class,()->s.testServer(1L)); verifyNoInteractions(registry); verify(m,never()).updateById(any(AiMcpServerDO.class)); }
 @Test void testFailureIsReturnedAsReadableBusinessError() {
  AiMcpServiceImpl s = new AiMcpServiceImpl();
  AiMcpServerMapper m = mock(AiMcpServerMapper.class);
  McpRegistryService registry = mock(McpRegistryService.class);
  ReflectionTestUtils.setField(s, "serverMapper", m);
  ReflectionTestUtils.setField(s, "registry", registry);
  when(m.selectById(3L)).thenReturn(new AiMcpServerDO().setId(3L));
  when(registry.inspect(3L)).thenThrow(new IllegalStateException(
      "上游服务暂时不可用（HTTP 503），请检查上游服务状态或网络"));

  ServiceException error = assertThrows(ServiceException.class, () -> s.testServer(3L));
  assertEquals(1_040_012_000, error.getCode());
  assertEquals("MCP 服务测试失败：上游服务暂时不可用（HTTP 503），请检查上游服务状态或网络", error.getMessage());
 }
 @Test void rejectsEmbeddedEndpointCredentialsBeforeWrite() { AiMcpServiceImpl s=new AiMcpServiceImpl(); AiMcpServerMapper m=mock(AiMcpServerMapper.class); ReflectionTestUtils.setField(s,"serverMapper",m); AiMcpServerSaveReqVO r=new AiMcpServerSaveReqVO(); r.setCode("test");r.setName("Test");r.setEndpoint("https://user:secret@example.com/mcp");r.setTransportProtocol("STREAMABLE_HTTP");r.setAuthType("NONE"); assertThrows(IllegalArgumentException.class,()->s.createServer(r));verifyNoInteractions(m); }
 @Test void acceptsDirectApiKeyOnCreate() {
  AiMcpServiceImpl s = new AiMcpServiceImpl();
  AiMcpServerMapper m = mock(AiMcpServerMapper.class);
  ReflectionTestUtils.setField(s, "serverMapper", m);
  AiMcpServerSaveReqVO r = new AiMcpServerSaveReqVO();
  r.setCode("haina"); r.setName("Haina"); r.setEndpoint("https://example.com/sse");
  r.setTransportProtocol("SSE"); r.setAuthType("API_KEY"); r.setSecretValue(" direct-key ");
  assertDoesNotThrow(() -> s.createServer(r));
  verify(m).insert(argThat((AiMcpServerDO server) -> "direct-key".equals(server.getSecretValue())
      && server.getSecretRef() == null && "API_KEY".equals(server.getAuthType())));
 }
 @Test void refusesUnconfirmedMutationPolicy() { AiMcpServiceImpl s=new AiMcpServiceImpl();AiMcpPolicyMapper m=mock(AiMcpPolicyMapper.class);ReflectionTestUtils.setField(s,"policyMapper",m);AiMcpPolicySaveReqVO r=new AiMcpPolicySaveReqVO();r.setRiskLevel("ASSET_WRITE");r.setRequireConfirmation(0);assertThrows(IllegalArgumentException.class,()->s.savePolicy(r));verifyNoInteractions(m); }
}
