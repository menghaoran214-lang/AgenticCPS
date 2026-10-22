import request from '@/sheep/request';

const CpsWithdrawApi = {
  createWithdraw: ({
    accountId,
    amountCent,
    withdrawType,
    withdrawAccount,
    withdrawAccountName,
    smsCode,
    idempotencyKey,
  }) => {
    return request({
      url: '/cps/withdraw/create',
      method: 'POST',
      data: {
        accountId,
        amountCent,
        withdrawType,
        withdrawAccount,
        withdrawAccountName,
        smsCode,
        idempotencyKey,
      },
      custom: {
        auth: true,
      },
    });
  },
  getWithdrawPage: (params) => {
    return request({
      url: '/cps/withdraw/page',
      method: 'GET',
      params,
      custom: {
        showLoading: false,
        auth: true,
      },
    });
  },
  getWithdraw: (id) => {
    return request({
      url: `/cps/withdraw/${id}`,
      method: 'GET',
      custom: {
        showLoading: false,
        auth: true,
      },
    });
  },
  getAccountList: () => {
    return request({
      url: '/cps/withdraw-account/list',
      method: 'GET',
      custom: {
        showLoading: false,
        auth: true,
      },
    });
  },
  saveAccount: (data) => {
    return request({
      url: '/cps/withdraw-account/save',
      method: 'POST',
      data,
      custom: {
        auth: true,
      },
    });
  },
  setDefaultAccount: (id) => {
    return request({
      url: `/cps/withdraw-account/set-default?id=${id}`,
      method: 'PUT',
      custom: {
        auth: true,
      },
    });
  },
  deleteAccount: (id) => {
    return request({
      url: `/cps/withdraw-account/delete?id=${id}`,
      method: 'DELETE',
      custom: {
        auth: true,
      },
    });
  },
};

export default CpsWithdrawApi;
