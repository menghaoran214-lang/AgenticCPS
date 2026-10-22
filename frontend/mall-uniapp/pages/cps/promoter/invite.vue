<template>
  <s-layout title="邀请海报" navbar="inner">
    <view class="invite-container">
      <!-- 邀请海报卡片 -->
      <view class="poster-card ui-BG-Main-Gradient">
        <view class="poster-header">
          <view class="poster-tag">专属分销邀请</view>
          <view class="poster-title">自购省钱 · 分享赚钱</view>
          <view class="poster-sub">加入我的省钱推广团队，共享多级佣金提成</view>
        </view>

        <view class="qr-box">
          <view class="qr-mock">
            <text class="qr-icon">📱</text>
            <text class="qr-tip">扫码立即加入</text>
          </view>
        </view>

        <view class="invite-code-box">
          <text class="code-title">我的专属邀请码</text>
          <text class="code-value">{{ state.summary.inviteCode || '------' }}</text>
          <button class="ss-reset-button copy-code-btn" @tap="copyCode">复制邀请码</button>
        </view>
      </view>

      <!-- 操作按钮栏 -->
      <view class="action-bar">
        <button class="ss-reset-button btn-link" @tap="copyLink">复制专属推广链接</button>
      </view>

      <!-- 锁粉推广指引 -->
      <view class="guide-card">
        <view class="guide-title">三步快速锁粉拓客</view>
        <view class="step-item">
          <view class="step-num">1</view>
          <view class="step-text">
            <text class="step-h">分享邀请码或商品转链</text>
            <text class="step-p">将您的邀请码或CPS优惠转链发送给微信好友或社群。</text>
          </view>
        </view>
        <view class="step-item">
          <view class="step-num">2</view>
          <view class="step-text">
            <text class="step-h">首单自动锁粉终身绑定</text>
            <text class="step-p">新客通过您的链接进入或完成首单，系统自动锁定上下级推广关系。</text>
          </view>
        </view>
        <view class="step-item">
          <view class="step-num">3</view>
          <view class="step-text">
            <text class="step-h">持续获得多级出单提成</text>
            <text class="step-p">下级及二级团队每一次购物出单，您都将按规则获得佣金提成。</text>
          </view>
        </view>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { reactive, onMounted } from 'vue';
  import sheep from '@/sheep';
  import CpsPromoterApi from '@/sheep/api/cps/promoter';

  const state = reactive({
    summary: {
      inviteCode: '',
      promoterLevel: 0,
    },
  });

  async function loadSummary() {
    try {
      const res = await CpsPromoterApi.getSummary();
      if (res && res.code === 0 && res.data) {
        state.summary = res.data;
      }
    } catch (e) {
      console.error(e);
    }
  }

  function copyCode() {
    if (!state.summary.inviteCode) return;
    uni.setClipboardData({
      data: state.summary.inviteCode,
      success: () => {
        sheep.$helper.toast('邀请码已复制');
      },
    });
  }

  function copyLink() {
    if (!state.summary.inviteCode) return;
    const link = `${window.location.origin}/#/pages/cps/goods?inviteCode=${state.summary.inviteCode}`;
    uni.setClipboardData({
      data: link,
      success: () => {
        sheep.$helper.toast('推广链接已复制');
      },
    });
  }

  onMounted(() => {
    loadSummary();
  });
</script>

<style lang="scss" scoped>
  .invite-container {
    padding: 30rpx 24rpx;
  }

  .poster-card {
    border-radius: 24rpx;
    padding: 40rpx 30rpx;
    color: #fff;
    display: flex;
    flex-direction: column;
    align-items: center;
    box-shadow: 0 10rpx 30rpx rgba(255, 96, 0, 0.2);

    .poster-header {
      text-align: center;

      .poster-tag {
        display: inline-block;
        padding: 6rpx 20rpx;
        background: rgba(255, 255, 255, 0.25);
        border-radius: 20rpx;
        font-size: 22rpx;
        margin-bottom: 16rpx;
      }

      .poster-title {
        font-size: 40rpx;
        font-weight: 700;
        letter-spacing: 2rpx;
      }

      .poster-sub {
        font-size: 24rpx;
        opacity: 0.85;
        margin-top: 10rpx;
      }
    }

    .qr-box {
      margin: 40rpx 0;
      width: 240rpx;
      height: 240rpx;
      background: #fff;
      border-radius: 20rpx;
      display: flex;
      align-items: center;
      justify-content: center;

      .qr-mock {
        display: flex;
        flex-direction: column;
        align-items: center;

        .qr-icon {
          font-size: 70rpx;
        }

        .qr-tip {
          font-size: 20rpx;
          color: #999;
          margin-top: 8rpx;
        }
      }
    }

    .invite-code-box {
      width: 100%;
      background: rgba(255, 255, 255, 0.2);
      border-radius: 16rpx;
      padding: 24rpx;
      display: flex;
      flex-direction: column;
      align-items: center;

      .code-title {
        font-size: 24rpx;
        opacity: 0.9;
      }

      .code-value {
        font-size: 48rpx;
        font-weight: 700;
        letter-spacing: 6rpx;
        margin: 12rpx 0;
      }

      .copy-code-btn {
        padding: 8rpx 32rpx;
        background: #fff;
        color: #ff6000;
        border-radius: 30rpx;
        font-size: 24rpx;
        font-weight: 600;
      }
    }
  }

  .action-bar {
    margin: 30rpx 0;

    .btn-link {
      width: 100%;
      height: 84rpx;
      line-height: 84rpx;
      text-align: center;
      background: #ff6000;
      color: #fff;
      border-radius: 42rpx;
      font-size: 30rpx;
      font-weight: 600;
    }
  }

  .guide-card {
    background: #fff;
    border-radius: 20rpx;
    padding: 30rpx;

    .guide-title {
      font-size: 30rpx;
      font-weight: 600;
      color: #333;
      margin-bottom: 24rpx;
    }

    .step-item {
      display: flex;
      margin-bottom: 24rpx;

      &:last-child {
        margin-bottom: 0;
      }

      .step-num {
        width: 44rpx;
        height: 44rpx;
        line-height: 44rpx;
        text-align: center;
        background: #fff0e6;
        color: #ff6000;
        font-weight: 700;
        font-size: 24rpx;
        border-radius: 22rpx;
        margin-right: 18rpx;
        flex-shrink: 0;
      }

      .step-text {
        flex: 1;
        display: flex;
        flex-direction: column;

        .step-h {
          font-size: 26rpx;
          font-weight: 600;
          color: #333;
        }

        .step-p {
          font-size: 22rpx;
          color: #888;
          line-height: 1.5;
          margin-top: 6rpx;
        }
      }
    }
  }
</style>
