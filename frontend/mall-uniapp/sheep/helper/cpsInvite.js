import CpsPromoterApi from '@/sheep/api/cps/promoter';
import { createInviteManager } from './cpsInviteCore';

export default createInviteManager({
  get: (key) => uni.getStorageSync(key),
  set: (key, value) => uni.setStorageSync(key, value),
  remove: (key) => uni.removeStorageSync(key),
  isLogin: () => Boolean(uni.getStorageSync('token')),
  bind: (code) => CpsPromoterApi.bind(code),
});
