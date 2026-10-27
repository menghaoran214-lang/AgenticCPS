package com.qiji.cps.module.cps.integration;

import com.qiji.cps.module.pay.framework.pay.core.client.impl.wallet.WalletPayClient;
import com.qiji.cps.module.pay.framework.pay.core.client.impl.NonePayClientConfig;
import com.qiji.cps.module.pay.dal.dataobject.order.*;
import com.qiji.cps.module.pay.dal.dataobject.refund.PayRefundDO;
import com.qiji.cps.module.pay.dal.dataobject.transfer.PayTransferDO;
import com.qiji.cps.module.pay.dal.dataobject.wallet.PayWalletTransactionDO;
import com.qiji.cps.module.pay.enums.wallet.PayWalletBizTypeEnum;
import com.qiji.cps.module.pay.enums.order.PayOrderStatusEnum;
import com.qiji.cps.module.pay.enums.refund.PayRefundStatusEnum;
import com.qiji.cps.module.pay.enums.transfer.PayTransferStatusEnum;
import com.qiji.cps.module.pay.service.wallet.PayWalletTransactionService;
import com.qiji.cps.module.pay.service.order.PayOrderService;
import com.qiji.cps.module.pay.service.refund.PayRefundService;
import com.qiji.cps.module.pay.service.transfer.PayTransferService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CpsWalletLookupSafetyTest {
    static class Client extends WalletPayClient {
        Client() { super(1L, new NonePayClientConfig()); }
        void order() { doGetOrder("P24"); }
        void refund() { doGetRefund("P24", "R25"); }
        void transfer() { doGetTransfer("T26"); }
    }
    PayWalletTransactionDO transaction(String biz, PayWalletBizTypeEnum type) {
        PayWalletTransactionDO t = new PayWalletTransactionDO();
        t.setNo("W-recorded"); t.setBizId(biz); t.setBizType(type.getType()); return t;
    }
    @Test void successfulOrderReadsRecordedChannelTransaction() {
        Client client = new Client();
        PayOrderService orders = mock(PayOrderService.class);
        PayWalletTransactionService transactions = mock(PayWalletTransactionService.class);
        ReflectionTestUtils.setField(client, "orderService", orders);
        ReflectionTestUtils.setField(client, "walletTransactionService", transactions);
        when(orders.getOrderExtensionByNo("P24")).thenReturn(PayOrderExtensionDO.builder()
                .orderId(24L).status(PayOrderStatusEnum.SUCCESS.getStatus()).build());
        when(orders.getOrder(24L)).thenReturn(PayOrderDO.builder().id(24L).channelOrderNo("W-recorded").build());
        when(transactions.getWalletTransactionByNo("W-recorded")).thenReturn(transaction("24", PayWalletBizTypeEnum.PAYMENT));
        client.order();
        verify(transactions).getWalletTransactionByNo("W-recorded");
        verifyNoMoreInteractions(transactions);
    }
    @Test void successfulRefundReadsRecordedChannelTransaction() {
        Client client = new Client();
        PayRefundService refunds = mock(PayRefundService.class);
        PayWalletTransactionService transactions = mock(PayWalletTransactionService.class);
        ReflectionTestUtils.setField(client, "refundService", refunds);
        ReflectionTestUtils.setField(client, "walletTransactionService", transactions);
        when(refunds.getRefundByNo("R25")).thenReturn(PayRefundDO.builder().id(25L)
                .status(PayRefundStatusEnum.SUCCESS.getStatus()).channelRefundNo("W-recorded").build());
        when(transactions.getWalletTransactionByNo("W-recorded")).thenReturn(transaction("25", PayWalletBizTypeEnum.PAYMENT_REFUND));
        client.refund();
        verify(transactions).getWalletTransactionByNo("W-recorded");
        verifyNoMoreInteractions(transactions);
    }
    @Test void transferLookupUsesStoredNoMatchingCreationBusinessKey() {
        Client client = new Client();
        PayTransferService transfers = mock(PayTransferService.class);
        PayWalletTransactionService transactions = mock(PayWalletTransactionService.class);
        ReflectionTestUtils.setField(client, "transferService", transfers);
        ReflectionTestUtils.setField(client, "walletTransactionService", transactions);
        PayTransferDO transfer = new PayTransferDO();
        transfer.setId(26L); transfer.setNo("T26"); transfer.setStatus(PayTransferStatusEnum.SUCCESS.getStatus());
        transfer.setChannelTransferNo("W-recorded");
        when(transfers.getTransferByNo("T26")).thenReturn(transfer);
        when(transactions.getWalletTransactionByNo("W-recorded")).thenReturn(transaction("T26", PayWalletBizTypeEnum.TRANSFER));
        client.transfer();
        verify(transactions).getWalletTransactionByNo("W-recorded");
        verifyNoMoreInteractions(transactions);
    }
    @Test void mismatchedRecordedTransactionFailsClosed() {
        Client client = new Client();
        PayTransferService transfers = mock(PayTransferService.class);
        PayWalletTransactionService transactions = mock(PayWalletTransactionService.class);
        ReflectionTestUtils.setField(client, "transferService", transfers);
        ReflectionTestUtils.setField(client, "walletTransactionService", transactions);
        PayTransferDO transfer = new PayTransferDO();
        transfer.setId(26L); transfer.setNo("T26"); transfer.setStatus(PayTransferStatusEnum.SUCCESS.getStatus());
        transfer.setChannelTransferNo("W-recorded");
        when(transfers.getTransferByNo("T26")).thenReturn(transfer);
        when(transactions.getWalletTransactionByNo("W-recorded")).thenReturn(transaction("unrelated", PayWalletBizTypeEnum.TRANSFER));
        assertThrows(IllegalArgumentException.class, client::transfer);
    }
}
