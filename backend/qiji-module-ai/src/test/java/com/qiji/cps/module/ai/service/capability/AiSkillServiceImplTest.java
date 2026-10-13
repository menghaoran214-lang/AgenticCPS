package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.framework.test.core.ut.BaseMockitoUnitTest;
import com.qiji.cps.module.ai.controller.admin.capability.vo.*;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
import com.qiji.cps.module.ai.dal.mysql.capability.*;
import com.qiji.cps.module.ai.dal.dataobject.knowledge.AiKnowledgeDO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiToolDO;
import com.qiji.cps.module.ai.dal.mysql.knowledge.AiKnowledgeMapper;
import com.qiji.cps.module.ai.dal.mysql.model.AiToolMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSkillServiceImplTest extends BaseMockitoUnitTest {
    @InjectMocks private AiSkillServiceImpl service;
    @Mock private AiSkillMapper skillMapper;
    @Mock private AiSkillVersionMapper versionMapper;
    @Mock private AiSkillBindingMapper bindingMapper;
    @Mock private AiToolMapper toolMapper;
    @Mock private AiKnowledgeMapper knowledgeMapper;
    @Mock private AiMcpServerMapper serverMapper;
    @Mock private AiMcpToolMapper mcpToolMapper;
    @Mock private AiMcpPolicyMapper policyMapper;

    @Test void missingVersionCannotBeSilentlyPublished() {
        assertThrows(RuntimeException.class, () -> service.publishVersion(99L));
        verifyNoInteractions(skillMapper);
    }

    @Test void publishedVersionContentCannotBeEdited() {
        lockedVersion("PUBLISHED", 0L);
        AiSkillVersionSaveReqVO request = new AiSkillVersionSaveReqVO();
        request.setId(3L);
        request.setSkillId(1L);
        request.setSystemPrompt("changed");
        assertThrows(RuntimeException.class, () -> service.updateVersion(request));
        verify(versionMapper, never()).updateById(any(AiSkillVersionDO.class));
    }

    @Test void draftCannotBePublishedWithoutReview() {
        lockedVersion("DRAFT", 0L);
        assertThrows(RuntimeException.class, () -> service.publishVersion(3L));
        verify(versionMapper, never()).updateById(any(AiSkillVersionDO.class));
    }

    @Test void rollbackCannotPublishAnUnreviewedDraft() {
        when(skillMapper.selectForUpdate(1L)).thenReturn(new AiSkillDO().setId(1L));
        when(versionMapper.selectBySkillIdAndVersion(1L, 2)).thenReturn(new AiSkillVersionDO()
                .setId(3L).setSkillId(1L).setVersion(2).setPublishStatus("DRAFT"));
        assertThrows(RuntimeException.class, () -> service.rollbackVersion(1L, 2));
        verify(versionMapper, never()).updateById(any(AiSkillVersionDO.class));
    }

    @Test void staleDraftRevisionCannotOverwriteNewerContent() {
        lockedVersion("DRAFT", 5L);
        var request = new AiSkillVersionSaveReqVO().setId(3L).setSkillId(1L).setExpectedRevision(4L)
                .setSystemPrompt("changed");
        assertThrows(RuntimeException.class, () -> service.updateVersion(request));
        verify(versionMapper, never()).updateById(any(AiSkillVersionDO.class));
    }

    @Test void publishingReviewedVersionUpdatesCurrentPointer() {
        lockedVersion("REVIEWING", 2L);
        when(skillMapper.selectById(1L)).thenReturn(new AiSkillDO().setId(1L).setStatus(0));
        when(bindingMapper.selectListByVersionId(3L)).thenReturn(java.util.List.of());
        service.publishVersion(3L);
        verify(versionMapper).updateById(argThat((AiSkillVersionDO value) ->
                "PUBLISHED".equals(value.getPublishStatus()) && value.getRevision() == 3));
        verify(skillMapper).updateById(argThat((AiSkillDO value) -> value.getCurrentVersion() == 1));
    }

    @Test void builtInSkillCreatorCannotBeDeletedOrDisabled() {
        var builtIn = new AiSkillDO().setId(99L).setCode(AiSkillService.BUILT_IN_CREATOR_CODE)
                .setName("Skill 创建器").setRiskLevel("READ_ONLY").setStatus(0).setCurrentVersion(1);
        when(skillMapper.selectForUpdate(99L)).thenReturn(builtIn);

        assertThrows(RuntimeException.class, () -> service.delete(99L));
        verify(skillMapper, never()).deleteById(anyLong());

        var disableReq = new AiSkillSaveReqVO().setId(99L).setCode(AiSkillService.BUILT_IN_CREATOR_CODE)
                .setName("Skill 创建器").setRiskLevel("READ_ONLY").setStatus(1);
        assertThrows(RuntimeException.class, () -> service.update(disableReq));
        verify(skillMapper, never()).updateById(any(AiSkillDO.class));
    }

    @Test void generateBlueprintUsesBuiltInSkillCreatorAndFiltersBindingsByRisk() {
        ReflectionTestUtils.setField(service, "riskProviders", List.of((AiCapabilityRiskProvider) name ->
                "cps_generate_link".equals(name) ? "ATTRIBUTION_WRITE" : "READ_ONLY"));
        when(toolMapper.selectList(any())).thenReturn(List.of(
                new AiToolDO().setId(1L).setName("cps_search_goods").setDescription("商品搜索").setStatus(0),
                new AiToolDO().setId(2L).setName("cps_compare_prices").setDescription("商品比价").setStatus(0),
                new AiToolDO().setId(3L).setName("cps_generate_link").setDescription("推广转链").setStatus(0)));

        var req = new AiSkillBlueprintReqVO()
                .setName("高佣选品对比")
                .setCategory("CPS")
                .setChannelScope("ALL")
                .setRiskLevel("READ_ONLY")
                .setGoal("围绕商品搜索和比价生成高佣选品建议");

        AiSkillBlueprintRespVO blueprint = service.generateBlueprint(req);
        assertEquals(AiSkillService.BUILT_IN_CREATOR_CODE, blueprint.getCreatorSkillCode());
        assertEquals("READ_ONLY", blueprint.getRiskLevel());
        assertEquals("NONE", blueprint.getConfirmationPolicy());
        assertTrue(blueprint.getSystemPrompt().contains("不得编造") || blueprint.getSystemPrompt().contains("严禁编造"));
        assertTrue(blueprint.getChangeLog().contains(AiSkillService.BUILT_IN_CREATOR_CODE));
        assertTrue(blueprint.getBindings().stream().noneMatch(b -> "cps_generate_link".equals(b.getLocalToolName())));
        assertTrue(blueprint.getBindings().stream().anyMatch(b -> "cps_search_goods".equals(b.getLocalToolName())));
    }

    @Test void creatorOptionsReturnsBuiltInCreatorAndRegisteredCapabilities() {
        ReflectionTestUtils.setField(service, "riskProviders", List.of((AiCapabilityRiskProvider) name ->
                "cps_search_goods".equals(name) ? "READ_ONLY" : null));
        when(toolMapper.selectList(any())).thenReturn(List.of(
                new AiToolDO().setId(1L).setName("cps_search_goods").setDescription("商品搜索").setStatus(0),
                new AiToolDO().setId(2L).setName("unregistered_tool").setDescription("未登记工具").setStatus(0)));
        when(knowledgeMapper.selectList(any())).thenReturn(List.of(
                new AiKnowledgeDO().setId(100L).setName("CPS 规则 FAQ").setDescription("FAQ").setStatus(0)));

        AiSkillCreatorOptionsRespVO options = service.getCreatorOptions();
        assertNotNull(options.getCreatorSkill());
        assertEquals(AiSkillService.BUILT_IN_CREATOR_CODE, options.getCreatorSkill().getCode());
        assertTrue(Boolean.TRUE.equals(options.getCreatorSkill().getBuiltIn()));
        assertEquals(1, options.getLocalTools().size());
        assertEquals("cps_search_goods", options.getLocalTools().get(0).getName());
        assertEquals("READ_ONLY", options.getLocalTools().get(0).getRiskLevel());
        assertEquals(1, options.getKnowledgeBases().size());
        assertFalse(options.getComplianceRules().isEmpty());
    }

    @Test void createSkillAlwaysBootstrapsInitialVersionAndBindingsFromSkillCreator() {
        ReflectionTestUtils.setField(service, "riskProviders", List.of((AiCapabilityRiskProvider) name ->
                "cps_generate_link".equals(name) ? "ATTRIBUTION_WRITE" : "READ_ONLY"));
        when(skillMapper.selectOne(
                org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.toolkit.support.SFunction<AiSkillDO, ?>>any(),
                eq(AiSkillService.BUILT_IN_CREATOR_CODE)))
                .thenReturn(new AiSkillDO().setId(10L).setCode(AiSkillService.BUILT_IN_CREATOR_CODE)
                        .setStatus(0).setCurrentVersion(1).setRiskLevel("READ_ONLY"));
        when(versionMapper.selectBySkillIdAndVersion(10L, 1)).thenReturn(new AiSkillVersionDO()
                .setId(100L).setSkillId(10L).setVersion(1).setPublishStatus("PUBLISHED"));
        when(skillMapper.selectOne(
                org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.toolkit.support.SFunction<AiSkillDO, ?>>any(),
                eq("live-script-helper"))).thenReturn(null);
        doAnswer(invocation -> {
            AiSkillDO arg = invocation.getArgument(0);
            arg.setId(55L);
            return 1;
        }).when(skillMapper).insert(any(AiSkillDO.class));
        doAnswer(invocation -> {
            AiSkillVersionDO arg = invocation.getArgument(0);
            arg.setId(501L);
            return 1;
        }).when(versionMapper).insert(any(AiSkillVersionDO.class));
        when(toolMapper.selectOne(
                org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.toolkit.support.SFunction<AiToolDO, ?>>any(),
                eq("cps_generate_link")))
                .thenReturn(new AiToolDO().setId(3L).setName("cps_generate_link").setStatus(0));

        var req = new AiSkillSaveReqVO()
                .setCode("live-script-helper")
                .setName("直播选品转链助手")
                .setCategory("CPS")
                .setChannelScope("DOUYIN")
                .setRiskLevel("ATTRIBUTION_WRITE")
                .setGoal("筛选抖音高佣商品并在确认后生成转链口令")
                .setBindings(List.of(new AiSkillBindingSaveReqVO()
                        .setBindingType("LOCAL_TOOL")
                        .setLocalToolName("cps_generate_link")
                        .setEnabled(1)));

        Long skillId = service.create(req);
        assertEquals(55L, skillId);

        var versionCaptor = ArgumentCaptor.forClass(AiSkillVersionDO.class);
        verify(versionMapper).insert(versionCaptor.capture());
        AiSkillVersionDO createdVersion = versionCaptor.getValue();
        assertEquals(55L, createdVersion.getSkillId());
        assertEquals(1, createdVersion.getVersion());
        assertEquals("DRAFT", createdVersion.getPublishStatus());
        assertEquals("REQUIRED", createdVersion.getConfirmationPolicy());
        assertTrue(createdVersion.getChangeLog().contains(AiSkillService.BUILT_IN_CREATOR_CODE));

        var bindingCaptor = ArgumentCaptor.forClass(AiSkillBindingDO.class);
        verify(bindingMapper).insert(bindingCaptor.capture());
        assertEquals(501L, bindingCaptor.getValue().getSkillVersionId());
        assertEquals("cps_generate_link", bindingCaptor.getValue().getLocalToolName());
    }

    @Test void nonReadOnlySkillRejectsNoneConfirmationPolicy() {
        when(skillMapper.selectOne(
                org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.toolkit.support.SFunction<AiSkillDO, ?>>any(),
                eq(AiSkillService.BUILT_IN_CREATOR_CODE)))
                .thenReturn(new AiSkillDO().setId(10L).setCode(AiSkillService.BUILT_IN_CREATOR_CODE)
                        .setStatus(0).setCurrentVersion(1).setRiskLevel("READ_ONLY"));
        when(versionMapper.selectBySkillIdAndVersion(10L, 1)).thenReturn(new AiSkillVersionDO()
                .setId(100L).setSkillId(10L).setVersion(1).setPublishStatus("PUBLISHED"));
        when(skillMapper.selectOne(
                org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.toolkit.support.SFunction<AiSkillDO, ?>>any(),
                eq("unsafe-write-skill"))).thenReturn(null);

        var req = new AiSkillSaveReqVO()
                .setCode("unsafe-write-skill")
                .setName("不安全写入技能")
                .setRiskLevel("ATTRIBUTION_WRITE")
                .setSystemPrompt("执行转链")
                .setConfirmationPolicy("NONE");

        assertThrows(RuntimeException.class, () -> service.create(req));
        verify(skillMapper, never()).insert(any(AiSkillDO.class));
    }

    private void lockedVersion(String status, Long revision) {
        var version = new AiSkillVersionDO().setId(3L).setSkillId(1L).setVersion(1)
                .setPublishStatus(status).setRevision(revision);
        when(versionMapper.selectById(3L)).thenReturn(version);
        when(skillMapper.selectForUpdate(1L)).thenReturn(new AiSkillDO().setId(1L));
        when(versionMapper.selectForUpdate(3L)).thenReturn(version);
    }
}
