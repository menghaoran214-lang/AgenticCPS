package com.qiji.cps.module.ai.controller.admin.capability;
import com.qiji.cps.framework.common.pojo.*;
import com.qiji.cps.framework.common.util.object.BeanUtils;
import com.qiji.cps.module.ai.controller.admin.capability.vo.*;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
import com.qiji.cps.module.ai.service.capability.AiSkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.qiji.cps.framework.common.pojo.CommonResult.success;
@PreAuthorize("@ss.hasPermission('ai:skill:query')")
@Tag(name="管理后台 - AI Skills") @RestController @RequestMapping("/ai/skill") public class AiSkillController {
 @Resource private AiSkillService service;
 @Resource private com.qiji.cps.module.ai.service.capability.AiSkillSandboxService sandbox;
 @PreAuthorize("@ss.hasPermission('ai:skill:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PostMapping("/create") @Operation(summary="创建 Skill") public CommonResult<Long> create(@Valid @RequestBody AiSkillSaveReqVO r){return success(service.create(r));}
 @PreAuthorize("@ss.hasPermission('ai:skill:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PutMapping("/update") public CommonResult<Boolean> update(@Valid @RequestBody AiSkillSaveReqVO r){service.update(r);return success(true);}
 @PreAuthorize("@ss.hasPermission('ai:skill:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @DeleteMapping("/delete") public CommonResult<Boolean> delete(@RequestParam Long id){service.delete(id);return success(true);}
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @GetMapping("/get") public CommonResult<AiSkillRespVO> get(@RequestParam Long id){return success(BeanUtils.toBean(service.get(id),AiSkillRespVO.class));}
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @GetMapping("/page") public CommonResult<PageResult<AiSkillRespVO>> page(@Valid AiSkillPageReqVO r){return success(BeanUtils.toBean(service.page(r),AiSkillRespVO.class));}
 @PreAuthorize("@ss.hasPermission('ai:skill:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PostMapping("/version/create") public CommonResult<Long> createVersion(@Valid @RequestBody AiSkillVersionSaveReqVO r){return success(service.createVersion(r));}
 @PreAuthorize("@ss.hasPermission('ai:skill:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PutMapping("/version/update") public CommonResult<Boolean> updateVersion(@Valid @RequestBody AiSkillVersionSaveReqVO r){service.updateVersion(r);return success(true);}
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @GetMapping("/version/list") public CommonResult<List<AiSkillVersionRespVO>> versions(@RequestParam Long skillId){return success(BeanUtils.toBean(service.versions(skillId),AiSkillVersionRespVO.class));}
 @PreAuthorize("@ss.hasPermission('ai:skill:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PostMapping("/version/publish") public CommonResult<Boolean> publish(@RequestParam Long id){service.publishVersion(id);return success(true);}
 @PreAuthorize("@ss.hasPermission('ai:skill:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PostMapping("/version/rollback") public CommonResult<Boolean> rollback(@RequestParam Long skillId,@RequestParam Integer version){service.rollbackVersion(skillId,version);return success(true);}
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @GetMapping("/bindings") public CommonResult<List<AiSkillBindingRespVO>> bindings(@RequestParam Long skillVersionId){return success(BeanUtils.toBean(service.bindings(skillVersionId),AiSkillBindingRespVO.class));}
 @PreAuthorize("@ss.hasPermission('ai:skill:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PostMapping("/bindings") public CommonResult<Boolean> saveBindings(@RequestParam Long skillVersionId,@RequestBody List<AiSkillBindingSaveReqVO> r,@RequestParam Long expectedRevision){service.saveBindings(skillVersionId,expectedRevision,r);return success(true);}
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PostMapping("/version/review") @PreAuthorize("@ss.hasPermission('ai:skill:manage')")
 public CommonResult<Boolean> review(@RequestParam Long id) { service.reviewVersion(id); return success(true); }
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PostMapping("/version/archive") @PreAuthorize("@ss.hasPermission('ai:skill:manage')")
 public CommonResult<Boolean> archive(@RequestParam Long id) { service.archiveVersion(id); return success(true); }
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PostMapping("/test") @PreAuthorize("@ss.hasPermission('ai:skill:manage')")
 public CommonResult<AiSkillTestRespVO> test(@Valid @RequestBody AiSkillTestReqVO req) { return success(sandbox.test(req)); }
}
