package com.qiji.cps.module.cps.dal.mysql.split;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.mybatis.core.mapper.BaseMapperX;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.module.cps.controller.admin.split.vo.CpsSplitRulePageReqVO;
import com.qiji.cps.module.cps.dal.dataobject.split.CpsSplitRuleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * CPS 多级分账规则 Mapper
 *
 * @author CPS System
 */
@Mapper
public interface CpsSplitRuleMapper extends BaseMapperX<CpsSplitRuleDO> {

    /**
     * 查询所有已启用的分账规则列表，按优先级降序排列
     */
    default List<CpsSplitRuleDO> selectActiveList() {
        return selectList(new LambdaQueryWrapper<CpsSplitRuleDO>()
                .eq(CpsSplitRuleDO::getStatus, 1)
                .orderByDesc(CpsSplitRuleDO::getPriority)
                .orderByDesc(CpsSplitRuleDO::getId));
    }

    /**
     * 分页查询分账规则
     */
    default PageResult<CpsSplitRuleDO> selectPage(CpsSplitRulePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CpsSplitRuleDO>()
                .likeIfPresent(CpsSplitRuleDO::getRuleName, reqVO.getRuleName())
                .eqIfPresent(CpsSplitRuleDO::getPlatformCode, reqVO.getPlatformCode())
                .eqIfPresent(CpsSplitRuleDO::getStatus, reqVO.getStatus())
                .orderByDesc(CpsSplitRuleDO::getPriority)
                .orderByDesc(CpsSplitRuleDO::getId));
    }

}
