package com.qiji.cps.module.cps.controller.admin.version;

import com.qiji.cps.framework.common.pojo.CommonResult;
import com.qiji.cps.framework.common.util.object.BeanUtils;
import com.qiji.cps.module.cps.controller.admin.version.vo.CpsSchemaVersionRespVO;
import com.qiji.cps.module.cps.dal.dataobject.version.CpsSchemaVersionDO;
import com.qiji.cps.module.cps.service.version.CpsSchemaVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.qiji.cps.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 数据库结构版本与更新时间")
@RestController
@RequestMapping("/cps/schema-version")
@Validated
public class CpsSchemaVersionController {

    @Resource
    private CpsSchemaVersionService schemaVersionService;

    @GetMapping("/latest")
    @Operation(summary = "获得最新数据库结构版本与更新时间")
    @Parameter(name = "module", description = "模块标识（默认 cps）", example = "cps")
    @PreAuthorize("@ss.hasPermission('cps:platform:query')")
    public CommonResult<CpsSchemaVersionRespVO> getLatestSchemaVersion(
            @RequestParam(value = "module", required = false, defaultValue = "cps") String module) {
        CpsSchemaVersionDO versionDO = schemaVersionService.getLatestSchemaVersion(module);
        return success(BeanUtils.toBean(versionDO, CpsSchemaVersionRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得数据库结构版本历史列表")
    @Parameter(name = "module", description = "模块标识（可选）", example = "cps")
    @PreAuthorize("@ss.hasPermission('cps:platform:query')")
    public CommonResult<List<CpsSchemaVersionRespVO>> listSchemaVersions(
            @RequestParam(value = "module", required = false) String module) {
        List<CpsSchemaVersionDO> list = schemaVersionService.listSchemaVersions(module);
        return success(BeanUtils.toBean(list, CpsSchemaVersionRespVO.class));
    }

}
