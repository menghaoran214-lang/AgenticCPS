package com.qiji.cps.module.cps.dal.mysql.publish;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.mybatis.core.mapper.BaseMapperX;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishTaskPageReqVO;
import com.qiji.cps.module.cps.dal.dataobject.publish.CpsPublishTaskDO;
import com.qiji.cps.module.cps.service.publish.CpsPublishConstants;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CpsPublishTaskMapper extends BaseMapperX<CpsPublishTaskDO> {

    default PageResult<CpsPublishTaskDO> selectPage(CpsPublishTaskPageReqVO reqVO) {
        return selectPage(reqVO, buildPageFilter(reqVO)
                .orderByDesc(CpsPublishTaskDO::getId));
    }

    default List<CpsPublishTaskDO> selectDispatchableList(LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<CpsPublishTaskDO>()
                .eq(CpsPublishTaskDO::getStatus, CpsPublishConstants.TaskStatus.ONLINE)
                .and(w -> w.isNull(CpsPublishTaskDO::getStartTime).or().le(CpsPublishTaskDO::getStartTime, now))
                .and(w -> w.isNull(CpsPublishTaskDO::getEndTime).or().ge(CpsPublishTaskDO::getEndTime, now))
                .and(w -> w.isNull(CpsPublishTaskDO::getNextDispatchTime).or().le(CpsPublishTaskDO::getNextDispatchTime, now))
                .orderByAsc(CpsPublishTaskDO::getId)
                .last("limit " + Math.max(1, limit)));
    }

    default CpsPublishTaskDO selectByTaskCode(String taskCode) {
        return selectOne(CpsPublishTaskDO::getTaskCode, taskCode);
    }

    default int updateDispatchState(Long id, LocalDateTime lastDispatchTime, LocalDateTime nextDispatchTime) {
        return update(null, new LambdaUpdateWrapper<CpsPublishTaskDO>()
                .eq(CpsPublishTaskDO::getId, id)
                .set(CpsPublishTaskDO::getLastDispatchTime, lastDispatchTime)
                .set(CpsPublishTaskDO::getNextDispatchTime, nextDispatchTime));
    }

    private LambdaQueryWrapperX<CpsPublishTaskDO> buildPageFilter(CpsPublishTaskPageReqVO reqVO) {
        return new LambdaQueryWrapperX<CpsPublishTaskDO>()
                .likeIfPresent(CpsPublishTaskDO::getTaskName, reqVO.getTaskName())
                .eqIfPresent(CpsPublishTaskDO::getTaskCode, reqVO.getTaskCode())
                .eqIfPresent(CpsPublishTaskDO::getChannelCode, reqVO.getChannelCode())
                .eqIfPresent(CpsPublishTaskDO::getSourceType, reqVO.getSourceType())
                .eqIfPresent(CpsPublishTaskDO::getTargetType, reqVO.getTargetType())
                .eqIfPresent(CpsPublishTaskDO::getStatus, reqVO.getStatus());
    }

}
