package com.qiji.cps.module.cps.service.withdraw;

public record CpsWithdrawCreateCommand(Long memberId, long amountCent, String withdrawType,
                                       String withdrawAccount, String withdrawAccountName,
                                       String idempotencyKey, Long accountId, Integer riskLevel,
                                       String clientIp, String userAgent) {

    public CpsWithdrawCreateCommand(Long memberId, long amountCent, String withdrawType,
                                   String withdrawAccount, String withdrawAccountName,
                                   String idempotencyKey) {
        this(memberId, amountCent, withdrawType, withdrawAccount, withdrawAccountName, idempotencyKey,
                null, 0, null, null);
    }

}
