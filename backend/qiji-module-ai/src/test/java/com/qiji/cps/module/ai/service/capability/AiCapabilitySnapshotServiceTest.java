package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.framework.test.core.ut.BaseMockitoUnitTest;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiChatRoleDO;
import com.qiji.cps.module.ai.dal.mysql.capability.*;
import com.qiji.cps.module.ai.service.model.AiToolService;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiCapabilitySnapshotServiceTest extends BaseMockitoUnitTest {
    @InjectMocks private AiCapabilitySnapshotService service;
    @Mock private AiSkillService skillService;
    @Mock private AiToolService toolService;
    @Mock private AiMcpPolicyMapper policyMapper;
    @Mock private AiMcpToolMapper mcpToolMapper;
    @Mock private AiMcpServerMapper serverMapper;

    @BeforeEach void enabled() {
        ReflectionTestUtils.setField(service,"enabled",true);
        ReflectionTestUtils.setField(service,"riskProviders",List.of((AiCapabilityRiskProvider) name -> "READ_ONLY"));
    }

    @Test void publishedSkillPromptKnowledgeAndToolsRemainFrozen() {
        AiSkillVersionDO version = new AiSkillVersionDO().setId(20L).setVersion(2).setSystemPrompt("original")
                .setConfirmationPolicy("NONE");
        when(skillService.get(2L)).thenReturn(new AiSkillDO().setId(2L).setCode("selection").setRiskLevel("READ_ONLY"));
        when(skillService.getPublishedVersion(2L)).thenReturn(version);
        when(skillService.bindings(20L)).thenReturn(List.of(new AiSkillBindingDO().setEnabled(1)
                .setBindingType("LOCAL_TOOL").setLocalToolName("search"), new AiSkillBindingDO().setEnabled(1)
                .setBindingType("KNOWLEDGE_BASE").setKnowledgeBaseId(33L)));
        var role = role().setSkillIds(List.of(2L));
        var conversation = new AiChatConversationDO().setOwnerUserType("ADMIN");
        service.capture(conversation,role);
        version.setSystemPrompt("changed").setVersion(3);
        role.setKnowledgeIds(List.of(999L)).setVersion(99);
        var frozen = AiCapabilitySnapshotService.read(conversation);
        assertEquals(1,frozen.getRoleVersion());
        assertEquals(2,frozen.getSkills().get(0).getVersion());
        assertEquals("original",frozen.getSkills().get(0).getSystemPrompt());
        assertEquals(List.of(33L),frozen.getKnowledgeIds());
        assertEquals("search",frozen.getTools().get(0).getName());
        assertTrue(conversation.getSystemMessage().contains("original"));
    }

    @Test void skillScopedPolicyCannotGrantToolsWithoutItsSkill() {
        when(policyMapper.selectById(5L)).thenReturn(new AiMcpPolicyDO().setId(5L).setStatus(0)
                .setSkillId(2L).setMcpServerIds("[6]").setMcpToolIds("[9]").setRiskLevel("READ_ONLY"));
        var conversation = new AiChatConversationDO();
        service.capture(conversation,role().setMcpPolicyId(5L));
        assertTrue(AiCapabilitySnapshotService.read(conversation).getTools().isEmpty());
        verifyNoInteractions(mcpToolMapper);
    }

    @Test void unpublishedRoleCannotCreateV2Conversation() {
        assertThrows(IllegalArgumentException.class, () -> service.capture(new AiChatConversationDO(),
                role().setPublishStatus("DRAFT")));
    }

    @Test void featureOffAndMemberPhaseOneKeepLegacyConversation() {
        var member = new AiChatConversationDO().setOwnerUserType("MEMBER");
        service.capture(member,role());
        assertNull(member.getEffectiveCapabilitySnapshot());
        ReflectionTestUtils.setField(service,"enabled",false);
        var admin = new AiChatConversationDO().setOwnerUserType("ADMIN");
        service.capture(admin,role());
        assertNull(admin.getEffectiveCapabilitySnapshot());
    }

    private AiChatRoleDO role() {
        return new AiChatRoleDO().setId(1L).setVersion(1).setPublishStatus("PUBLISHED")
                .setSystemMessage("role").setRiskLevel("READ_ONLY").setAllowMutation(false);
    }
}
