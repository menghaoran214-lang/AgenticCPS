package com.qiji.cps.module.cps.dal.mysql.rebate;

import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.mybatis.core.mapper.BaseMapperX;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.module.cps.controller.admin.rebate.vo.CpsRebateRecordPageReqVO;
import com.qiji.cps.module.cps.dal.dataobject.rebate.CpsRebateRecordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * CPS返利记录 Mapper
 *
 * @author CPS System
 */
@Mapper
public interface CpsRebateRecordMapper extends BaseMapperX<CpsRebateRecordDO> {

    default PageResult<CpsRebateRecordDO> selectPage(CpsRebateRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CpsRebateRecordDO>()
                .eqIfPresent(CpsRebateRecordDO::getMemberId, reqVO.getMemberId())
                .inIfPresent(CpsRebateRecordDO::getMemberId, reqVO.getMemberIds())
                .eqIfPresent(CpsRebateRecordDO::getPlatformCode, reqVO.getPlatformCode())
                .eqIfPresent(CpsRebateRecordDO::getRebateType, reqVO.getRebateType())
                .eqIfPresent(CpsRebateRecordDO::getRebateStatus, reqVO.getRebateStatus())
                .betweenIfPresent(CpsRebateRecordDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(CpsRebateRecordDO::getId));
    }

    /**
     * 按订单ID查询返利记录（判断是否已结算过）
     */
    default CpsRebateRecordDO selectByOrderIdAndType(Long orderId, String rebateType) {
        return selectOne(new LambdaQueryWrapperX<CpsRebateRecordDO>()
                .eq(CpsRebateRecordDO::getOrderId, orderId)
                .eq(CpsRebateRecordDO::getRebateType, rebateType)
                .last("LIMIT 1"));
    }

    /**
     * 按订单ID、返利类型和分账角色查询唯一返利记录
     */
    default CpsRebateRecordDO selectByOrderIdTypeAndRole(Long orderId, String rebateType, String splitRole) {
        return selectOne(new LambdaQueryWrapperX<CpsRebateRecordDO>()
                .eq(CpsRebateRecordDO::getOrderId, orderId)
                .eq(CpsRebateRecordDO::getRebateType, rebateType)
                .eqIfPresent(CpsRebateRecordDO::getSplitRole, splitRole)
                .last("LIMIT 1"));
    }

    /**
     * 按订单ID查询该订单下的所有返利/分账记录（用于退款级联扣回）
     */
    default List<CpsRebateRecordDO> selectListByOrderId(Long orderId) {
        if (orderId == null) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<CpsRebateRecordDO>()
                .eq(CpsRebateRecordDO::getOrderId, orderId)
                .orderByAsc(CpsRebateRecordDO::getId));
    }

    @Select("""
            SELECT COALESCE(SUM(rebate_amount), 0)
            FROM cps_rebate_record
            WHERE deleted = 0
              AND member_id = #{memberId}
              AND rebate_type = 'rebate'
              AND rebate_status = 'pending'
            """)
    BigDecimal sumMemberPendingRebate(@Param("memberId") Long memberId);

    default List<CpsRebateRecordDO> selectListByTrace(Long orderId, String platformOrderId, String idempotencyKey) {
        if (orderId == null && isBlank(platformOrderId) && isBlank(idempotencyKey)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<CpsRebateRecordDO>()
                .eqIfPresent(CpsRebateRecordDO::getOrderId, orderId)
                .eqIfPresent(CpsRebateRecordDO::getPlatformOrderId, platformOrderId)
                .eqIfPresent(CpsRebateRecordDO::getIdempotencyKey, idempotencyKey)
                .orderByDesc(CpsRebateRecordDO::getId));
    }

    default PageResult<CpsRebateRecordDO> selectPageByMemberAndSplitRole(Long memberId, String splitRole, com.qiji.cps.framework.common.pojo.PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<CpsRebateRecordDO>()
                .eq(CpsRebateRecordDO::getMemberId, memberId)
                .eqIfPresent(CpsRebateRecordDO::getSplitRole, splitRole)
                .orderByDesc(CpsRebateRecordDO::getId));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
