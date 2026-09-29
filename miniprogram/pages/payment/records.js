const app = getApp()
const { get } = require('../../utils/request')
const {
  formatMoney, formatDate, paymentStatusLabel, payMethodLabel,
} = require('../../utils/format')

// 缴费记录列表（T15 卡片 514 行 / PRD 288 行「缴费记录列表 — 展示门诊缴费历史」）。
//
// 列表里有两类来源：T15 的余额缴费（payMethod=BALANCE）与 T12 的挂号费微信流水
// ——它们本来就是同一张 payment_record，所以"缴费记录"必然一起出，不是混排错误。
// 卡片 496/514 行的两处「记录」都只要求「展示历史」，没有要求按来源分栏。
Page({
  data: {
    loading: false,
    rows: [],
  },

  onShow() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadRecords()
  },

  async loadRecords() {
    this.setData({ loading: true })
    try {
      const list = await get('/user/payments')
      this.setData({
        rows: (list || []).map((item) => ({
          id: item.id,
          patientName: item.patientName || '—',
          amountText: formatMoney(item.amountFen),
          status: item.status,
          statusLabel: paymentStatusLabel(item.status),
          payMethodLabel: payMethodLabel(item.payMethod),
          timeText: item.createdAt ? formatDate(item.createdAt, 'YYYY-MM-DD HH:mm') : '—',
          // 三色分沿用任务卡 92–96 行的状态色语义：已缴=绿、待缴=蓝、已退=红
          tone: item.status === 'SUCCESS' ? 'ok'
              : item.status === 'PENDING' ? 'pending' : 'refunded',
        })),
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onOpen(e) {
    wx.navigateTo({ url: `/pages/payment/detail?id=${e.currentTarget.dataset.id}` })
  },

  onGoPay() {
    wx.navigateTo({ url: '/pages/payment/confirm' })
  },
})
