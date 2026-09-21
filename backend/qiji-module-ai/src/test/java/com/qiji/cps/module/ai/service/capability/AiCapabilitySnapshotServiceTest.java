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

    @Test void memberConversationCapturesPublishedRoleSkillsWhenCapabilityCenterIsEnabled() {
        AiSkillVersionDO version = new AiSkillVersionDO().setId(20L).setVersion(2)
                .setSystemPrompt("member skill").setConfirmationPolicy("NONE");
        when(skillService.get(2L)).thenReturn(new AiSkillDO().setId(2L).setCode("member-skill")
                .setRiskLevel("READ_ONLY"));
        when(skillService.getPublishedVersion(2L)).thenReturn(version);
        when(skillService.bindings(20L)).thenReturn(List.of());

        var conversation = new AiChatConversationDO().setOwnerUserType("MEMBER");
        service.capture(conversation, role().setSkillIds(List.of(2L)));

        var snapshot = AiCapabilitySnapshotService.read(conversation);
        assertEquals(List.of(2L), snapshot.getSkills().stream().map(AiCapabilitySnapshot.Skill::getId).toList());
        assertTrue(conversation.getSystemMessage().contains("member skill"));
    }

    @Test void skillScopedPolicyCannotGrantToolsWithoutItsSkill() {
        when(policyMapper.selectById(5L)).thenReturn(new AiMcpPolicyDO().setId(5L).setStatus(0)
                .setSkillId(2L).setMcpServerIds("[6]").setMcpToolIds("[9]").setRiskLevel("READ_ONLY"));
        var conversation = new AiChatConversationDO();
        service.capture(conversation,role().setMcpPolicyId(5L));
        assertTrue(AiCapabilitySnapshotService.read(conversation).getTools().isEmpty());
        verifyNoInteractions(mcpToolMapper);
    }

    @Test void directMcpPolicyFreezesExplicitToolGrant() {
        var server = new AiMcpServerDO().setId(6L).setCode("catalog").setStatus(0);
        var tool = new AiMcpToolDO().setId(9L).setServerId(6L).setName("search_catalog")
                .setStatus(0).setRiskLevel("READ_ONLY").setInputSchema("{\"type\":\"object\"}");
        when(policyMapper.selectById(5L)).thenReturn(new AiMcpPolicyDO().setId(5L).setStatus(0)
                .setMcpServerIds("[6]").setMcpToolIds("[9]").setRiskLevel("READ_ONLY")
                .setRequireConfirmation(0));
        when(mcpToolMapper.selectById(9L)).thenReturn(tool);
        when(mcpToolMapper.selectOne(any())).thenReturn(tool);
        when(serverMapper.selectById(6L)).thenReturn(server);

        var conversation = new AiChatConversationDO();
        service.capture(conversation, role().setMcpPolicyId(5L));

        var snapshot = AiCapabilitySnapshotService.read(conversation);
        assertEquals(1, snapshot.getTools().size());
        assertEquals("search_catalog", snapshot.getTools().get(0).getName());
        assertEquals(6L, snapshot.getTools().get(0).getServerId());
        assertEquals("catalog", snapshot.getTools().get(0).getServerCode());
        assertEquals("{\"type\":\"object\"}", snapshot.getTools().get(0).getInputSchema());
    }

    @Test void legacyMcpClientRoleKeepsLegacyCallbackPathWhenV2IsEnabled() {
        var conversation = new AiChatConversationDO().setOwnerUserType("ADMIN");

        service.capture(conversation, role().setMcpClientNames(List.of("external")));

        assertNull(conversation.getEffectiveCapabilitySnapshot());
        verifyNoInteractions(policyMapper, mcpToolMapper, serverMapper);
    }

    @Test void legacyMcpClientCannotBeCombinedWithSkillsWithoutPolicy() {
        var conversation = new AiChatConversationDO().setOwnerUserType("ADMIN");

        assertThrows(IllegalArgumentException.class, () -> service.capture(conversation,
                role().setMcpClientNames(List.of("external")).setSkillIds(List.of(2L))));
        verifyNoInteractions(skillService, policyMapper, mcpToolMapper, serverMapper);
    }

    @Test void unpublishedRoleCannotCreateV2Conversation() {
        assertThrows(IllegalArgumentException.class, () -> service.capture(new AiChatConversationDO(),
                role().setPublishStatus("DRAFT")));
    }

    @Test void featureOffKeepsMemberLegacyConversation() {
        ReflectionTestUtils.setField(service,"enabled",false);
        var member = new AiChatConversationDO().setOwnerUserType("MEMBER");
        service.capture(member,role());
        assertNull(member.getEffectiveCapabilitySnapshot());
    }

    @Test void enabledMemberConversationUsesRoleCapabilitySnapshot() {
        var member = new AiChatConversationDO().setOwnerUserType("MEMBER");
        service.capture(member,role());
        assertNotNull(member.getEffectiveCapabilitySnapshot());
    }

    @Test void featureOffAdminConversationKeepsLegacyConversation() {
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
