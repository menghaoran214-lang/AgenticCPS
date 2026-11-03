package com.qiji.cps.module.cps.service.publish;

import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishDispatchRespVO;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishLogPageReqVO;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishTaskPageReqVO;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishTaskSaveReqVO;
import com.qiji.cps.module.cps.dal.dataobject.publish.CpsPublishLogDO;
import com.qiji.cps.module.cps.dal.dataobject.publish.CpsPublishTaskDO;
import jakarta.validation.Valid;

/**
 * CPS 投放任务 Service. 承担群发 / 定时群发 / 定时消息的投放调度能力。
 */
public interface CpsPublishTaskService {

    /**
     * 创建投放任务，初始状态为草稿。
     *
     * @param reqVO 投放任务信息
     * @return 任务 ID
     */
    Long createTask(@Valid CpsPublishTaskSaveReqVO reqVO);

    /**
     * 更新投放任务。
     *
     * @param reqVO 投放任务信息
     */
    void updateTask(@Valid CpsPublishTaskSaveReqVO reqVO);

    /**
     * 删除投放任务。
     *
     * @param id 任务 ID
     */
    void deleteTask(Long id);

    /**
     * 上线投放任务，并计算下次投放时间。
     *
     * @param id 任务 ID
     */
    void publishTask(Long id);

    /**
     * 下线投放任务。
     *
     * @param id 任务 ID
     */
    void offlineTask(Long id);

    /**
     * 获取投放任务。
     *
     * @param id 任务 ID
     * @return 投放任务
     */
    CpsPublishTaskDO getTask(Long id);

    /**
     * 获取投放任务分页。
     *
     * @param reqVO 分页查询
     * @return 投放任务分页
     */
    PageResult<CpsPublishTaskDO> getTaskPage(CpsPublishTaskPageReqVO reqVO);

    /**
     * 获取投放记录分页。
     *
     * @param reqVO 分页查询
     * @return 投放记录分页
     */
    PageResult<CpsPublishLogDO> getLogPage(CpsPublishLogPageReqVO reqVO);

    /**
     * 立即执行一次投放，忽略免打扰时段与 Cron 表达式。
     *
     * @param id 任务 ID
     * @return 执行结果
     */
    CpsPublishDispatchRespVO dispatchNow(Long id);

    /**
     * 执行所有到期的投放任务，尊重免打扰时段与 Cron 表达式。
     *
     * @return 执行结果
     */
    CpsPublishDispatchRespVO dispatchDueTasks();

}
