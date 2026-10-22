package com.qiji.cps.module.cps.controller.admin.split;

import com.qiji.cps.framework.common.pojo.CommonResult;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.common.util.object.BeanUtils;
import com.qiji.cps.module.cps.controller.admin.split.vo.CpsSplitRulePageReqVO;
import com.qiji.cps.module.cps.controller.admin.split.vo.CpsSplitRuleRespVO;
import com.qiji.cps.module.cps.controller.admin.split.vo.CpsSplitRuleSaveReqVO;
import com.qiji.cps.module.cps.dal.dataobject.split.CpsSplitRuleDO;
import com.qiji.cps.module.cps.service.split.CpsSplitRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.qiji.cps.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - CPS 多级分账规则配置
 *
 * @author CPS System
 */
@Tag(name = "管理后台 - CPS 多级分账规则配置")
@RestController
@RequestMapping("/cps/split-rule")
@Validated
public class CpsSplitRuleController {

    @Resource
    private CpsSplitRuleService splitRuleService;

    @PostMapping("/create")
    @Operation(summary = "创建分账规则")
    @PreAuthorize("@ss.hasPermission('cps:split-rule:create')")
    public CommonResult<Long> createSplitRule(@Valid @RequestBody CpsSplitRuleSaveReqVO createReqVO) {
        CpsSplitRuleDO ruleDO = BeanUtils.toBean(createReqVO, CpsSplitRuleDO.class);
        ruleDO.setId(null);
        return success(splitRuleService.saveSplitRule(ruleDO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新分账规则")
    @PreAuthorize("@ss.hasPermission('cps:split-rule:update')")
    public CommonResult<Boolean> updateSplitRule(@Valid @RequestBody CpsSplitRuleSaveReqVO updateReqVO) {
        CpsSplitRuleDO ruleDO = BeanUtils.toBean(updateReqVO, CpsSplitRuleDO.class);
        splitRuleService.saveSplitRule(ruleDO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除分账规则")
    @Parameter(name = "id", description = "规则编号", required = true)
    @PreAuthorize("@ss.hasPermission('cps:split-rule:delete')")
    public CommonResult<Boolean> deleteSplitRule(@RequestParam("id") Long id) {
        splitRuleService.deleteSplitRule(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得分账规则详情")
    @Parameter(name = "id", description = "规则编号", required = true)
    @PreAuthorize("@ss.hasPermission('cps:split-rule:query')")
    public CommonResult<CpsSplitRuleRespVO> getSplitRule(@RequestParam("id") Long id) {
        CpsSplitRuleDO ruleDO = splitRuleService.getSplitRule(id);
        return success(BeanUtils.toBean(ruleDO, CpsSplitRuleRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得分账规则分页")
    @PreAuthorize("@ss.hasPermission('cps:split-rule:query')")
    public CommonResult<PageResult<CpsSplitRuleRespVO>> getSplitRulePage(@Valid CpsSplitRulePageReqVO pageReqVO) {
        PageResult<CpsSplitRuleDO> pageResult = splitRuleService.getSplitRulePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CpsSplitRuleRespVO.class));
    }
}
