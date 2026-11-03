package com.qiji.cps.module.cps.job;

import com.qiji.cps.framework.quartz.core.handler.JobHandler;
import com.qiji.cps.framework.tenant.core.job.TenantJob;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishDispatchRespVO;
import com.qiji.cps.module.cps.service.publish.CpsPublishTaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CPS 投放任务调度。扫描到期的投放任务并按免打扰时段、频控与幂等规则执行投放。
 */
@Slf4j
@Component
public class CpsPublishDispatchJob implements JobHandler {

    @Resource
    private CpsPublishTaskService publishTaskService;

    @Override
    @TenantJob
    public String execute(String param) {
        CpsPublishDispatchRespVO result = publishTaskService.dispatchDueTasks();
        log.info("[execute][投放调度完成 success={}, failed={}, skipped={}]",
                result.getSuccessCount(), result.getFailedCount(), result.getSkippedCount());
        return "投放调度完成: success=" + result.getSuccessCount()
                + ", failed=" + result.getFailedCount()
                + ", skipped=" + result.getSkippedCount();
    }

}
