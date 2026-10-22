<template>
  <s-layout title="推广中心" navbar="inner">
    <!-- 头部推广员身份与收益大卡 -->
    <view class="promoter-header ui-BG-Main-Gradient">
      <view class="user-row">
        <view class="level-badge">{{ levelText(state.summary.promoterLevel) }}</view>
        <view v-if="state.summary.parentNickname" class="parent-info">
          推荐人: {{ state.summary.parentNickname }}
        </view>
        <view v-else class="parent-info" @tap="showBindDialog = true">
          未绑定推荐人 <text class="bind-link">[去绑定]</text>
        </view>
      </view>

      <view class="commission-panel">
        <view class="commission-label">累计推广收益</view>
        <view class="commission-val">¥{{ state.summary.totalCommissionYuan || '0.00' }}</view>
      </view>

      <view class="code-box">
        <text class="code-label">专属邀请码:</text>
        <text class="code-val">{{ state.summary.inviteCode || '-' }}</text>
        <button class="ss-reset-button copy-btn" @tap="copyInviteCode">复制</button>
      </view>
    </view>

    <!-- 团队数据看板 -->
    <view class="stats-card">
      <view class="stat-item" @tap="goTeam(0)">
        <text class="stat-num">{{ state.summary.totalTeamCount || 0 }}</text>
        <text class="stat-title">团队总人数</text>
      </view>
      <view class="stat-item" @tap="goTeam(1)">
        <text class="stat-num">{{ state.summary.directTeamCount || 0 }}</text>
        <text class="stat-title">直推人数(一级)</text>
      </view>
      <view class="stat-item" @tap="goTeam(2)">
        <text class="stat-num">{{ state.summary.indirectTeamCount || 0 }}</text>
        <text class="stat-title">间推人数(二级)</text>
      </view>
      <view class="stat-item" @tap="goOrders">
        <text class="stat-num">{{ state.summary.totalOrdersCount || 0 }}</text>
        <text class="stat-title">团队总出单</text>
      </view>
    </view>

    <!-- 功能金刚区 -->
    <view class="menu-grid">
      <view class="menu-item" @tap="goInvite">
        <view class="menu-icon icon-invite">🎁</view>
        <text class="menu-text">邀请海报</text>
        <text class="menu-sub">推广锁粉扩建团队</text>
      </view>
      <view class="menu-item" @tap="goTeam(0)">
        <view class="menu-icon icon-team">👥</view>
        <text class="menu-text">我的团队</text>
        <text class="menu-sub">查看一二级粉丝</text>
      </view>
      <view class="menu-item" @tap="goOrders">
        <view class="menu-icon icon-order">📋</view>
        <text class="menu-text">推广订单</text>
        <text class="menu-sub">下级出单提成明细</text>
      </view>
      <view class="menu-item" @tap="goWallet">
        <view class="menu-icon icon-wallet">💰</view>
        <text class="menu-text">返利钱包</text>
        <text class="menu-sub">提现与Token兑换</text>
      </view>
    </view>

    <!-- 推广说明卡片 -->
    <view class="rules-card">
      <view class="rules-head">三级分销与分润规则</view>
      <view class="rules-item">1. 分享您的专属邀请码或商品转链，好友首次进入系统自动锁粉绑定成为您的团队成员。</view>
      <view class="rules-item">2. 团队成员购物出单，扣除平台技术服务费后，净佣金池按比例瓜分（自购享返利，一级享直接提成，二级享间接提成）。</view>
      <view class="rules-item">3. 提成结算到期后自动汇入可用余额，支持随时申请提现或兑换 AI Token。</view>
    </view>

    <!-- 绑定上级弹窗 -->
    <su-popup :show="showBindDialog" type="center" round="12" @close="showBindDialog = false">
      <view class="bind-modal">
        <view class="modal-title">绑定推荐人</view>
        <view class="modal-desc">请输入邀请人的 6-8 位专属邀请码</view>
        <input
          v-model="inputInviteCode"
          class="bind-input"
          placeholder="请输入邀请码"
          maxlength="16"
        />
        <view class="modal-actions">
          <button class="ss-reset-button btn-cancel" @tap="showBindDialog = false">取消</button>
          <button class="ss-reset-button btn-confirm" @tap="confirmBind">确认绑定</button>
        </view>
      </view>
    </su-popup>
  </s-layout>
</template>

<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import sheep from '@/sheep';
  import CpsPromoterApi from '@/sheep/api/cps/promoter';

  const state = reactive({
    summary: {
      inviteCode: '',
      promoterLevel: 0,
      parentMemberId: null,
      parentNickname: '',
      parentAvatar: '',
      totalCommissionCent: 0,
      totalCommissionYuan: '0.00',
      totalOrdersCount: 0,
      directTeamCount: 0,
      indirectTeamCount: 0,
      totalTeamCount: 0,
    },
  });

  const showBindDialog = ref(false);
  const inputInviteCode = ref('');

  function levelText(level) {
    const map = {
      0: '普通会员',
      1: '初级团长',
      2: '中级合伙人',
      3: '高级合伙人',
    };
    return map[level] || '普通会员';
  }

  async function loadSummary() {
    try {
      const res = await CpsPromoterApi.getSummary();
      if (res && res.code === 0 && res.data) {
        state.summary = res.data;
      }
    } catch (e) {
      console.error('加载推广中心失败', e);
    }
  }

  function copyInviteCode() {
    if (!state.summary.inviteCode) return;
    uni.setClipboardData({
      data: state.summary.inviteCode,
      success: () => {
        sheep.$helper.toast('邀请码已复制');
      },
    });
  }

  function goInvite() {
    sheep.$router.go('/pages/cps/promoter/invite');
  }

  function goTeam(level) {
    sheep.$router.go('/pages/cps/promoter/team', { level });
  }

  function goOrders() {
    sheep.$router.go('/pages/cps/promoter/orders');
  }

  function goWallet() {
    sheep.$router.go('/pages/cps/wallet');
  }

  async function confirmBind() {
    if (!inputInviteCode.value || !inputInviteCode.value.trim()) {
      sheep.$helper.toast('请输入邀请码');
      return;
    }
    try {
      const res = await CpsPromoterApi.bind(inputInviteCode.value.trim());
      if (res && res.code === 0) {
        sheep.$helper.toast('绑定成功');
        showBindDialog.value = false;
        inputInviteCode.value = '';
        loadSummary();
      } else {
        sheep.$helper.toast(res?.msg || '绑定失败，请检查邀请码');
      }
    } catch (e) {
      sheep.$helper.toast('绑定失败，请重试');
    }
  }

  onMounted(() => {
    loadSummary();
  });
</script>

<style lang="scss" scoped>
  .promoter-header {
    margin: 20rpx;
    padding: 30rpx;
    border-radius: 20rpx;
    color: #fff;

    .user-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 24rpx;

      .level-badge {
        padding: 6rpx 18rpx;
        background: rgba(255, 255, 255, 0.25);
        border-radius: 20rpx;
        font-size: 24rpx;
        font-weight: 500;
      }

      .parent-info {
        font-size: 24rpx;
        opacity: 0.9;

        .bind-link {
          color: #ffe082;
          margin-left: 8rpx;
        }
      }
    }

    .commission-panel {
      margin-bottom: 24rpx;

      .commission-label {
        font-size: 26rpx;
        opacity: 0.85;
      }

      .commission-val {
        font-size: 56rpx;
        font-weight: 700;
        margin-top: 8rpx;
      }
    }

    .code-box {
      display: flex;
      align-items: center;
      background: rgba(0, 0, 0, 0.15);
      padding: 12rpx 20rpx;
      border-radius: 12rpx;
      font-size: 26rpx;

      .code-label {
        opacity: 0.85;
      }

      .code-val {
        margin-left: 12rpx;
        font-weight: 600;
        letter-spacing: 2rpx;
      }

      .copy-btn {
        margin-left: auto;
        padding: 4rpx 16rpx;
        background: #fff;
        color: #ff6000;
        border-radius: 20rpx;
        font-size: 22rpx;
        font-weight: 500;
      }
    }
  }

  .stats-card {
    display: flex;
    margin: 20rpx;
    padding: 24rpx 0;
    background: #fff;
    border-radius: 16rpx;

    .stat-item {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      border-right: 1rpx solid #f0f0f0;

      &:last-child {
        border-right: none;
      }

      .stat-num {
        font-size: 32rpx;
        font-weight: 700;
        color: #333;
      }

      .stat-title {
        font-size: 22rpx;
        color: #999;
        margin-top: 8rpx;
      }
    }
  }

  .menu-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 20rpx;
    margin: 20rpx;

    .menu-item {
      background: #fff;
      padding: 28rpx;
      border-radius: 16rpx;
      display: flex;
      flex-direction: column;

      .menu-icon {
        font-size: 40rpx;
        margin-bottom: 12rpx;
      }

      .menu-text {
        font-size: 28rpx;
        font-weight: 600;
        color: #333;
      }

      .menu-sub {
        font-size: 22rpx;
        color: #999;
        margin-top: 6rpx;
      }
    }
  }

  .rules-card {
    margin: 20rpx;
    padding: 24rpx;
    background: #fff;
    border-radius: 16rpx;

    .rules-head {
      font-size: 28rpx;
      font-weight: 600;
      color: #333;
      margin-bottom: 16rpx;
    }

    .rules-item {
      font-size: 24rpx;
      color: #666;
      line-height: 1.6;
      margin-bottom: 12rpx;
    }
  }

  .bind-modal {
    width: 580rpx;
    padding: 36rpx;
    background: #fff;
    border-radius: 20rpx;

    .modal-title {
      font-size: 32rpx;
      font-weight: 600;
      text-align: center;
      color: #333;
    }

    .modal-desc {
      font-size: 24rpx;
      color: #999;
      text-align: center;
      margin-top: 12rpx;
      margin-bottom: 30rpx;
    }

    .bind-input {
      width: 100%;
      height: 80rpx;
      border: 2rpx solid #eee;
      border-radius: 12rpx;
      padding: 0 20rpx;
      font-size: 28rpx;
      box-sizing: border-box;
      margin-bottom: 36rpx;
    }

    .modal-actions {
      display: flex;
      gap: 20rpx;

      .btn-cancel {
        flex: 1;
        height: 72rpx;
        line-height: 72rpx;
        text-align: center;
        background: #f5f5f5;
        color: #666;
        border-radius: 36rpx;
        font-size: 28rpx;
      }

      .btn-confirm {
        flex: 1;
        height: 72rpx;
        line-height: 72rpx;
        text-align: center;
        background: #ff6000;
        color: #fff;
        border-radius: 36rpx;
        font-size: 28rpx;
      }
    }
  }
</style>
