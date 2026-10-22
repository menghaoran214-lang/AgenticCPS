<template>
  <s-layout title="我的团队" navbar="inner">
    <su-tabs :list="tabList" :current="currentTab" @change="onTabChange" />

    <view v-if="state.list.length > 0" class="member-list">
      <view v-for="item in state.list" :key="item.memberId" class="member-card">
        <view class="avatar-wrap">
          <image
            class="avatar"
            :src="item.avatar || '/static/images/default-avatar.png'"
            mode="aspectFill"
          />
        </view>
        <view class="info-wrap">
          <view class="name-row">
            <text class="nickname">{{ item.nickname || '会员' + item.memberId }}</text>
            <text class="level-tag" :class="'level-' + item.level">
              {{ item.level === 1 ? '直推下级' : '间推成员' }}
            </text>
          </view>
          <view class="meta-row">
            <text v-if="item.mobile" class="mobile">{{ item.mobile }}</text>
            <text class="bind-time">加入: {{ formatTime(item.bindTime) }}</text>
          </view>
        </view>
        <view class="stats-wrap">
          <view class="stats-item">
            <text class="stats-val">{{ item.teamOrdersCount || 0 }} 单</text>
            <text class="stats-lbl">贡献出单</text>
          </view>
          <view class="stats-item">
            <text class="stats-val income">¥{{ item.contributedCommissionYuan || '0.00' }}</text>
            <text class="stats-lbl">提成贡献</text>
          </view>
        </view>
      </view>

      <uni-load-more :status="state.loadStatus" />
    </view>

    <view v-else-if="!state.loading" class="empty-wrap">
      <view class="empty-icon">👥</view>
      <text class="empty-text">暂无下级团队成员</text>
      <button class="ss-reset-button invite-btn" @tap="goInvite">立即邀请好友</button>
    </view>
  </s-layout>
</template>

<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { onLoad, onReachBottom } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import CpsPromoterApi from '@/sheep/api/cps/promoter';

  const tabList = [
    { name: '全部成员', value: null },
    { name: '直推(一级)', value: 1 },
    { name: '间推(二级)', value: 2 },
  ];

  const currentTab = ref(0);

  const state = reactive({
    list: [],
    total: 0,
    pageNo: 1,
    pageSize: 10,
    loadStatus: 'more',
    loading: false,
  });

  onLoad((options) => {
    if (options && options.level) {
      const idx = tabList.findIndex((t) => String(t.value) === String(options.level));
      if (idx !== -1) currentTab.value = idx;
    }
  });

  function onTabChange(e) {
    currentTab.value = e.index;
    state.pageNo = 1;
    state.list = [];
    loadData();
  }

  async function loadData() {
    if (state.loading) return;
    state.loading = true;
    state.loadStatus = 'loading';

    const level = tabList[currentTab.value].value;
    try {
      const res = await CpsPromoterApi.getTeamPage({
        level: level,
        pageNo: state.pageNo,
        pageSize: state.pageSize,
      });
      if (res && res.code === 0 && res.data) {
        const list = res.data.list || [];
        state.list = state.pageNo === 1 ? list : state.list.concat(list);
        state.total = res.data.total || 0;
        state.loadStatus = state.list.length >= state.total ? 'noMore' : 'more';
      } else {
        state.loadStatus = 'more';
      }
    } catch (e) {
      state.loadStatus = 'more';
    } finally {
      state.loading = false;
    }
  }

  function formatTime(timeStr) {
    if (!timeStr) return '-';
    return String(timeStr).replace('T', ' ').substring(0, 16);
  }

  function goInvite() {
    sheep.$router.go('/pages/cps/promoter/invite');
  }

  onReachBottom(() => {
    if (state.loadStatus === 'more') {
      state.pageNo++;
      loadData();
    }
  });

  onMounted(() => {
    loadData();
  });
</script>

<style lang="scss" scoped>
  .member-list {
    padding: 20rpx;
  }

  .member-card {
    display: flex;
    align-items: center;
    background: #fff;
    padding: 24rpx;
    border-radius: 16rpx;
    margin-bottom: 20rpx;

    .avatar-wrap {
      .avatar {
        width: 88rpx;
        height: 88rpx;
        border-radius: 44rpx;
        background: #f0f0f0;
      }
    }

    .info-wrap {
      flex: 1;
      margin-left: 20rpx;

      .name-row {
        display: flex;
        align-items: center;

        .nickname {
          font-size: 28rpx;
          font-weight: 600;
          color: #333;
        }

        .level-tag {
          font-size: 20rpx;
          padding: 2rpx 10rpx;
          border-radius: 6rpx;
          margin-left: 12rpx;

          &.level-1 {
            background: #fff0e6;
            color: #ff6000;
          }

          &.level-2 {
            background: #e6f7ff;
            color: #1890ff;
          }
        }
      }

      .meta-row {
        margin-top: 8rpx;
        font-size: 22rpx;
        color: #999;

        .mobile {
          margin-right: 12rpx;
        }
      }
    }

    .stats-wrap {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 6rpx;

      .stats-item {
        display: flex;
        align-items: center;
        gap: 8rpx;
        font-size: 22rpx;

        .stats-val {
          font-weight: 600;
          color: #333;

          &.income {
            color: #ff6000;
          }
        }

        .stats-lbl {
          color: #999;
        }
      }
    }
  }

  .empty-wrap {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding-top: 160rpx;

    .empty-icon {
      font-size: 80rpx;
    }

    .empty-text {
      font-size: 28rpx;
      color: #999;
      margin-top: 20rpx;
      margin-bottom: 40rpx;
    }

    .invite-btn {
      width: 280rpx;
      height: 72rpx;
      line-height: 72rpx;
      text-align: center;
      background: #ff6000;
      color: #fff;
      border-radius: 36rpx;
      font-size: 28rpx;
      font-weight: 500;
    }
  }
</style>
