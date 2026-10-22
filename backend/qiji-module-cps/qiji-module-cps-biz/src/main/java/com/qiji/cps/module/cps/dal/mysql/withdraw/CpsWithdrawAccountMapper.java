package com.qiji.cps.module.cps.dal.mysql.withdraw;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiji.cps.framework.mybatis.core.mapper.BaseMapperX;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.module.cps.dal.dataobject.withdraw.CpsWithdrawAccountDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 会员提现账户 Mapper
 *
 * @author CPS System
 */
@Mapper
public interface CpsWithdrawAccountMapper extends BaseMapperX<CpsWithdrawAccountDO> {

    default List<CpsWithdrawAccountDO> selectListByMemberId(Long memberId) {
        return selectList(new LambdaQueryWrapperX<CpsWithdrawAccountDO>()
                .eq(CpsWithdrawAccountDO::getMemberId, memberId)
                .orderByDesc(CpsWithdrawAccountDO::getIsDefault)
                .orderByDesc(CpsWithdrawAccountDO::getLastUsedTime)
                .orderByDesc(CpsWithdrawAccountDO::getId));
    }

    default CpsWithdrawAccountDO selectByMemberIdAndId(Long memberId, Long id) {
        return selectOne(new LambdaQueryWrapperX<CpsWithdrawAccountDO>()
                .eq(CpsWithdrawAccountDO::getMemberId, memberId)
                .eq(CpsWithdrawAccountDO::getId, id)
                .last("LIMIT 1"));
    }

    default CpsWithdrawAccountDO selectByMemberIdAndAccount(Long memberId, String type, String account) {
        return selectOne(new LambdaQueryWrapperX<CpsWithdrawAccountDO>()
                .eq(CpsWithdrawAccountDO::getMemberId, memberId)
                .eq(CpsWithdrawAccountDO::getType, type)
                .eq(CpsWithdrawAccountDO::getAccount, account)
                .last("LIMIT 1"));
    }

    default int clearDefaultByMemberId(Long memberId) {
        return update(null, new LambdaUpdateWrapper<CpsWithdrawAccountDO>()
                .eq(CpsWithdrawAccountDO::getMemberId, memberId)
                .set(CpsWithdrawAccountDO::getIsDefault, false));
    }

}
