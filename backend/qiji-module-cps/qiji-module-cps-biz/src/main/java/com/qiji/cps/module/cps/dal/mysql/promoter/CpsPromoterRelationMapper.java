package com.qiji.cps.module.cps.dal.mysql.promoter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qiji.cps.framework.common.pojo.PageParam;
import com.qiji.cps.framework.mybatis.core.mapper.BaseMapperX;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.framework.mybatis.core.util.MyBatisUtils;
import com.qiji.cps.module.cps.dal.dataobject.promoter.CpsPromoterRelationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.time.LocalDateTime;

/**
 * CPS 推广员分销关系 Mapper
 *
 * @author CPS System
 */
@Mapper
public interface CpsPromoterRelationMapper extends BaseMapperX<CpsPromoterRelationDO> {

    default CpsPromoterRelationDO selectByMemberId(Long memberId) {
        if (memberId == null) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<CpsPromoterRelationDO>()
                .eq(CpsPromoterRelationDO::getMemberId, memberId));
    }

    default CpsPromoterRelationDO selectByInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<CpsPromoterRelationDO>()
                .eq(CpsPromoterRelationDO::getInviteCode, inviteCode));
    }

    default IPage<CpsPromoterRelationDO> selectPageByParentMemberId(PageParam pageParam, Long parentMemberId) {
        return selectPage(MyBatisUtils.buildPage(pageParam), new LambdaQueryWrapperX<CpsPromoterRelationDO>()
                .eq(CpsPromoterRelationDO::getParentMemberId, parentMemberId)
                .orderByDesc(CpsPromoterRelationDO::getBindTime));
    }

    default IPage<CpsPromoterRelationDO> selectPageByGrandparentMemberId(PageParam pageParam, Long grandparentMemberId) {
        return selectPage(MyBatisUtils.buildPage(pageParam), new LambdaQueryWrapperX<CpsPromoterRelationDO>()
                .eq(CpsPromoterRelationDO::getGrandparentMemberId, grandparentMemberId)
                .orderByDesc(CpsPromoterRelationDO::getBindTime));
    }

    default com.qiji.cps.framework.common.pojo.PageResult<CpsPromoterRelationDO> selectAdminPage(
            com.qiji.cps.module.cps.controller.admin.promoter.vo.CpsAdminPromoterPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CpsPromoterRelationDO>()
                .eqIfPresent(CpsPromoterRelationDO::getMemberId, reqVO.getMemberId())
                .eqIfPresent(CpsPromoterRelationDO::getInviteCode, reqVO.getInviteCode())
                .eqIfPresent(CpsPromoterRelationDO::getPromoterLevel, reqVO.getPromoterLevel())
                .eqIfPresent(CpsPromoterRelationDO::getPromoterStatus, reqVO.getPromoterStatus())
                .orderByDesc(CpsPromoterRelationDO::getId));
    }


    @Update("""
            UPDATE cps_promoter_relation
            SET parent_member_id = #{parentMemberId},
                grandparent_member_id = #{grandparentMemberId},
                bind_time = #{bindTime},
                update_time = NOW()
            WHERE id = #{id}
              AND parent_member_id IS NULL
              AND deleted = 0
            """)
    int bindIfUnbound(@Param("id") Long id,
                      @Param("parentMemberId") Long parentMemberId,
                      @Param("grandparentMemberId") Long grandparentMemberId,
                      @Param("bindTime") LocalDateTime bindTime);

    @Update("""
            UPDATE cps_promoter_relation
            SET team_count_l1 = team_count_l1 + #{delta},
                update_time = NOW()
            WHERE member_id = #{memberId}
              AND deleted = 0
            """)
    int incrTeamCountL1(@Param("memberId") Long memberId, @Param("delta") int delta);

    @Update("""
            UPDATE cps_promoter_relation
            SET team_count_l2 = team_count_l2 + #{delta},
                update_time = NOW()
            WHERE member_id = #{memberId}
              AND deleted = 0
            """)
    int incrTeamCountL2(@Param("memberId") Long memberId, @Param("delta") int delta);

    @Update("""
            UPDATE cps_promoter_relation
            SET team_order_count = team_order_count + #{orderDelta},
                total_rebate_cent = total_rebate_cent + #{rebateCentDelta},
                total_split_cent = total_split_cent + #{splitCentDelta},
                update_time = NOW()
            WHERE member_id = #{memberId}
              AND deleted = 0
            """)
    int incrStats(@Param("memberId") Long memberId,
                  @Param("orderDelta") int orderDelta,
                  @Param("rebateCentDelta") long rebateCentDelta,
                  @Param("splitCentDelta") long splitCentDelta);

}
