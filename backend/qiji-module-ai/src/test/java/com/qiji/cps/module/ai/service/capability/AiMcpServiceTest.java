package com.qiji.cps.module.ai.service.capability;
import com.qiji.cps.module.ai.dal.mysql.capability.*;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
import com.qiji.cps.module.ai.controller.admin.capability.vo.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiMcpServiceTest {
 @Test void missingServerNeverReportsHealthy() { AiMcpServiceImpl s=new AiMcpServiceImpl(); AiMcpServerMapper m=mock(AiMcpServerMapper.class); McpRegistryService registry=mock(McpRegistryService.class); ReflectionTestUtils.setField(s,"serverMapper",m); ReflectionTestUtils.setField(s,"registry",registry); assertThrows(IllegalArgumentException.class,()->s.testServer(1L)); verifyNoInteractions(registry); verify(m,never()).updateById(any(AiMcpServerDO.class)); }
 @Test void rejectsEmbeddedEndpointCredentialsBeforeWrite() { AiMcpServiceImpl s=new AiMcpServiceImpl(); AiMcpServerMapper m=mock(AiMcpServerMapper.class); ReflectionTestUtils.setField(s,"serverMapper",m); AiMcpServerSaveReqVO r=new AiMcpServerSaveReqVO(); r.setCode("test");r.setName("Test");r.setEndpoint("https://user:secret@example.com/mcp");r.setTransportProtocol("STREAMABLE_HTTP");r.setAuthType("NONE"); assertThrows(IllegalArgumentException.class,()->s.createServer(r));verifyNoInteractions(m); }
 @Test void refusesUnconfirmedMutationPolicy() { AiMcpServiceImpl s=new AiMcpServiceImpl();AiMcpPolicyMapper m=mock(AiMcpPolicyMapper.class);ReflectionTestUtils.setField(s,"policyMapper",m);AiMcpPolicySaveReqVO r=new AiMcpPolicySaveReqVO();r.setRiskLevel("ASSET_WRITE");r.setRequireConfirmation(0);assertThrows(IllegalArgumentException.class,()->s.savePolicy(r));verifyNoInteractions(m); }
}
