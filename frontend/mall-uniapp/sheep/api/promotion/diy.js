import request from '@/sheep/request';

const DiyApi = {
  getUsedDiyTemplate: () => {
    return request({
      url: '/promotion/diy-template/used',
      method: 'GET',
      custom: {
        showError: false,
        showLoading: false,
        isToken: false,
      },
    });
  },
  getDiyTemplate: (id) => {
    return request({
      url: '/promotion/diy-template/get',
      method: 'GET',
      params: {
        id
      },
      custom: {
        showError: false,
        showLoading: false,
        isToken: false,
      },
    });
  },
  getDiyPage: (id) => {
    return request({
      url: '/promotion/diy-page/get',
      method: 'GET',
      params: {
        id
      },
      custom: {
        isToken: false,
      },
    });
  },
};

export default DiyApi;
