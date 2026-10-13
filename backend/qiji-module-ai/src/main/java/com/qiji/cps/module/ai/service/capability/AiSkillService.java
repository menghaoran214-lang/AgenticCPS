package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.module.ai.controller.admin.capability.vo.*;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
import java.util.List;

public interface AiSkillService {
    String BUILT_IN_CREATOR_CODE = "skill-creator";

    Long create(AiSkillSaveReqVO req);
    void update(AiSkillSaveReqVO req);
    void delete(Long id);
    AiSkillDO get(Long id);
    PageResult<AiSkillDO> page(AiSkillPageReqVO req);
    Long createVersion(AiSkillVersionSaveReqVO req);
    void updateVersion(AiSkillVersionSaveReqVO req);
    List<AiSkillVersionDO> versions(Long skillId);
    void reviewVersion(Long id);
    void publishVersion(Long id);
    void archiveVersion(Long id);
    void rollbackVersion(Long skillId, Integer version);
    AiSkillVersionDO getPublishedVersion(Long skillId);
    List<AiSkillBindingDO> bindings(Long versionId);
    void saveBindings(Long versionId, Long expectedRevision, List<AiSkillBindingSaveReqVO> reqs);
    AiSkillTestRespVO test(AiSkillTestReqVO req);
    AiSkillCreatorOptionsRespVO getCreatorOptions();
    AiSkillBlueprintRespVO generateBlueprint(AiSkillBlueprintReqVO req);
}
