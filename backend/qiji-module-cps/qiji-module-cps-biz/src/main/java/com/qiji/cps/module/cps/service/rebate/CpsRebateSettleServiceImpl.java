package com.qiji.cps.module.cps.service.rebate;

import com.qiji.cps.module.cps.dal.dataobject.freeze.CpsFreezeConfigDO;
import com.qiji.cps.module.cps.dal.dataobject.freeze.CpsFreezeRecordDO;
import com.qiji.cps.module.cps.dal.dataobject.order.CpsOrderDO;
import com.qiji.cps.module.cps.dal.dataobject.rebate.CpsRebateAccountDO;
import com.qiji.cps.module.cps.dal.dataobject.rebate.CpsRebateConfigDO;
import com.qiji.cps.module.cps.dal.dataobject.rebate.CpsRebateRecordDO;
import com.qiji.cps.module.cps.dal.mysql.order.CpsOrderMapper;
import com.qiji.cps.module.cps.dal.mysql.rebate.CpsRebateAccountMapper;
import com.qiji.cps.module.cps.dal.mysql.rebate.CpsRebateRecordMapper;
import com.qiji.cps.module.cps.enums.CpsFreezeStatusEnum;
import com.qiji.cps.module.cps.enums.CpsOrderStatusEnum;
import com.qiji.cps.module.cps.enums.CpsRebateStatusEnum;
import com.qiji.cps.module.cps.enums.CpsRebateTypeEnum;
import com.qiji.cps.module.cps.service.freeze.CpsFreezeService;
import com.qiji.cps.module.cps.dal.dataobject.promoter.CpsPromoterRelationDO;
import com.qiji.cps.module.cps.dal.dataobject.split.CpsSplitRuleDO;
import com.qiji.cps.module.cps.enums.CpsSplitRoleEnum;
import com.qiji.cps.module.cps.service.promoter.CpsPromoterRelationService;
import com.qiji.cps.module.cps.service.split.CpsSplitRuleService;
import com.qiji.cps.module.cps.service.split.calculator.CpsSplitAllocation;
import com.qiji.cps.module.cps.service.split.calculator.CpsSplitCalculator;
import com.qiji.cps.module.cps.service.rebate.asset.CpsMoneyConverter;
import com.qiji.cps.module.cps.service.rebate.asset.CpsRebateAssetService;
import com.qiji.cps.module.member.api.user.MemberUserApi;
import com.qiji.cps.module.member.api.user.dto.MemberUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CPS 返利结算 Service 实现类
 *
 * <p>核心流程：
 * <ol>
 *   <li>扫描已收货/已结算且未入账的订单</li>
 *   <li>按返利配置优先级计算应得返利金额</li>
 *   <li>写入返利记录（{@code cps_rebate_record}）</li>
 *   <li>按实际返利阈值选择直接入账或创建冻结记录；冻结到期后再更新为"已到账"</li>
 *   <li>乐观锁更新返利账户余额（{@code cps_rebate_account}）</li>
 * </ol>
 * </p>
 *
 * @author CPS System
 */
@Slf4j
@Service
@Validated
public class CpsRebateSettleServiceImpl implements CpsRebateSettleService {

    private static final int SETTLE_RETRY_DELAY_MINUTES = 15;
    private static final int SETTLE_ERROR_MAX_LENGTH = 500;

    /**
     * 待结算订单状态：已收货或已结算；已收货订单必须同时具备平台结算时间和确认收货时间。
     */
    private static final List<String> PENDING_SETTLE_STATUSES = List.of(
            CpsOrderStatusEnum.RECEIVED.getStatus(), CpsOrderStatusEnum.SETTLED.getStatus());

    @Resource
    private CpsOrderMapper orderMapper;

    @Resource
    private CpsRebateRecordMapper rebateRecordMapper;

    @Resource
    private CpsRebateAccountMapper rebateAccountMapper;

    @Resource
    private CpsRebateConfigService rebateConfigService;

    @Resource
    private CpsRebateAssetService rebateAssetService;

    @Resource
    private CpsFreezeService freezeService;

    @Resource
    private CpsMoneyConverter moneyConverter;

    @Resource
    private MemberUserApi memberUserApi;

    @Resource
    private CpsRebateSettleExecutor settleExecutor;
    @Resource
    private CpsPromoterRelationService promoterRelationService;

    @Resource
    private CpsSplitRuleService splitRuleService;

    @Resource
    private CpsSplitCalculator splitCalculator;

    // ==================== 核心结算逻辑 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean settleOrder(CpsOrderDO candidateOrder) {
        if (candidateOrder == null || candidateOrder.getId() == null) {
            return false;
        }
        CpsOrderDO order = orderMapper.selectForUpdateById(candidateOrder.getId());
        if (order == null) {
            log.warn("[settleOrder] 订单不存在，跳过: orderId={}", candidateOrder.getId());
            return false;
        }
        if (order.getMemberId() == null) {
            log.debug("[settleOrder] 订单无会员归因，跳过: orderId={}", order.getId());
            return false;
        }
        if (!isSettlementReady(order)) {
            log.warn("[settleOrder] 订单未满足平台返利结算前置条件: orderId={}", order.getId());
            return false;
        }

        // 幂等检查：已有返利记录则跳过
        CpsRebateRecordDO existRecord = rebateRecordMapper.selectByOrderIdAndType(
                order.getId(), CpsRebateTypeEnum.REBATE.getType());
        if (existRecord != null
                && !CpsRebateStatusEnum.PENDING.getStatus().equals(existRecord.getRebateStatus())) {
            log.debug("[settleOrder] 订单已结算过，跳过: orderId={}, recordId={}", order.getId(), existRecord.getId());
            return false;
        }

        MemberUserRespDTO member = memberUserApi.getUser(order.getMemberId());
        if (member == null) {
            throw new IllegalStateException("结算会员不存在: " + order.getMemberId());
        }

        // 若装配了多级分账服务，则优先走多级分账引擎
        if (splitRuleService != null && promoterRelationService != null && splitCalculator != null) {
            return settleOrderMultiLevel(order, member, existRecord);
        }
        CpsRebateConfigDO config = rebateConfigService.matchRebateConfig(
                order.getMemberId(), member.getLevelId(), order.getPlatformCode());
        if (config == null) {
            log.warn("[settleOrder] 无匹配返利配置，保持待处理: orderId={}", order.getId());
            return false;
        }

        // 旧版本可能已写入 pending 记录后在创建冻结阶段失败。重跑时复用该记录金额，
        // 避免因已有记录直接跳过导致订单永久停留在已结算且实际返利为 0。
        BigDecimal rebateAmount = existRecord != null && existRecord.getRebateAmount() != null
                ? existRecord.getRebateAmount() : calculateRebateAmount(order, config);
        if (rebateAmount == null || rebateAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[settleOrder] 返利金额计算为0，跳过结算: orderId={}", order.getId());
            return false;
        }

        BigDecimal rebateRate = config.getRebateRate();
        String idempotencyKey = "order-rebate:" + order.getId();
        long rebateAmountCent = moneyConverter.yuanToCent(rebateAmount);
        CpsFreezeConfigDO freezeConfig = freezeService == null
                ? null : freezeService.getActiveConfig(order.getPlatformCode(), rebateAmountCent);
        // 兼容未装配统一冻结服务的旧测试/启动场景；正式运行始终以冻结管理规则为准。
        boolean shouldFreeze = freezeService == null
                ? shouldFreezeLegacy(rebateAmount, config) : freezeConfig != null;

        // 3. 写入返利记录
        CpsRebateRecordDO record = CpsRebateRecordDO.builder()
                .memberId(order.getMemberId())
                .orderId(order.getId())
                .platformCode(order.getPlatformCode())
                .platformOrderId(order.getPlatformOrderId())
                .itemId(order.getItemId())
                .itemTitle(order.getItemTitle())
                .orderAmount(order.getFinalPrice() != null ? order.getFinalPrice() : BigDecimal.ZERO)
                .commissionAmount(order.getCommissionAmount() != null ? order.getCommissionAmount() : BigDecimal.ZERO)
                .rebateRate(rebateRate)
                .rebateAmount(rebateAmount)
                .rebateType(CpsRebateTypeEnum.REBATE.getType())
                .rebateStatus(CpsRebateStatusEnum.PENDING.getStatus())
                .rebateConfigId(config.getId())
                .memberLevelIdSnapshot(member.getLevelId())
                .rebateAmountCent(moneyConverter.yuanToCent(rebateAmount))
                .idempotencyKey(idempotencyKey)
                .remark(shouldFreeze ? "平台结算后创建冻结返利" : "平台结算后直接入账返利")
                .build();
        if (existRecord == null) {
            rebateRecordMapper.insert(record);
        } else {
            record = existRecord;
        }

        if (!shouldFreeze) {
            rebateAssetService.creditOrderRebate(order.getId(), "order-rebate-credit:" + order.getId());
            log.info("[settleOrder] 小额订单返利直接入账: orderId={}, memberId={}, rebateAmount={}",
                    order.getId(), order.getMemberId(), rebateAmount);
            return true;
        }

        CpsFreezeRecordDO freeze = freezeService == null
                ? rebateAssetService.createOrderRebateFreeze(order.getId(), idempotencyKey, config.getFreezeDays())
                : rebateAssetService.createOrderRebateFreeze(order.getId(), idempotencyKey);

        CpsOrderDO updateOrder = CpsOrderDO.builder()
                .id(order.getId())
                .orderStatus(CpsOrderStatusEnum.SETTLED.getStatus())
                .realRebate(rebateAmount)
                .rebateFreezeStatus(CpsFreezeStatusEnum.FROZEN.getStatus())
                .planUnfreezeTime(freeze.getUnfreezeTime())
                .build();
        if (orderMapper.updateRebateFreezeByStatusVersion(updateOrder, order.getStatusVersion()) != 1) {
            throw new IllegalStateException("订单状态已并发变更，回滚本次返利结算: orderId=" + order.getId());
        }

        log.info("[settleOrder] 返利冻结创建成功: orderId={}, memberId={}, rebateAmount={}",
                order.getId(), order.getMemberId(), rebateAmount);
        return true;
    }

    private boolean settleOrderMultiLevel(CpsOrderDO order, MemberUserRespDTO member, CpsRebateRecordDO existRecord) {
        CpsSplitRuleDO splitRule = splitRuleService.matchSplitRule(order.getPlatformCode(), member.getLevelId());
        CpsPromoterRelationDO buyerRel = promoterRelationService.getOrCreateRelation(order.getMemberId());
        CpsPromoterRelationDO parentRel = buyerRel.getParentMemberId() != null
                ? promoterRelationService.getOrCreateRelation(buyerRel.getParentMemberId()) : null;
        CpsPromoterRelationDO grandparentRel = buyerRel.getGrandparentMemberId() != null
                ? promoterRelationService.getOrCreateRelation(buyerRel.getGrandparentMemberId()) : null;

        long commissionCent = order.getCommissionAmount() != null
                ? moneyConverter.yuanToCent(order.getCommissionAmount()) : 0L;
        if (commissionCent <= 0) {
            log.warn("[settleOrderMultiLevel] 订单佣金为0，跳过结算: orderId={}", order.getId());
            return false;
        }

        CpsSplitAllocation allocation = splitCalculator.calculateSplit(
                commissionCent, splitRule, buyerRel, parentRel, grandparentRel);

        BigDecimal selfRebateAmount = moneyConverter.centToYuan(allocation.getSelfRebateCent());
        if (selfRebateAmount == null || selfRebateAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[settleOrderMultiLevel] 自购返利金额计算为0，跳过结算: orderId={}", order.getId());
            return false;
        }

        int freezeDays = splitRule != null && splitRule.getFreezeDays() != null ? splitRule.getFreezeDays() : 15;
        CpsFreezeConfigDO freezeConfig = freezeService == null
                ? null : freezeService.getActiveConfig(order.getPlatformCode(), allocation.getSelfRebateCent());
        boolean shouldFreeze = freezeService == null ? (freezeDays > 0) : freezeConfig != null;

        // 1. 记录或复用出单人 (SELF) 返利记录
        String idempotencyKeySelf = "order-split:self:" + order.getId();
        CpsRebateRecordDO selfRecord = CpsRebateRecordDO.builder()
                .memberId(order.getMemberId())
                .orderId(order.getId())
                .platformCode(order.getPlatformCode())
                .platformOrderId(order.getPlatformOrderId())
                .itemId(order.getItemId())
                .itemTitle(order.getItemTitle())
                .orderAmount(order.getFinalPrice() != null ? order.getFinalPrice() : BigDecimal.ZERO)
                .commissionAmount(order.getCommissionAmount() != null ? order.getCommissionAmount() : BigDecimal.ZERO)
                .rebateRate(allocation.getSelfRebateRate())
                .rebateAmount(selfRebateAmount)
                .rebateType(CpsRebateTypeEnum.REBATE.getType())
                .splitRole(CpsSplitRoleEnum.SELF.getRole())
                .sourceMemberId(order.getMemberId())
                .splitRuleId(allocation.getSplitRuleId())
                .splitRuleSnapshot(allocation.getRuleSnapshotJson())
                .rebateStatus(CpsRebateStatusEnum.PENDING.getStatus())
                .memberLevelIdSnapshot(member.getLevelId())
                .rebateAmountCent(allocation.getSelfRebateCent())
                .idempotencyKey(idempotencyKeySelf)
                .remark(shouldFreeze ? "多级分账自购冻结返利" : "多级分账自购直接入账返利")
                .build();
        if (existRecord == null) {
            rebateRecordMapper.insert(selfRecord);
        } else {
            selfRecord = existRecord;
        }

        // 2. 记录一级推荐人 (PARENT_L1) 返利记录
        CpsRebateRecordDO l1Record = null;
        if (allocation.getParentRebateCent() > 0 && buyerRel.getParentMemberId() != null) {
            CpsRebateRecordDO existL1 = rebateRecordMapper.selectByOrderIdTypeAndRole(
                    order.getId(), CpsRebateTypeEnum.REBATE.getType(), CpsSplitRoleEnum.PARENT_L1.getRole());
            if (existL1 == null) {
                l1Record = CpsRebateRecordDO.builder()
                        .memberId(buyerRel.getParentMemberId())
                        .orderId(order.getId())
                        .platformCode(order.getPlatformCode())
                        .platformOrderId(order.getPlatformOrderId())
                        .itemId(order.getItemId())
                        .itemTitle(order.getItemTitle())
                        .orderAmount(order.getFinalPrice() != null ? order.getFinalPrice() : BigDecimal.ZERO)
                        .commissionAmount(order.getCommissionAmount() != null ? order.getCommissionAmount() : BigDecimal.ZERO)
                        .rebateRate(allocation.getParentRebateRate())
                        .rebateAmount(moneyConverter.centToYuan(allocation.getParentRebateCent()))
                        .rebateType(CpsRebateTypeEnum.REBATE.getType())
                        .splitRole(CpsSplitRoleEnum.PARENT_L1.getRole())
                        .sourceMemberId(order.getMemberId())
                        .splitRuleId(allocation.getSplitRuleId())
                        .splitRuleSnapshot(allocation.getRuleSnapshotJson())
                        .rebateStatus(CpsRebateStatusEnum.PENDING.getStatus())
                        .rebateAmountCent(allocation.getParentRebateCent())
                        .idempotencyKey("order-split:l1:" + order.getId() + ":" + buyerRel.getParentMemberId())
                        .remark("下级自购多级分账一级推广提成")
                        .build();
                rebateRecordMapper.insert(l1Record);
            } else {
                l1Record = existL1;
            }
        }

        // 3. 记录二级推荐人 (GRANDPARENT_L2) 返利记录
        CpsRebateRecordDO l2Record = null;
        if (allocation.getGrandparentRebateCent() > 0 && buyerRel.getGrandparentMemberId() != null) {
            CpsRebateRecordDO existL2 = rebateRecordMapper.selectByOrderIdTypeAndRole(
                    order.getId(), CpsRebateTypeEnum.REBATE.getType(), CpsSplitRoleEnum.GRANDPARENT_L2.getRole());
            if (existL2 == null) {
                l2Record = CpsRebateRecordDO.builder()
                        .memberId(buyerRel.getGrandparentMemberId())
                        .orderId(order.getId())
                        .platformCode(order.getPlatformCode())
                        .platformOrderId(order.getPlatformOrderId())
                        .itemId(order.getItemId())
                        .itemTitle(order.getItemTitle())
                        .orderAmount(order.getFinalPrice() != null ? order.getFinalPrice() : BigDecimal.ZERO)
                        .commissionAmount(order.getCommissionAmount() != null ? order.getCommissionAmount() : BigDecimal.ZERO)
                        .rebateRate(allocation.getGrandparentRebateRate())
                        .rebateAmount(moneyConverter.centToYuan(allocation.getGrandparentRebateCent()))
                        .rebateType(CpsRebateTypeEnum.REBATE.getType())
                        .splitRole(CpsSplitRoleEnum.GRANDPARENT_L2.getRole())
                        .sourceMemberId(order.getMemberId())
                        .splitRuleId(allocation.getSplitRuleId())
                        .splitRuleSnapshot(allocation.getRuleSnapshotJson())
                        .rebateStatus(CpsRebateStatusEnum.PENDING.getStatus())
                        .rebateAmountCent(allocation.getGrandparentRebateCent())
                        .idempotencyKey("order-split:l2:" + order.getId() + ":" + buyerRel.getGrandparentMemberId())
                        .remark("下级自购多级分账二级推广提成")
                        .build();
                rebateRecordMapper.insert(l2Record);
            } else {
                l2Record = existL2;
            }
        }

        // 4. 资产记账（自购）
        CpsFreezeRecordDO freeze = null;
        if (!shouldFreeze) {
            rebateAssetService.creditOrderRebate(order.getId(), "order-rebate-credit:" + order.getId());
        } else {
            freeze = rebateAssetService.createOrderRebateFreeze(order.getId(), idempotencyKeySelf, freezeDays);
        }

        // 5. 资产记账（一级提成）
        if (l1Record != null) {
            String l1Key = l1Record.getIdempotencyKey();
            if (!shouldFreeze) {
                rebateAssetService.creditSplitRebate(l1Record.getMemberId(), order.getId(), order.getPlatformOrderId(),
                        "ORDER_SPLIT_CREDIT", "l1:" + order.getId(), allocation.getParentRebateCent(), l1Key);
                CpsRebateRecordDO updateL1 = new CpsRebateRecordDO();
                updateL1.setId(l1Record.getId());
                updateL1.setRebateStatus(CpsRebateStatusEnum.RECEIVED.getStatus());
                rebateRecordMapper.updateById(updateL1);
            } else {
                CpsFreezeRecordDO l1Freeze = rebateAssetService.createSplitRebateFreeze(
                        l1Record.getMemberId(), order.getId(), order.getPlatformOrderId(),
                        "ORDER_SPLIT_L1", "l1:" + order.getId(), allocation.getParentRebateCent(), freezeDays, l1Key);
                CpsRebateRecordDO updateL1 = new CpsRebateRecordDO();
                updateL1.setId(l1Record.getId());
                updateL1.setFreezeRecordId(l1Freeze.getId());
                rebateRecordMapper.updateById(updateL1);
            }
        }

        // 6. 资产记账（二级提成）
        if (l2Record != null) {
            String l2Key = l2Record.getIdempotencyKey();
            if (!shouldFreeze) {
                rebateAssetService.creditSplitRebate(l2Record.getMemberId(), order.getId(), order.getPlatformOrderId(),
                        "ORDER_SPLIT_CREDIT", "l2:" + order.getId(), allocation.getGrandparentRebateCent(), l2Key);
                CpsRebateRecordDO updateL2 = new CpsRebateRecordDO();
                updateL2.setId(l2Record.getId());
                updateL2.setRebateStatus(CpsRebateStatusEnum.RECEIVED.getStatus());
                rebateRecordMapper.updateById(updateL2);
            } else {
                CpsFreezeRecordDO l2Freeze = rebateAssetService.createSplitRebateFreeze(
                        l2Record.getMemberId(), order.getId(), order.getPlatformOrderId(),
                        "ORDER_SPLIT_L2", "l2:" + order.getId(), allocation.getGrandparentRebateCent(), freezeDays, l2Key);
                CpsRebateRecordDO updateL2 = new CpsRebateRecordDO();
                updateL2.setId(l2Record.getId());
                updateL2.setFreezeRecordId(l2Freeze.getId());
                rebateRecordMapper.updateById(updateL2);
            }
        }

        // 7. 累计团队出单与收益
        promoterRelationService.recordTeamStats(order.getMemberId(),
                allocation.getSelfRebateCent(), allocation.getParentRebateCent(), allocation.getGrandparentRebateCent());

        // 8. 直接入账由资产服务原子写入到账状态和版本；这里只回写冻结订单。
        if (shouldFreeze) {
            CpsOrderDO updateOrder = CpsOrderDO.builder()
                    .id(order.getId())
                    .orderStatus(CpsOrderStatusEnum.SETTLED.getStatus())
                    .realRebate(selfRebateAmount)
                    .rebateFreezeStatus(CpsFreezeStatusEnum.FROZEN.getStatus())
                    .planUnfreezeTime(freeze.getUnfreezeTime())
                    .build();
            if (orderMapper.updateRebateFreezeByStatusVersion(updateOrder, order.getStatusVersion()) != 1) {
                throw new IllegalStateException("订单状态已并发变更，回滚本次返利结算: orderId=" + order.getId());
            }
        }

        log.info("[settleOrderMultiLevel] 多级分账结算完成: orderId={}, selfRebate={}, l1Split={}, l2Split={}",
                order.getId(), selfRebateAmount, allocation.getParentRebateCent(), allocation.getGrandparentRebateCent());
        return true;
    }

    @Override
    public int[] batchSettle(int batchSize) {
        List<CpsOrderDO> orders = orderMapper.selectPendingSettleOrders(PENDING_SETTLE_STATUSES, batchSize);
        int successCount = 0, skipCount = 0, failCount = 0;

        for (CpsOrderDO order : orders) {
            try {
                boolean settled = settleExecutor.settleOne(order);
                if (settled) successCount++;
                else {
                    skipCount++;
                    markSettleRetry(order.getId(), "结算条件或配置暂不满足，保持待处理");
                }
            } catch (Exception e) {
                log.error("[batchSettle] 订单结算失败: orderId={}", order.getId(), e);
                markSettleRetry(order.getId(), e.getMessage());
                failCount++;
            }
        }

        log.info("[batchSettle] 批量结算完成: 成功={}, 跳过={}, 失败={}", successCount, skipCount, failCount);
        return new int[]{successCount, skipCount, failCount};
    }

    private void markSettleRetry(Long orderId, String reason) {
        String normalized = reason == null || reason.isBlank() ? "未知结算异常" : reason;
        if (normalized.length() > SETTLE_ERROR_MAX_LENGTH) {
            normalized = normalized.substring(0, SETTLE_ERROR_MAX_LENGTH);
        }
        orderMapper.markSettleRetry(orderId, normalized,
                LocalDateTime.now().plusMinutes(SETTLE_RETRY_DELAY_MINUTES));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reverseRebate(Long orderId) {
        // 查找原始返利记录
        CpsRebateRecordDO origRecord = rebateRecordMapper.selectByOrderIdAndType(
                orderId, CpsRebateTypeEnum.REBATE.getType());
        if (origRecord == null || CpsRebateStatusEnum.REFUNDED.getStatus().equals(origRecord.getRebateStatus())) {
            log.info("[reverseRebate] 无需扣回，订单未入账返利: orderId={}", orderId);
            return false;
        }

        rebateAssetService.reverseOrderRebate(orderId, "order-refund:" + orderId);

        log.info("[reverseRebate] 返利扣回成功: orderId={}, memberId={}, amount={}",
                orderId, origRecord.getMemberId(), origRecord.getRebateAmount());
        return true;
    }

    @Override
    public CpsRebateAccountDO getOrInitAccount(Long memberId) {
        CpsRebateAccountDO account = rebateAccountMapper.selectByMemberId(memberId);
        if (account == null) {
            account = CpsRebateAccountDO.builder()
                    .memberId(memberId)
                    .totalRebate(BigDecimal.ZERO)
                    .availableBalance(BigDecimal.ZERO)
                    .frozenBalance(BigDecimal.ZERO)
                    .debtBalance(BigDecimal.ZERO)
                    .withdrawnAmount(BigDecimal.ZERO)
                    .status(1)
                    .version(0)
                    .build();
            rebateAccountMapper.insert(account);
            log.info("[getOrInitAccount] 初始化返利账户: memberId={}", memberId);
        }
        return account;
    }

    // ==================== 私有方法 ====================

    /**
     * 计算返利金额
     *
     * <p>返利 = 佣金金额 × 返利比例（按配置匹配优先级决定）</p>
     *
     * <p>注：若 commissionAmount 为空，则使用 finalPrice × 默认比例作为兜底计算。</p>
     */
    private BigDecimal calculateRebateAmount(CpsOrderDO order, CpsRebateConfigDO config) {
        BigDecimal commission = order.getCommissionAmount();
        if (commission == null || commission.compareTo(BigDecimal.ZERO) <= 0) {
            // 佣金为0，无法结算
            return BigDecimal.ZERO;
        }

        if (config.getRebateRate() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal rebateRate = config.getRebateRate().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

        BigDecimal rebateAmount = commission.multiply(rebateRate).setScale(2, RoundingMode.HALF_UP);

        // 应用上下限
        if (config.getMaxRebateAmount() != null && config.getMaxRebateAmount().compareTo(BigDecimal.ZERO) > 0) {
            rebateAmount = rebateAmount.min(config.getMaxRebateAmount());
        }
        if (config.getMinRebateAmount() != null && config.getMinRebateAmount().compareTo(BigDecimal.ZERO) > 0) {
            rebateAmount = rebateAmount.max(config.getMinRebateAmount());
        }

        return rebateAmount;
    }

    private boolean shouldFreezeLegacy(BigDecimal rebateAmount, CpsRebateConfigDO config) {
        return config.getFreezeThresholdAmount() != null
                && config.getFreezeThresholdAmount().signum() > 0
                && rebateAmount.compareTo(config.getFreezeThresholdAmount()) > 0;
    }

    /**
     * 平台 settled 状态本身表示平台已完成订单结算，部分淘宝接口不会再返回收货时间。
     * 对 settled 订单只要求平台结算时间；其他状态仍要求完整的收货与结算时间。
     */
    private boolean isSettlementReady(CpsOrderDO order) {
        if (order == null || order.getSettleTime() == null) {
            return false;
        }
        if (CpsOrderStatusEnum.SETTLED.getStatus().equals(order.getOrderStatus())) {
            return true;
        }
        return CpsOrderStatusEnum.RECEIVED.getStatus().equals(order.getOrderStatus())
                && order.getConfirmReceiptTime() != null;
    }

}
