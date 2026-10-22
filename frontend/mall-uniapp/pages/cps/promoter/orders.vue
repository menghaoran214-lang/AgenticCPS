<template>
  <s-layout title="推广订单" navbar="inner">
    <su-tabs :list="tabList" :current="currentTab" @change="onTabChange" />

    <view v-if="state.list.length > 0" class="order-list">
      <view v-for="item in state.list" :key="item.id" class="order-card">
        <view class="card-head">
          <view class="platform-tag">{{ platformName(item.platformCode) }}</view>
          <view v-if="item.sourceMemberNickname" class="buyer-nick">
            出单人: {{ item.sourceMemberNickname }}
          </view>
          <view class="status-tag" :class="item.rebateStatus">
            {{ statusText(item.rebateStatus) }}
          </view>
        </view>

        <view class="card-body">
          <view class="goods-title">{{ item.itemTitle || '平台推广订单' }}</view>
          <view class="order-meta">
            <text>订单金额: ¥{{ item.orderAmount }}</text>
            <text class="commission-txt">联盟佣金: ¥{{ item.commissionAmount }}</text>
          </view>
        </view>

        <view class="card-foot">
          <view class="split-info">
            <text class="role-badge" :class="'role-' + item.splitRole">
              {{ roleText(item.splitRole) }}
            </text>
            <text class="rate-txt">提成 {{ item.rebateRate }}%</text>
          </view>
          <view class="amount-wrap">
            <text class="amount-lbl">分润收益:</text>
            <text class="amount-val">+¥{{ item.rebateAmount }}</text>
          </view>
        </view>
      </view>

      <uni-load-more :status="state.loadStatus" />
    </view>

    <view v-else-if="!state.loading" class="empty-wrap">
      <view class="empty-icon">📋</view>
      <text class="empty-text">暂无推广提成出单记录</text>
    </view>
  </s-layout>
</template>

<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { onReachBottom } from '@dcloudio/uni-app';
  import CpsPromoterApi from '@/sheep/api/cps/promoter';

  const tabList = [
    { name: '全部', value: null },
    { name: '一级提成', value: 'parent_l1' },
    { name: '二级提成', value: 'grandparent_l2' },
    { name: '自购返利', value: 'self' },
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

    const role = tabList[currentTab.value].value;
    try {
      const res = await CpsPromoterApi.getTeamOrdersPage({
        splitRole: role,
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

  function platformName(code) {
    const map = {
      taobao: '淘宝',
      jd: '京东',
      pdd: '拼多多',
      vip: '唯品会',
      meituan: '美团',
      douyin: '抖音',
    };
    return map[code] || code || 'CPS';
  }

  function statusText(st) {
    const map = {
      pending: '待结算',
      received: '已到账',
      refunded: '已退款扣回',
    };
    return map[st] || st || '待处理';
  }

  function roleText(role) {
    const map = {
      self: '自购返利',
      parent_l1: '一级直推提成',
      grandparent_l2: '二级间推提成',
      partner: '合伙人级差',
    };
    return map[role] || role || '推广分账';
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
  .order-list {
    padding: 20rpx;
  }

  .order-card {
    background: #fff;
    border-radius: 16rpx;
    padding: 24rpx;
    margin-bottom: 20rpx;

    .card-head {
      display: flex;
      align-items: center;
      padding-bottom: 16rpx;
      border-bottom: 1rpx solid #f6f6f6;

      .platform-tag {
        font-size: 22rpx;
        color: #ff6000;
        background: #fff0e6;
        padding: 4rpx 12rpx;
        border-radius: 6rpx;
        font-weight: 500;
      }

      .buyer-nick {
        font-size: 24rpx;
        color: #666;
        margin-left: 16rpx;
      }

      .status-tag {
        margin-left: auto;
        font-size: 22rpx;

        &.pending {
          color: #fa8c16;
        }

        &.received {
          color: #52c41a;
        }

        &.refunded {
          color: #ff4d4f;
        }
      }
    }

    .card-body {
      padding: 16rpx 0;

      .goods-title {
        font-size: 28rpx;
        font-weight: 600;
        color: #333;
        line-height: 1.4;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }

      .order-meta {
        display: flex;
        gap: 24rpx;
        font-size: 22rpx;
        color: #999;
        margin-top: 12rpx;

        .commission-txt {
          color: #666;
        }
      }
    }

    .card-foot {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-top: 16rpx;
      border-top: 1rpx solid #f6f6f6;

      .split-info {
        display: flex;
        align-items: center;
        gap: 12rpx;

        .role-badge {
          font-size: 20rpx;
          padding: 2rpx 10rpx;
          border-radius: 6rpx;

          &.role-self {
            background: #f6ffed;
            color: #52c41a;
          }

          &.role-parent_l1 {
            background: #fff7e6;
            color: #fa8c16;
          }

          &.role-grandparent_l2 {
            background: #e6f7ff;
            color: #1890ff;
          }
        }

        .rate-txt {
          font-size: 22rpx;
          color: #999;
        }
      }

      .amount-wrap {
        display: flex;
        align-items: baseline;
        gap: 6rpx;

        .amount-lbl {
          font-size: 22rpx;
          color: #999;
        }

        .amount-val {
          font-size: 32rpx;
          font-weight: 700;
          color: #ff6000;
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
    }
  }
</style>
