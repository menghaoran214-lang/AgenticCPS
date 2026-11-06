package com.qiji.cps.module.cps.dal.mysql.publish;

import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.mybatis.core.mapper.BaseMapperX;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishLogPageReqVO;
import com.qiji.cps.module.cps.dal.dataobject.publish.CpsPublishLogDO;
import com.qiji.cps.module.cps.service.publish.CpsPublishConstants;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface CpsPublishLogMapper extends BaseMapperX<CpsPublishLogDO> {

    default PageResult<CpsPublishLogDO> selectPage(CpsPublishLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CpsPublishLogDO>()
                .eqIfPresent(CpsPublishLogDO::getTaskId, reqVO.getTaskId())
                .eqIfPresent(CpsPublishLogDO::getTaskCode, reqVO.getTaskCode())
                .eqIfPresent(CpsPublishLogDO::getChannelCode, reqVO.getChannelCode())
                .eqIfPresent(CpsPublishLogDO::getMemberId, reqVO.getMemberId())
                .eqIfPresent(CpsPublishLogDO::getStatus, reqVO.getStatus())
                .geIfPresent(CpsPublishLogDO::getSentAt, reqVO.getStartTime())
                .leIfPresent(CpsPublishLogDO::getSentAt, reqVO.getEndTime())
                .orderByDesc(CpsPublishLogDO::getId));
    }

    /**
     * 统计指定任务在指定会员上的当日已投放次数，占位中（PROCESSING）与成功（SUCCESS）都计入频控。
     */
    default Long countByTaskAndMemberSince(Long taskId, Long memberId, LocalDateTime since) {
        return selectCount(new LambdaQueryWrapperX<CpsPublishLogDO>()
                .eq(CpsPublishLogDO::getTaskId, taskId)
                .eq(CpsPublishLogDO::getMemberId, memberId)
                .and(w -> w.eq(CpsPublishLogDO::getStatus, CpsPublishConstants.LogStatus.PROCESSING)
                        .or().eq(CpsPublishLogDO::getStatus, CpsPublishConstants.LogStatus.ACCEPTED)
                        .or().eq(CpsPublishLogDO::getStatus, CpsPublishConstants.LogStatus.PREVIEW)
                        .or().eq(CpsPublishLogDO::getStatus, CpsPublishConstants.LogStatus.SUCCESS))
                .ge(CpsPublishLogDO::getSentAt, since));
    }

    default boolean existsByIdempotentKey(String idempotentKey) {
        return selectCount(CpsPublishLogDO::getIdempotentKey, idempotentKey) > 0;
    }

}
