package com.qiji.cps.module.cps.controller.admin.publish;

import com.qiji.cps.framework.common.pojo.CommonResult;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.common.util.object.BeanUtils;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishDispatchRespVO;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishLogPageReqVO;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishLogRespVO;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishTaskPageReqVO;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishTaskRespVO;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishTaskSaveReqVO;
import com.qiji.cps.module.cps.dal.dataobject.publish.CpsPublishLogDO;
import com.qiji.cps.module.cps.dal.dataobject.publish.CpsPublishTaskDO;
import com.qiji.cps.module.cps.service.publish.CpsPublishTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.qiji.cps.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CPS投放任务")
@RestController
@RequestMapping("/cps/publish-task")
@Validated
public class CpsPublishTaskController {

    @Resource
    private CpsPublishTaskService publishTaskService;

    @PostMapping("/create")
    @Operation(summary = "创建投放任务")
    @PreAuthorize("@ss.hasPermission('cps:publish-task:create')")
    public CommonResult<Long> createTask(@Valid @RequestBody CpsPublishTaskSaveReqVO reqVO) {
        return success(publishTaskService.createTask(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新投放任务")
    @PreAuthorize("@ss.hasPermission('cps:publish-task:update')")
    public CommonResult<Boolean> updateTask(@Valid @RequestBody CpsPublishTaskSaveReqVO reqVO) {
        publishTaskService.updateTask(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除投放任务")
    @Parameter(name = "id", description = "任务ID", required = true)
    @PreAuthorize("@ss.hasPermission('cps:publish-task:delete')")
    public CommonResult<Boolean> deleteTask(@RequestParam("id") Long id) {
        publishTaskService.deleteTask(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取投放任务")
    @Parameter(name = "id", description = "任务ID", required = true)
    @PreAuthorize("@ss.hasPermission('cps:publish-task:query')")
    public CommonResult<CpsPublishTaskRespVO> getTask(@RequestParam("id") Long id) {
        CpsPublishTaskDO task = publishTaskService.getTask(id);
        return success(BeanUtils.toBean(task, CpsPublishTaskRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "投放任务分页")
    @PreAuthorize("@ss.hasPermission('cps:publish-task:query')")
    public CommonResult<PageResult<CpsPublishTaskRespVO>> getTaskPage(@Valid CpsPublishTaskPageReqVO reqVO) {
        PageResult<CpsPublishTaskDO> pageResult = publishTaskService.getTaskPage(reqVO);
        return success(BeanUtils.toBean(pageResult, CpsPublishTaskRespVO.class));
    }

    @PutMapping("/publish")
    @Operation(summary = "上线投放任务")
    @Parameter(name = "id", description = "任务ID", required = true)
    @PreAuthorize("@ss.hasPermission('cps:publish-task:publish')")
    public CommonResult<Boolean> publishTask(@RequestParam("id") Long id) {
        publishTaskService.publishTask(id);
        return success(true);
    }

    @PutMapping("/offline")
    @Operation(summary = "下线投放任务")
    @Parameter(name = "id", description = "任务ID", required = true)
    @PreAuthorize("@ss.hasPermission('cps:publish-task:offline')")
    public CommonResult<Boolean> offlineTask(@RequestParam("id") Long id) {
        publishTaskService.offlineTask(id);
        return success(true);
    }

    @PostMapping("/dispatch")
    @Operation(summary = "立即执行一次投放")
    @Parameter(name = "id", description = "任务ID", required = true)
    @PreAuthorize("@ss.hasPermission('cps:publish-task:dispatch')")
    public CommonResult<CpsPublishDispatchRespVO> dispatchNow(@RequestParam("id") Long id) {
        return success(publishTaskService.dispatchNow(id));
    }

    @GetMapping("/log-page")
    @Operation(summary = "投放记录分页")
    @PreAuthorize("@ss.hasPermission('cps:publish-task:query')")
    public CommonResult<PageResult<CpsPublishLogRespVO>> getLogPage(@Valid CpsPublishLogPageReqVO reqVO) {
        PageResult<CpsPublishLogDO> pageResult = publishTaskService.getLogPage(reqVO);
        return success(BeanUtils.toBean(pageResult, CpsPublishLogRespVO.class));
    }

}
