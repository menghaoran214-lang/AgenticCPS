package com.qiji.cps.module.cps.service.promoter;

import com.qiji.cps.framework.common.pojo.PageParam;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.module.cps.dal.dataobject.promoter.CpsPromoterRelationDO;

/**
 * CPS 推广员分销关系 Service 接口
 *
 * @author CPS System
 */
public interface CpsPromoterRelationService {

    /**
     * 获取指定会员的推广关系（若尚未存在则自动初始化创建）
     *
     * @param memberId 会员ID
     * @return 推广关系DO
     */
    CpsPromoterRelationDO getOrCreateRelation(Long memberId);

    /**
     * 根据邀请码查询推广员关系
     *
     * @param inviteCode 专属邀请码
     * @return 推广关系DO
     */
    CpsPromoterRelationDO getRelationByInviteCode(String inviteCode);

    /**
     * 绑定上级推荐人（通过邀请码）
     *
     * @param memberId   当前会员ID
     * @param inviteCode 上级邀请码
     * @return 是否绑定成功
     */
    boolean bindInviter(Long memberId, String inviteCode);

    /**
     * 绑定上级推荐人（通过上级会员ID，常用于转链出单新客自动锁粉）
     *
     * @param memberId       当前会员ID
     * @param parentMemberId 一级推荐人会员ID
     * @return 是否绑定成功
     */
    boolean bindInviterByParentMemberId(Long memberId, Long parentMemberId);

    /**
     * 分页查询我的团队下线成员
     *
     * @param memberId  当前会员ID
     * @param level     层级（1: 一级直推, 2: 二级间推）
     * @param pageParam 分页参数
     * @return 分页结果
     */
    PageResult<CpsPromoterRelationDO> getTeamPage(Long memberId, Integer level, PageParam pageParam);

    /**
     * 累计记录团队出单与分润业绩
     *
     * @param buyerMemberId  出单买家会员ID
     * @param selfRebateCent 自购返利金额（分）
     * @param l1SplitCent    一级提成金额（分）
     * @param l2SplitCent    二级提成金额（分）
     */
    void recordTeamStats(Long buyerMemberId, long selfRebateCent, long l1SplitCent, long l2SplitCent);

    /**
     * 管理后台 - 分页查询推广员关系列表
     *
     * @param reqVO 分页查询参数
     * @return 分页结果
     */
    PageResult<CpsPromoterRelationDO> getAdminPromoterPage(
            com.qiji.cps.module.cps.controller.admin.promoter.vo.CpsAdminPromoterPageReqVO reqVO);

    /**
     * 管理后台 - 人工换绑推荐人关系
     *
     * @param memberId          会员ID
     * @param newParentMemberId 新一级推荐人ID（可为 null 表示解绑）
     * @param reason            操作原因
     * @return 是否成功
     */
    boolean rebindInviterAdmin(Long memberId, Long newParentMemberId, String reason);

    /**
     * 管理后台 - 修改推广员等级与状态
     *
     * @param memberId 会员ID
     * @param level    推广等级
     * @param status   分销状态
     */
    void updatePromoterLevelAndStatus(Long memberId, Integer level, Integer status);

}
