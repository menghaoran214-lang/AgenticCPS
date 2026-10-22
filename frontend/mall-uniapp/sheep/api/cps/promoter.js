import request from '@/sheep/request';

const CpsPromoterApi = {
  getSummary: () => {
    return request({
      url: '/cps/promoter/summary',
      method: 'GET',
      custom: {
        showLoading: false,
        auth: true,
      },
    });
  },
  bind: (inviteCode) => {
    return request({
      url: '/cps/promoter/bind',
      method: 'POST',
      data: { inviteCode },
      custom: {
        showLoading: true,
        auth: true,
      },
    });
  },
  getTeamPage: (params) => {
    return request({
      url: '/cps/promoter/team/page',
      method: 'GET',
      params,
      custom: {
        showLoading: false,
        auth: true,
      },
    });
  },
  getTeamOrdersPage: (params) => {
    return request({
      url: '/cps/promoter/team-orders/page',
      method: 'GET',
      params,
      custom: {
        showLoading: false,
        auth: true,
      },
    });
  },
};

export default CpsPromoterApi;
