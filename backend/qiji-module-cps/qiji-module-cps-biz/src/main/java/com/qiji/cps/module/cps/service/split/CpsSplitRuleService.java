package com.qiji.cps.module.cps.service.split;

import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.module.cps.dal.dataobject.split.CpsSplitRuleDO;

import java.util.List;

/**
 * CPS 多级分账规则 Service 接口
 *
 * @author CPS System
 */
public interface CpsSplitRuleService {

    /**
     * 根据电商平台与会员等级匹配最优先适用的多级分账规则
     *
     * @param platformCode  电商平台代码（如 taobao, jd, pdd）
     * @param memberLevelId 会员等级ID
     * @return 命中的分账规则DO
     */
    CpsSplitRuleDO matchSplitRule(String platformCode, Long memberLevelId);

    /**
     * 获取指定ID的分账规则
     *
     * @param id 规则ID
     * @return 规则DO
     */
    CpsSplitRuleDO getSplitRule(Long id);

    /**
     * 获取所有启用的分账规则列表
     *
     * @return 规则列表
     */
    List<CpsSplitRuleDO> getActiveRuleList();

    /**
     * 保存或更新分账规则
     *
     * @param ruleDO 规则DO
     * @return 规则ID
     */
    Long saveSplitRule(CpsSplitRuleDO ruleDO);

    /**
     * 删除分账规则
     *
     * @param id 规则ID
     */
    void deleteSplitRule(Long id);

    /**
     * 分页查询分账规则
     *
     * @param reqVO 分页查询条件
     * @return 分页结果
     */
    PageResult<CpsSplitRuleDO> getSplitRulePage(com.qiji.cps.module.cps.controller.admin.split.vo.CpsSplitRulePageReqVO reqVO);

}
