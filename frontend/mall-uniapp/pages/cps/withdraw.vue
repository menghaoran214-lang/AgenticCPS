<template>
  <s-layout title="返利提现" navbar="inner">
    <!-- 顶部可用返利面板 -->
    <view class="balance-panel ui-BG-Main-Gradient">
      <view class="balance-label">可提现返利</view>
      <view class="balance-value">¥{{ formatMoney(state.account.withdrawableBalance) }}</view>
      <view class="balance-sub"
        >冻结 ¥{{ formatMoney(state.account.frozenBalance)
        }}<text v-if="toNumber(state.account.debtBalance) > 0"
          >· 欠款 ¥{{ formatMoney(state.account.debtBalance) }}</text
        ></view
      >
    </view>

    <!-- 提现操作主卡片 -->
    <view class="draw-card">
      <view class="section-title">提现金额</view>
      <view class="amount-input">
        <text>¥</text>
        <input v-model="state.form.amountYuan" type="digit" placeholder="0.00" />
      </view>
      <view class="amount-action-row">
        <text class="amount-tip">单笔限额 1 ~ 5,000 元</text>
        <button class="ss-reset-button all-button" @tap="fillAll">全部提现</button>
      </view>

      <!-- 收款账户模块 -->
      <view class="section-title method-title">
        <text>收款账户</text>
        <view class="method-title-side" @tap="openAccountSelectPopup">
          <text v-if="state.accountList.length > 0">切换账户</text>
          <text v-else class="add-link">+ 添加账户</text>
          <text class="arrow-right">›</text>
        </view>
      </view>

      <!-- 1. 已选中绑定账户卡片 -->
      <view v-if="state.selectedAccount" class="selected-account-box" @tap="openAccountSelectPopup">
        <view class="account-badge" :class="state.selectedAccount.type">
          {{ withdrawTypeText(state.selectedAccount.type) }}
        </view>
        <view class="account-info">
          <view class="account-number">{{ state.selectedAccount.accountMasked || state.selectedAccount.account }}</view>
          <view class="account-name">{{ state.selectedAccount.accountNameMasked || state.selectedAccount.accountName }}</view>
        </view>
        <view v-if="state.selectedAccount.isDefault" class="default-pill">默认</view>
      </view>

      <!-- 2. 手动临时输入收款账号 -->
      <view v-else-if="state.useCustomAccount" class="custom-account-box">
        <view class="method-row">
          <button
            v-for="item in withdrawTypes"
            :key="item.value"
            class="ss-reset-button method-button"
            :class="{ active: state.form.withdrawType === item.value }"
            @tap="state.form.withdrawType = item.value"
            >{{ item.label }}</button
          >
        </view>
        <uni-easyinput v-model="state.form.withdrawAccount" placeholder="请输入收款账号" />
        <uni-easyinput v-model="state.form.withdrawAccountName" placeholder="请输入收款姓名（需与实名一致）" />
      </view>

      <!-- 3. 未绑定账户空态引导 -->
      <view v-else class="empty-account-box" @tap="openAddAccountPopup">
        <text class="empty-account-title">+ 绑定提现账户（推荐）</text>
        <text class="empty-account-desc">绑定常用提现账户，小额（≤200元）享受免审秒到账</text>
      </view>

      <!-- 风控分级与安全提示卡片 -->
      <view class="security-card" :class="{ 'warning-mode': needWithdrawSms }">
        <view class="security-header">
          <text class="security-icon">{{ needWithdrawSms ? '🛡️' : '⚡' }}</text>
          <text class="security-title">{{ needWithdrawSms ? '大额/安全核验通道' : '极速免审通道' }}</text>
        </view>
        <view class="security-desc">
          <text v-if="!needWithdrawSms">已绑定账户单笔 ≤ 200 元，免短信验证，提交后系统秒级自动打款到账。</text>
          <text v-else-if="state.useCustomAccount">使用未绑定账户提现，需完成手机短信核验，提交后进入人工安全审核。</text>
          <text v-else>单笔提现超过 200 元（大额提现），需完成手机短信核验，提交后进入安全审核。</text>
        </view>

        <!-- 短信验证码输入行 (仅在大额或未绑定账户时展示) -->
        <view v-if="needWithdrawSms" class="sms-verify-row">
          <view class="sms-input-wrap">
            <uni-easyinput
              v-model="state.form.smsCode"
              placeholder="请输入短信验证码"
              type="number"
              maxlength="6"
            />
          </view>
          <button
            class="ss-reset-button sms-send-btn"
            :disabled="state.withdrawSmsCountdown > 0 || state.withdrawSmsSending"
            @tap="sendSmsCodeForWithdraw"
          >
            {{ state.withdrawSmsCountdown > 0 ? `${state.withdrawSmsCountdown}s` : '获取验证码' }}
          </button>
        </view>
      </view>

      <view class="form-tip">提交后将根据风控等级执行秒到账或人工审核，请确保收款信息准确。</view>
      <button
        class="ss-reset-button submit-button ui-BG-Main-Gradient"
        :disabled="state.submitting"
        @tap="onConfirm"
        >{{ state.submitting ? '提交中...' : '确认提现' }}</button
      >
    </view>

    <!-- 提现记录部分 -->
    <view class="record-section">
      <view class="record-head"
        ><view class="section-title">提现记录</view
        ><text>共 {{ state.pagination.total }} 笔</text></view
      >
      <view
        v-for="item in state.pagination.list"
        :key="item.id"
        class="withdraw-row"
        @tap="getWithdraw(item.id)"
      >
        <view
          ><strong>{{ item.withdrawNo || '提现申请' }}</strong
          ><text
            >{{ withdrawTypeText(item.withdrawType) }} · {{ formatTime(item.createTime) }}</text
          ></view
        >
        <view class="withdraw-side"
          ><strong>¥{{ formatCentMoney(item.amountCent) }}</strong
          ><text>{{ withdrawStatusText(item.status) }}</text></view
        >
      </view>
      <view v-if="state.errorMessage && !state.pagination.list.length" class="empty-state"
        ><text>{{ state.errorMessage }}</text
        ><button class="ss-reset-button retry-button" @tap="retryRecords">重试</button></view
      >
      <s-empty
        v-else-if="state.pagination.total === 0 && state.loadStatus !== 'loading'"
        icon="/static/data-empty.png"
        text="暂无提现记录"
      />
      <uni-load-more
        v-if="state.pagination.total > 0 || state.loadStatus === 'loading'"
        :status="state.loadStatus"
        @tap="loadMore"
      />
    </view>

    <!-- 提现详情弹窗 -->
    <su-popup
      :show="state.showDetailPopup"
      type="bottom"
      round="20"
      showClose
      @close="state.showDetailPopup = false"
    >
      <view v-if="state.currentWithdraw" class="detail-panel">
        <view class="detail-title">提现详情</view>
        <view class="detail-item"
          ><text>提现单号</text><text>{{ state.currentWithdraw.withdrawNo || '-' }}</text></view
        >
        <view class="detail-item"
          ><text>提现金额</text
          ><text>¥{{ formatCentMoney(state.currentWithdraw.amountCent) }}</text></view
        >
        <view class="detail-item"
          ><text>申请状态</text
          ><text>{{ withdrawStatusText(state.currentWithdraw.status) }}</text></view
        >
        <view class="detail-item"
          ><text>打款状态</text
          ><text>{{ transferStatusText(state.currentWithdraw.transferStatus) }}</text></view
        >
        <view class="detail-item"
          ><text>审核备注</text><text>{{ state.currentWithdraw.reviewNote || '-' }}</text></view
        >
        <view class="detail-item"
          ><text>打款时间</text
          ><text>{{ formatTime(state.currentWithdraw.transferTime) }}</text></view
        >
      </view>
    </su-popup>

    <!-- 账户选择弹窗 -->
    <su-popup
      :show="state.showAccountSelectPopup"
      type="bottom"
      round="20"
      showClose
      @close="state.showAccountSelectPopup = false"
    >
      <view class="popup-box">
        <view class="popup-title">选择提现账户</view>
        <view class="account-select-list">
          <view
            v-for="acc in state.accountList"
            :key="acc.id"
            class="account-select-item"
            :class="{ active: state.selectedAccountId === acc.id }"
            @tap="selectAccount(acc)"
          >
            <view class="account-badge" :class="acc.type">{{ withdrawTypeText(acc.type) }}</view>
            <view class="account-detail">
              <view class="acc-row">
                <text class="acc-num">{{ acc.accountMasked }}</text>
                <text v-if="acc.isDefault" class="default-pill">默认</text>
              </view>
              <view class="acc-name">{{ acc.accountNameMasked }}</view>
            </view>
            <view class="acc-check">
              <text v-if="state.selectedAccountId === acc.id">✓</text>
            </view>
          </view>

          <!-- 手动输入选项 -->
          <view
            class="account-select-item"
            :class="{ active: state.useCustomAccount }"
            @tap="selectCustomAccount"
          >
            <view class="account-badge other">手动</view>
            <view class="account-detail">
              <view class="acc-num">使用其他账号临时提现</view>
              <view class="acc-name">临时输入收款账号，需短信核验与人工审核</view>
            </view>
            <view class="acc-check">
              <text v-if="state.useCustomAccount">✓</text>
            </view>
          </view>
        </view>

        <button class="ss-reset-button add-account-btn" @tap="openAddAccountPopup">
          + 添加常用提现账户
        </button>
      </view>
    </su-popup>

    <!-- 添加提现账户弹窗 -->
    <su-popup
      :show="state.showAddAccountPopup"
      type="bottom"
      round="20"
      showClose
      @close="state.showAddAccountPopup = false"
    >
      <view class="popup-box">
        <view class="popup-title">添加提现账户</view>
        <view class="add-account-form">
          <view class="method-row ss-m-b-20">
            <button
              v-for="item in withdrawTypes"
              :key="item.value"
              class="ss-reset-button method-button"
              :class="{ active: state.addForm.type === item.value }"
              @tap="state.addForm.type = item.value"
            >
              {{ item.label }}
            </button>
          </view>

          <view class="form-item-label">收款账号</view>
          <uni-easyinput
            v-model="state.addForm.account"
            placeholder="请输入支付宝账号 / 微信账号"
          />

          <view class="form-item-label ss-m-t-20">真实姓名</view>
          <uni-easyinput
            v-model="state.addForm.accountName"
            placeholder="请输入真实姓名（需与实名认证一致）"
          />

          <view class="form-item-label ss-m-t-20">安全短信验证码</view>
          <view class="sms-verify-row ss-m-t-10">
            <view class="sms-input-wrap">
              <uni-easyinput
                v-model="state.addForm.smsCode"
                placeholder="请输入短信验证码"
                type="number"
                maxlength="6"
              />
            </view>
            <button
              class="ss-reset-button sms-send-btn"
              :disabled="state.addAccountSmsCountdown > 0 || state.addAccountSmsSending"
              @tap="sendSmsCodeForAddAccount"
            >
              {{ state.addAccountSmsCountdown > 0 ? `${state.addAccountSmsCountdown}s` : '获取验证码' }}
            </button>
          </view>

          <view class="default-switch-row ss-m-t-20" @tap="state.addForm.isDefault = !state.addForm.isDefault">
            <text>设为默认提现账户</text>
            <switch :checked="state.addForm.isDefault" color="var(--ui-BG-Main)" style="transform: scale(0.8);" />
          </view>

          <button
            class="ss-reset-button submit-button ui-BG-Main-Gradient ss-m-t-30"
            :disabled="state.addAccountSubmitting"
            @tap="submitAddAccount"
          >
            {{ state.addAccountSubmitting ? '保存中...' : '确认绑定' }}
          </button>
        </view>
      </view>
    </su-popup>
  </s-layout>
</template>

<script setup>
  import { reactive, computed, watch } from 'vue';
  import { onLoad, onReachBottom } from '@dcloudio/uni-app';
  import { concat } from 'lodash-es';
  import sheep from '@/sheep';
  import { resetPagination } from '@/sheep/helper/utils';
  import {
    createCpsIdempotencyKey,
    formatCentMoney,
    formatMoney,
    toNumber,
  } from '@/sheep/helper/cps';
  import CpsRebateApi from '@/sheep/api/cps/rebate';
  import CpsWithdrawApi from '@/sheep/api/cps/withdraw';
  import AuthUtil from '@/sheep/api/member/auth';
  import { trackCpsEvent } from '@/sheep/helper/cpsAnalytics';

  const state = reactive({
    account: { withdrawableBalance: 0, frozenBalance: 0, debtBalance: 0 },
    form: {
      amountYuan: '',
      withdrawType: 'alipay',
      withdrawAccount: '',
      withdrawAccountName: '',
      smsCode: '',
    },
    // 账户列表与选中状态
    accountList: [],
    selectedAccount: null,
    selectedAccountId: null,
    useCustomAccount: false,
    // 弹窗状态
    showAccountSelectPopup: false,
    showAddAccountPopup: false,
    showDetailPopup: false,
    currentWithdraw: null,
    // 添加账户表单
    addForm: {
      type: 'alipay',
      account: '',
      accountName: '',
      isDefault: true,
      smsCode: '',
    },
    addAccountSubmitting: false,
    addAccountSmsCountdown: 0,
    addAccountSmsSending: false,
    // 提现验证码倒计时
    withdrawSmsCountdown: 0,
    withdrawSmsSending: false,
    withdrawIdempotencyKey: '',
    submitting: false,
    loadStatus: '',
    errorMessage: '',
    pagination: { list: [], total: 0, pageNo: 1, pageSize: 8 },
  });

  const withdrawTypes = [
    { label: '支付宝', value: 'alipay' },
    { label: '微信', value: 'wechat' },
  ];

  // 风控规则：金额大于 200 元或使用未绑定账号，必须走短信验证及审核
  const isExceedFastLimit = computed(() => {
    return toNumber(state.form.amountYuan) > 200;
  });

  const needWithdrawSms = computed(() => {
    return isExceedFastLimit.value || state.useCustomAccount || !state.selectedAccountId;
  });

  async function getAccount() {
    const { code, data } = await CpsRebateApi.getAccount();
    if (code === 0) state.account = data || {};
  }

  async function getAccountList(targetId) {
    try {
      const { code, data } = await CpsWithdrawApi.getAccountList();
      if (code === 0) {
        state.accountList = data || [];
        if (state.accountList.length > 0) {
          let matched = null;
          if (targetId) {
            matched = state.accountList.find((a) => a.id === targetId);
          }
          if (!matched && state.selectedAccountId) {
            matched = state.accountList.find((a) => a.id === state.selectedAccountId);
          }
          if (!matched) {
            matched = state.accountList.find((a) => a.isDefault) || state.accountList[0];
          }
          selectAccount(matched);
        } else {
          state.useCustomAccount = true;
          state.selectedAccount = null;
          state.selectedAccountId = null;
        }
      }
    } catch (e) {
      console.error('getAccountList error', e);
    }
  }

  function selectAccount(acc) {
    if (!acc) return;
    state.selectedAccount = acc;
    state.selectedAccountId = acc.id;
    state.useCustomAccount = false;
    state.form.withdrawType = acc.type;
    state.form.withdrawAccount = acc.account;
    state.form.withdrawAccountName = acc.accountName;
    state.showAccountSelectPopup = false;
  }

  function selectCustomAccount() {
    state.selectedAccount = null;
    state.selectedAccountId = null;
    state.useCustomAccount = true;
    state.form.withdrawAccount = '';
    state.form.withdrawAccountName = '';
    state.showAccountSelectPopup = false;
  }

  function openAccountSelectPopup() {
    state.showAccountSelectPopup = true;
  }

  function openAddAccountPopup() {
    state.showAccountSelectPopup = false;
    state.addForm.type = 'alipay';
    state.addForm.account = '';
    state.addForm.accountName = '';
    state.addForm.isDefault = state.accountList.length === 0;
    state.addForm.smsCode = '';
    state.showAddAccountPopup = true;
  }

  // 发送添加账户短信验证码 (场景 5: MEMBER_WITHDRAW)
  async function sendSmsCodeForAddAccount() {
    if (state.addAccountSmsSending || state.addAccountSmsCountdown > 0) return;
    state.addAccountSmsSending = true;
    try {
      const userMobile = sheep.$store('user').userInfo?.mobile || '';
      const { code } = await AuthUtil.sendSmsCode(userMobile, 5);
      if (code === 0) {
        sheep.$helper.toast('验证码已发送');
        state.addAccountSmsCountdown = 60;
        const timer = setInterval(() => {
          state.addAccountSmsCountdown--;
          if (state.addAccountSmsCountdown <= 0) {
            clearInterval(timer);
          }
        }, 1000);
      }
    } catch (e) {
      sheep.$helper.toast('发送验证码失败，请重试');
    } finally {
      state.addAccountSmsSending = false;
    }
  }

  // 提交添加提现账户
  async function submitAddAccount() {
    if (!state.addForm.account.trim()) {
      sheep.$helper.toast('请输入收款账号');
      return;
    }
    if (!state.addForm.accountName.trim()) {
      sheep.$helper.toast('请输入收款姓名');
      return;
    }
    if (!state.addForm.smsCode.trim()) {
      sheep.$helper.toast('请输入短信验证码');
      return;
    }
    if (state.addAccountSubmitting) return;
    state.addAccountSubmitting = true;
    try {
      const { code, data } = await CpsWithdrawApi.saveAccount({
        type: state.addForm.type,
        account: state.addForm.account.trim(),
        accountName: state.addForm.accountName.trim(),
        isDefault: state.addForm.isDefault,
        smsCode: state.addForm.smsCode.trim(),
      });
      if (code === 0) {
        sheep.$helper.toast('账户绑定成功');
        state.showAddAccountPopup = false;
        await getAccountList(data);
      }
    } catch (e) {
      // 错误由 request 统一弹出或拦截
    } finally {
      state.addAccountSubmitting = false;
    }
  }

  // 发送提现短信验证码 (场景 5: MEMBER_WITHDRAW)
  async function sendSmsCodeForWithdraw() {
    if (state.withdrawSmsSending || state.withdrawSmsCountdown > 0) return;
    state.withdrawSmsSending = true;
    try {
      const userMobile = sheep.$store('user').userInfo?.mobile || '';
      const { code } = await AuthUtil.sendSmsCode(userMobile, 5);
      if (code === 0) {
        sheep.$helper.toast('验证码已发送');
        state.withdrawSmsCountdown = 60;
        const timer = setInterval(() => {
          state.withdrawSmsCountdown--;
          if (state.withdrawSmsCountdown <= 0) {
            clearInterval(timer);
          }
        }, 1000);
      }
    } catch (e) {
      sheep.$helper.toast('发送验证码失败，请重试');
    } finally {
      state.withdrawSmsSending = false;
    }
  }

  async function getWithdrawPage() {
    if (state.loadStatus === 'loading') return;
    state.loadStatus = 'loading';
    state.errorMessage = '';
    try {
      const { code, data } = await CpsWithdrawApi.getWithdrawPage({
        pageNo: state.pagination.pageNo,
        pageSize: state.pagination.pageSize,
      });
      if (code !== 0) {
        state.errorMessage = '提现记录加载失败';
        state.loadStatus = 'more';
        return;
      }
      const list = data?.list || [];
      state.pagination.list = concat(state.pagination.list, list);
      state.pagination.total = data?.total || 0;
      state.loadStatus = state.pagination.list.length < state.pagination.total ? 'more' : 'noMore';
    } catch (error) {
      if (error?.code === 401) {
        state.loadStatus = 'more';
        return;
      }
      state.errorMessage = '网络异常，请重试';
      state.loadStatus = 'more';
    }
  }

  async function getWithdraw(id) {
    try {
      const { code, data } = await CpsWithdrawApi.getWithdraw(id);
      if (code === 0) {
        state.currentWithdraw = data;
        state.showDetailPopup = true;
      }
    } catch (error) {
      if (error?.code !== 401) sheep.$helper.toast('提现详情加载失败，请重试');
    }
  }

  function ensureWithdrawIdempotencyKey() {
    if (!state.withdrawIdempotencyKey)
      state.withdrawIdempotencyKey = createCpsIdempotencyKey('cps-withdraw');
  }

  function resetWithdrawIdempotencyKey() {
    state.withdrawIdempotencyKey = '';
  }

  async function onConfirm() {
    const amountCent = Math.round(toNumber(state.form.amountYuan) * 100);
    if (
      amountCent <= 0 ||
      amountCent > Math.round(toNumber(state.account.withdrawableBalance) * 100)
    ) {
      sheep.$helper.toast('提现金额必须大于 0 且不能超过可提现余额');
      return;
    }
    if (amountCent < 100) {
      sheep.$helper.toast('单笔提现金额最低 1 元');
      return;
    }
    if (amountCent > 500000) {
      sheep.$helper.toast('单笔提现金额最高 5,000 元');
      return;
    }
    if (!state.form.withdrawType) {
      sheep.$helper.toast('请选择提现方式');
      return;
    }
    if (!state.form.withdrawAccount.trim()) {
      sheep.$helper.toast('请输入收款账号');
      return;
    }
    if (needWithdrawSms.value && !state.form.smsCode.trim()) {
      sheep.$helper.toast('请输入短信验证码');
      return;
    }
    if (state.submitting) return;
    ensureWithdrawIdempotencyKey();
    state.submitting = true;
    try {
      const { code } = await CpsWithdrawApi.createWithdraw({
        accountId: state.selectedAccountId || undefined,
        amountCent,
        withdrawType: state.form.withdrawType,
        withdrawAccount: state.form.withdrawAccount.trim(),
        withdrawAccountName: state.form.withdrawAccountName.trim(),
        smsCode: needWithdrawSms.value ? state.form.smsCode.trim() : undefined,
        idempotencyKey: state.withdrawIdempotencyKey,
      });
      if (code !== 0) {
        trackCpsEvent('cps_withdraw_submit', {
          withdrawType: state.form.withdrawType,
          result: 'failed',
        });
        return;
      }
      trackCpsEvent('cps_withdraw_submit', {
        withdrawType: state.form.withdrawType,
        result: 'success',
      });
      sheep.$helper.toast(needWithdrawSms.value ? '提现申请已提交，进入安全审核' : '提现申请成功，自动打款中');
      resetWithdrawIdempotencyKey();
      state.form.amountYuan = '';
      state.form.smsCode = '';
      if (state.useCustomAccount) {
        state.form.withdrawAccount = '';
        state.form.withdrawAccountName = '';
      }
      resetPagination(state.pagination);
      await Promise.all([getAccount(), getWithdrawPage()]);
    } catch (error) {
      if (error?.code === 401) return;
      trackCpsEvent('cps_withdraw_submit', {
        withdrawType: state.form.withdrawType,
        result: 'network_error',
      });
      sheep.$helper.toast('提交状态未知，请保持表单不变后重试');
    } finally {
      state.submitting = false;
    }
  }

  watch(
    () => [
      state.form.amountYuan,
      state.form.withdrawType,
      state.form.withdrawAccount,
      state.form.withdrawAccountName,
      state.form.smsCode,
    ],
    resetWithdrawIdempotencyKey,
  );

  function fillAll() {
    state.form.amountYuan = formatMoney(state.account.withdrawableBalance);
  }

  function retryRecords() {
    resetPagination(state.pagination);
    getWithdrawPage();
  }

  function loadMore() {
    if (state.loadStatus === 'loading' || state.loadStatus === 'noMore') return;
    state.pagination.pageNo++;
    getWithdrawPage();
  }

  function withdrawTypeText(value) {
    return { alipay: '支付宝', wechat: '微信' }[value] || value || '未知方式';
  }

  function withdrawStatusText(value) {
    return (
      {
        created: '待审核',
        reviewing: '打款中',
        success: '提现成功',
        failed: '提现失败',
        rejected: '已驳回',
        closed: '已关闭',
      }[value] ||
      value ||
      '处理中'
    );
  }

  function transferStatusText(value) {
    return (
      {
        none: '未打款',
        pending: '打款中',
        success: '打款成功',
        failed: '打款失败',
        closed: '已关闭',
      }[value] ||
      value ||
      '未打款'
    );
  }

  function formatTime(value) {
    return value ? sheep.$helper.timeFormat(value, 'yyyy-mm-dd hh:MM') : '-';
  }

  onLoad(async () => {
    try {
      await Promise.all([getAccount(), getAccountList(), getWithdrawPage()]);
    } catch (error) {
      if (error?.code !== 401) state.errorMessage = '网络异常，请重试';
    }
  });

  onReachBottom(loadMore);
</script>

<style lang="scss" scoped>
  .balance-panel {
    padding: 38rpx 30rpx 76rpx;
    color: #fff;
  }
  .balance-label {
    font-size: 25rpx;
  }
  .balance-value {
    margin-top: 16rpx;
    font-size: 58rpx;
    font-weight: 700;
    overflow-wrap: anywhere;
  }
  .balance-sub {
    margin-top: 15rpx;
    font-size: 22rpx;
    opacity: 0.88;
  }
  .balance-sub text {
    margin-left: 14rpx;
  }
  .draw-card,
  .record-section {
    margin: -42rpx 24rpx 24rpx;
    padding: 28rpx;
    border-radius: 14rpx;
    background: #fff;
  }
  .record-section {
    margin-top: 0;
  }
  .section-title {
    color: #222;
    font-size: 28rpx;
    font-weight: 600;
  }
  .amount-input {
    display: flex;
    align-items: center;
    gap: 16rpx;
    margin-top: 20rpx;
    padding-bottom: 18rpx;
    border-bottom: 1rpx solid #ddd;
  }
  .amount-input text {
    color: #333;
    font-size: 42rpx;
  }
  .amount-input input {
    flex: 1;
    min-width: 0;
    height: 70rpx;
    font-size: 44rpx;
  }
  .amount-action-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 14rpx;
  }
  .amount-tip {
    font-size: 22rpx;
    color: #999;
  }
  .all-button {
    color: var(--ui-BG-Main);
    font-size: 23rpx;
  }
  .method-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 28rpx;
  }
  .method-title-side {
    display: flex;
    align-items: center;
    gap: 6rpx;
    font-size: 24rpx;
    color: #666;
  }
  .method-title-side .add-link {
    color: var(--ui-BG-Main);
  }
  .arrow-right {
    font-size: 28rpx;
    color: #bbb;
  }

  /* 选中账户卡片 */
  .selected-account-box {
    display: flex;
    align-items: center;
    gap: 18rpx;
    margin-top: 18rpx;
    padding: 20rpx 24rpx;
    border: 1rpx solid #e2e8f0;
    border-radius: 12rpx;
    background: #f8fafc;
  }
  .account-badge {
    padding: 6rpx 14rpx;
    border-radius: 6rpx;
    font-size: 22rpx;
    font-weight: 500;
    color: #fff;
    background: #1677ff;
  }
  .account-badge.wechat {
    background: #07c160;
  }
  .account-badge.other {
    background: #8c8c8c;
  }
  .account-info {
    flex: 1;
    min-width: 0;
  }
  .account-number {
    font-size: 28rpx;
    font-weight: 600;
    color: #1e293b;
  }
  .account-name {
    margin-top: 4rpx;
    font-size: 22rpx;
    color: #64748b;
  }
  .default-pill {
    padding: 4rpx 10rpx;
    border-radius: 4rpx;
    font-size: 20rpx;
    color: #ff4d4f;
    background: #fff1f0;
    border: 1rpx solid #ffa39e;
  }

  /* 手动/未绑定样式 */
  .custom-account-box {
    margin-top: 14rpx;
  }
  .empty-account-box {
    margin-top: 18rpx;
    padding: 30rpx 24rpx;
    border: 2rpx dashed #cbd5e1;
    border-radius: 12rpx;
    text-align: center;
    background: #f8fafc;
  }
  .empty-account-title {
    display: block;
    font-size: 28rpx;
    font-weight: 600;
    color: var(--ui-BG-Main);
  }
  .empty-account-desc {
    display: block;
    margin-top: 8rpx;
    font-size: 22rpx;
    color: #94a3b8;
  }

  .method-row {
    display: flex;
    gap: 16rpx;
    margin: 18rpx 0 22rpx;
  }
  .method-button {
    flex: 1;
    height: 68rpx;
    line-height: 68rpx;
    border: 1rpx solid #ddd;
    border-radius: 8rpx;
    color: #555;
    font-size: 25rpx;
  }
  .method-button.active {
    color: var(--ui-BG-Main);
    border-color: var(--ui-BG-Main);
    background: rgba(var(--ui-BG-Main-rgb), 0.08);
  }
  .draw-card :deep(.uni-easyinput) {
    margin-top: 16rpx;
  }

  /* 安全风控提示卡片 */
  .security-card {
    margin-top: 24rpx;
    padding: 20rpx 22rpx;
    border-radius: 10rpx;
    background: #f6ffed;
    border: 1rpx solid #b7eb8f;
  }
  .security-card.warning-mode {
    background: #fffbe6;
    border-color: #ffe58f;
  }
  .security-header {
    display: flex;
    align-items: center;
    gap: 10rpx;
  }
  .security-icon {
    font-size: 28rpx;
  }
  .security-title {
    font-size: 24rpx;
    font-weight: 600;
    color: #389e0d;
  }
  .security-card.warning-mode .security-title {
    color: #d46b08;
  }
  .security-desc {
    margin-top: 8rpx;
    font-size: 22rpx;
    line-height: 1.5;
    color: #555;
  }

  /* 验证码输入框及按钮 */
  .sms-verify-row {
    display: flex;
    align-items: center;
    gap: 16rpx;
    margin-top: 16rpx;
  }
  .sms-input-wrap {
    flex: 1;
    min-width: 0;
  }
  .sms-input-wrap :deep(.uni-easyinput) {
    margin-top: 0 !important;
  }
  .sms-send-btn {
    height: 72rpx;
    line-height: 72rpx;
    padding: 0 24rpx;
    border-radius: 8rpx;
    font-size: 24rpx;
    color: var(--ui-BG-Main);
    border: 1rpx solid var(--ui-BG-Main);
    background: #fff;
    white-space: nowrap;
  }

  .form-tip {
    margin-top: 18rpx;
    color: #999;
    font-size: 21rpx;
    line-height: 1.5;
  }
  .submit-button {
    width: 100%;
    height: 78rpx;
    line-height: 78rpx;
    margin-top: 28rpx;
    border-radius: 39rpx;
    color: #fff;
  }

  /* 提现记录样式 */
  .record-head,
  .withdraw-row,
  .detail-item {
    display: flex;
    justify-content: space-between;
    gap: 24rpx;
  }
  .record-head {
    align-items: center;
    margin-bottom: 8rpx;
    color: #999;
    font-size: 22rpx;
  }
  .withdraw-row {
    padding: 22rpx 0;
    border-bottom: 1rpx solid #eee;
  }
  .withdraw-row > view {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 10rpx;
  }
  .withdraw-row strong {
    color: #333;
    font-size: 25rpx;
    overflow-wrap: anywhere;
  }
  .withdraw-row text {
    color: #999;
    font-size: 21rpx;
  }
  .withdraw-side {
    flex-shrink: 0;
    text-align: right;
  }
  .withdraw-side strong {
    color: #fa3534;
    font-size: 28rpx;
  }

  /* 底部弹窗通用 */
  .popup-box {
    max-height: 80vh;
    padding: 32rpx 28rpx 50rpx;
    overflow-y: auto;
    background: #fff;
  }
  .popup-title {
    margin-bottom: 24rpx;
    color: #1e293b;
    font-size: 32rpx;
    font-weight: 600;
    text-align: center;
  }

  /* 账户选择列表 */
  .account-select-list {
    display: flex;
    flex-direction: column;
    gap: 18rpx;
    margin-bottom: 30rpx;
  }
  .account-select-item {
    display: flex;
    align-items: center;
    gap: 20rpx;
    padding: 22rpx 20rpx;
    border: 1rpx solid #e2e8f0;
    border-radius: 12rpx;
    background: #fff;
  }
  .account-select-item.active {
    border-color: var(--ui-BG-Main);
    background: rgba(var(--ui-BG-Main-rgb), 0.04);
  }
  .account-detail {
    flex: 1;
    min-width: 0;
  }
  .acc-row {
    display: flex;
    align-items: center;
    gap: 12rpx;
  }
  .acc-num {
    font-size: 28rpx;
    font-weight: 600;
    color: #1e293b;
  }
  .acc-name {
    margin-top: 6rpx;
    font-size: 22rpx;
    color: #64748b;
  }
  .acc-check {
    width: 44rpx;
    text-align: center;
    font-size: 32rpx;
    font-weight: 700;
    color: var(--ui-BG-Main);
  }
  .add-account-btn {
    width: 100%;
    height: 76rpx;
    line-height: 76rpx;
    border-radius: 38rpx;
    border: 2rpx dashed var(--ui-BG-Main);
    color: var(--ui-BG-Main);
    font-size: 26rpx;
    background: rgba(var(--ui-BG-Main-rgb), 0.05);
  }

  /* 添加账户表单 */
  .form-item-label {
    font-size: 26rpx;
    font-weight: 500;
    color: #333;
  }
  .default-switch-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10rpx 0;
    font-size: 26rpx;
    color: #444;
  }

  /* 详情面板 */
  .detail-panel {
    max-height: 78vh;
    padding: 32rpx 28rpx 54rpx;
    overflow-y: auto;
    background: #fff;
  }
  .detail-title {
    margin-bottom: 20rpx;
    color: #222;
    font-size: 34rpx;
    font-weight: 600;
  }
  .detail-item {
    padding: 21rpx 0;
    border-bottom: 1rpx solid #eee;
    color: #777;
    font-size: 25rpx;
  }
  .detail-item text:last-child {
    max-width: 440rpx;
    color: #222;
    text-align: right;
    overflow-wrap: anywhere;
  }
  .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 20rpx;
    padding: 80rpx 30rpx;
    color: #999;
  }
  .retry-button {
    width: 160rpx;
    height: 60rpx;
    line-height: 60rpx;
    border-radius: 30rpx;
    color: #fff;
    background: var(--ui-BG-Main);
  }
  button[disabled] {
    opacity: 0.55;
  }
</style>
