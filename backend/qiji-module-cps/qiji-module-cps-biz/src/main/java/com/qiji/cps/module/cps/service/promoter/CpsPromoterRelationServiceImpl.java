package com.qiji.cps.module.cps.service.promoter;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qiji.cps.framework.common.pojo.PageParam;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.mybatis.core.util.MyBatisUtils;
import com.qiji.cps.module.cps.dal.dataobject.promoter.CpsPromoterRelationDO;
import com.qiji.cps.module.cps.dal.mysql.promoter.CpsPromoterRelationMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * CPS 推广员分销关系 Service 实现类
 *
 * @author CPS System
 */
@Slf4j
@Service
@Validated
public class CpsPromoterRelationServiceImpl implements CpsPromoterRelationService {

    private static final int MAX_INVITE_CODE_RETRY = 5;

    @Resource
    private CpsPromoterRelationMapper promoterRelationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CpsPromoterRelationDO getOrCreateRelation(Long memberId) {
        if (memberId == null) {
            return null;
        }
        CpsPromoterRelationDO relation = promoterRelationMapper.selectByMemberId(memberId);
        if (relation != null) {
            return relation;
        }

        // 初始化创建并生成专属邀请码
        for (int i = 0; i < MAX_INVITE_CODE_RETRY; i++) {
            String code = generateInviteCode(memberId);
            CpsPromoterRelationDO newRelation = CpsPromoterRelationDO.builder()
                    .memberId(memberId)
                    .parentMemberId(null)
                    .grandparentMemberId(null)
                    .inviteCode(code)
                    .promoterStatus(1) // 默认有效
                    .promoterLevel(1)  // 默认普通会员
                    .bindTime(LocalDateTime.now())
                    .teamCountL1(0)
                    .teamCountL2(0)
                    .teamOrderCount(0)
                    .totalRebateCent(0L)
                    .totalSplitCent(0L)
                    .build();
            try {
                promoterRelationMapper.insert(newRelation);
                return newRelation;
            } catch (DuplicateKeyException e) {
                // 如果是 memberId 重复，重新查询返回
                CpsPromoterRelationDO exist = promoterRelationMapper.selectByMemberId(memberId);
                if (exist != null) {
                    return exist;
                }
                log.warn("[getOrCreateRelation] 邀请码重复碰撞，重试生成: memberId={}, code={}", memberId, code);
            }
        }
        throw new IllegalStateException("生成唯一邀请码失败，超出最大重试次数: memberId=" + memberId);
    }

    @Override
    public CpsPromoterRelationDO getRelationByInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return null;
        }
        return promoterRelationMapper.selectByInviteCode(inviteCode.trim());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean bindInviter(Long memberId, String inviteCode) {
        if (memberId == null || inviteCode == null || inviteCode.isBlank()) {
            return false;
        }
        CpsPromoterRelationDO inviter = getRelationByInviteCode(inviteCode);
        if (inviter == null) {
            log.warn("[bindInviter] 邀请码不存在: code={}", inviteCode);
            return false;
        }
        return bindInviterByParentMemberId(memberId, inviter.getMemberId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean bindInviterByParentMemberId(Long memberId, Long parentMemberId) {
        if (memberId == null || parentMemberId == null) {
            return false;
        }
        // 1. 不能绑定自己
        if (Objects.equals(memberId, parentMemberId)) {
            log.warn("[bindInviterByParentMemberId] 严禁自己绑定自己: memberId={}", memberId);
            return false;
        }

        CpsPromoterRelationDO child = getOrCreateRelation(memberId);
        // 2. 检查是否已经绑定
        if (child.getParentMemberId() != null) {
            if (Objects.equals(child.getParentMemberId(), parentMemberId)) {
                log.debug("[bindInviterByParentMemberId] 已经绑定过该上级，幂等成功: memberId={}, parentId={}",
                        memberId, parentMemberId);
                return true;
            }
            log.warn("[bindInviterByParentMemberId] 已绑定其他推荐人，禁止重复换绑: memberId={}, currentParent={}, newParent={}",
                    memberId, child.getParentMemberId(), parentMemberId);
            return false;
        }

        CpsPromoterRelationDO parent = getOrCreateRelation(parentMemberId);
        if (parent == null || !Integer.valueOf(1).equals(parent.getPromoterStatus())) {
            log.warn("[bindInviterByParentMemberId] 推荐人资格无效或已冻结: parentId={}", parentMemberId);
            return false;
        }

        // 3. 循环依赖（成环）检查：沿着 parent 的祖先链向上追溯，不能出现 memberId
        Long ancestor = parent.getParentMemberId();
        int depth = 0;
        while (ancestor != null && depth < 20) {
            if (Objects.equals(ancestor, memberId)) {
                log.error("[bindInviterByParentMemberId] 检测到循环依赖环路，拒绝绑定: memberId={}, ancestorId={}",
                        memberId, ancestor);
                throw new IllegalArgumentException("禁止循环绑定上下级关系");
            }
            CpsPromoterRelationDO ancestorRel = promoterRelationMapper.selectByMemberId(ancestor);
            ancestor = ancestorRel != null ? ancestorRel.getParentMemberId() : null;
            depth++;
        }

        // 4. 更新下级关系记录
        Long grandparentMemberId = parent.getParentMemberId();
        int updated = promoterRelationMapper.bindIfUnbound(
                child.getId(), parentMemberId, grandparentMemberId, LocalDateTime.now());
        if (updated != 1) {
            CpsPromoterRelationDO current = promoterRelationMapper.selectByMemberId(memberId);
            return current != null && Objects.equals(current.getParentMemberId(), parentMemberId);
        }

        // 5. 原子递增上级的团队人数统计
        promoterRelationMapper.incrTeamCountL1(parentMemberId, 1);
        if (grandparentMemberId != null) {
            promoterRelationMapper.incrTeamCountL2(grandparentMemberId, 1);
        }

        log.info("[bindInviterByParentMemberId] 推广关系绑定成功: memberId={}, parentMemberId={}, grandparentMemberId={}",
                memberId, parentMemberId, grandparentMemberId);
        return true;
    }

    @Override
    public PageResult<CpsPromoterRelationDO> getTeamPage(Long memberId, Integer level, PageParam pageParam) {
        if (memberId == null) {
            return PageResult.empty();
        }
        IPage<CpsPromoterRelationDO> iPage;
        if (Integer.valueOf(2).equals(level)) {
            iPage = promoterRelationMapper.selectPageByGrandparentMemberId(pageParam, memberId);
        } else {
            iPage = promoterRelationMapper.selectPageByParentMemberId(pageParam, memberId);
        }
        return new PageResult<>(iPage.getRecords(), iPage.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordTeamStats(Long buyerMemberId, long selfRebateCent, long l1SplitCent, long l2SplitCent) {
        if (buyerMemberId == null) {
            return;
        }
        CpsPromoterRelationDO buyerRel = getOrCreateRelation(buyerMemberId);
        // 出单人增加出单数与自购返利
        promoterRelationMapper.incrStats(buyerMemberId, 1, selfRebateCent, 0L);

        // 一级推荐人增加出单数与推广分佣
        if (buyerRel.getParentMemberId() != null && l1SplitCent > 0) {
            promoterRelationMapper.incrStats(buyerRel.getParentMemberId(), 1, 0L, l1SplitCent);
        }

        // 二级推荐人增加出单数与推广分佣
        if (buyerRel.getGrandparentMemberId() != null && l2SplitCent > 0) {
            promoterRelationMapper.incrStats(buyerRel.getGrandparentMemberId(), 1, 0L, l2SplitCent);
        }
    }

    @Override
    public PageResult<CpsPromoterRelationDO> getAdminPromoterPage(
            com.qiji.cps.module.cps.controller.admin.promoter.vo.CpsAdminPromoterPageReqVO reqVO) {
        return promoterRelationMapper.selectAdminPage(reqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rebindInviterAdmin(Long memberId, Long newParentMemberId, String reason) {
        if (memberId == null) {
            return false;
        }
        if (Objects.equals(memberId, newParentMemberId)) {
            throw new IllegalArgumentException("不能将推荐人绑定为自己");
        }

        CpsPromoterRelationDO child = getOrCreateRelation(memberId);
        Long oldParentId = child.getParentMemberId();
        Long oldGrandparentId = child.getGrandparentMemberId();

        // 1. 若新上级为 null，则解绑
        if (newParentMemberId == null) {
            if (oldParentId != null) {
                promoterRelationMapper.incrTeamCountL1(oldParentId, -1);
            }
            if (oldGrandparentId != null) {
                promoterRelationMapper.incrTeamCountL2(oldGrandparentId, -1);
            }
            CpsPromoterRelationDO updateDO = new CpsPromoterRelationDO();
            updateDO.setId(child.getId());
            updateDO.setParentMemberId(null);
            updateDO.setGrandparentMemberId(null);
            updateDO.setBindTime(null);
            promoterRelationMapper.updateById(updateDO);
            log.info("[rebindInviterAdmin] 管理员解绑成功: memberId={}, oldParent={}, reason={}",
                    memberId, oldParentId, reason);
            return true;
        }

        // 2. 新上级存在，进行循环依赖检测
        CpsPromoterRelationDO newParent = getOrCreateRelation(newParentMemberId);
        Long ancestor = newParent.getParentMemberId();
        int depth = 0;
        while (ancestor != null && depth < 20) {
            if (Objects.equals(ancestor, memberId)) {
                log.error("[rebindInviterAdmin] 检测到循环依赖环路，拒绝换绑: memberId={}, ancestorId={}",
                        memberId, ancestor);
                throw new IllegalArgumentException("禁止循环绑定上下级关系");
            }
            CpsPromoterRelationDO ancestorRel = promoterRelationMapper.selectByMemberId(ancestor);
            ancestor = ancestorRel != null ? ancestorRel.getParentMemberId() : null;
            depth++;
        }

        // 3. 扣减旧上级的团队统计
        if (oldParentId != null) {
            promoterRelationMapper.incrTeamCountL1(oldParentId, -1);
        }
        if (oldGrandparentId != null) {
            promoterRelationMapper.incrTeamCountL2(oldGrandparentId, -1);
        }

        // 4. 增加新上级和新爷爷的团队统计
        Long newGrandparentId = newParent.getParentMemberId();
        promoterRelationMapper.incrTeamCountL1(newParentMemberId, 1);
        if (newGrandparentId != null) {
            promoterRelationMapper.incrTeamCountL2(newGrandparentId, 1);
        }

        // 5. 更新自身记录
        CpsPromoterRelationDO updateDO = new CpsPromoterRelationDO();
        updateDO.setId(child.getId());
        updateDO.setParentMemberId(newParentMemberId);
        updateDO.setGrandparentMemberId(newGrandparentId);
        updateDO.setBindTime(LocalDateTime.now());
        promoterRelationMapper.updateById(updateDO);

        log.info("[rebindInviterAdmin] 管理员人工换绑成功: memberId={}, oldParent={}, newParent={}, reason={}",
                memberId, oldParentId, newParentMemberId, reason);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePromoterLevelAndStatus(Long memberId, Integer level, Integer status) {
        if (memberId == null) {
            return;
        }
        CpsPromoterRelationDO rel = getOrCreateRelation(memberId);
        CpsPromoterRelationDO updateDO = new CpsPromoterRelationDO();
        updateDO.setId(rel.getId());
        if (level != null) {
            updateDO.setPromoterLevel(level);
        }
        if (status != null) {
            updateDO.setPromoterStatus(status);
        }
        promoterRelationMapper.updateById(updateDO);
        log.info("[updatePromoterLevelAndStatus] 修改推广员等级与状态成功: memberId={}, level={}, status={}",
                memberId, level, status);
    }

    private String generateInviteCode(Long memberId) {
        // 采用 base32 友好字符，避免 0/O, 1/I 混淆
        String rand = RandomUtil.randomString("23456789ABCDEFGHJKLMNPQRSTUVWXYZ", 4);
        String suffix = String.format("%04X", memberId % 65535);
        return rand + suffix;
    }

}
