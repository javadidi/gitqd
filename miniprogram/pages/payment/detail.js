const app = getApp()
const { get } = require('../../utils/request')
const {
  formatMoney, formatDate, paymentStatusLabel, payMethodLabel,
} = require('../../utils/format')

// 缴费详情（T15 PRD 289 行「缴费详情 — 查看单笔缴费明细」，入口在个人中心 §6.1 527 行）。
//
// 与成功页共用 GET /user/payments/{id}，但两处对同一个 balanceFen 用了不同标签：
// 这里写「当前卡内余额」，成功页写「扣费后余额」。后端这一个字段始终是就诊人此刻的余额，
// 只有"刚扣完"那一刻两种说法才等价——看历史单据时写「扣费后余额」就是一句假话（T14 定下的纪律）。
Page({
  data: {
    loading: false,
    id: null,
    detail: null,
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.setData({ id: options.id })
    this.loadDetail(options.id)
  },

  async loadDetail(id) {
    this.setData({ loading: true })
    try {
      const item = await get(`/user/payments/${id}`)
      this.setData({
        detail: {
          orderNo: item.orderNo || '—',
          patientName: item.patientName || '—',
          amountText: formatMoney(item.amountFen),
          balanceText: formatMoney(item.balanceFen),
          status: item.status,
          statusLabel: paymentStatusLabel(item.status),
          tone: item.status === 'SUCCESS' ? 'ok'
              : item.status === 'PENDING' ? 'pending' : 'refunded',
          payMethodText: payMethodLabel(item.payMethod),
          timeText: item.createdAt ? formatDate(item.createdAt, 'YYYY-MM-DD HH:mm:ss') : '—',
          // 余额支付没有第三方交易号（V1:164 那一列叫「第三方交易号」，而余额不出本院系统），
          // 所以这里空着是正确状态，不是缺数据
          tradeNo: item.tradeNo || '—',
          items: (item.items || []).map((row) => ({
            name: row.name || '—',
            amountText: formatMoney(row.amountFen),
          })),
        },
      })
    } catch (err) {
      // 5001（不是你的单）等消息由 request.js toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onBack() {
    wx.navigateBack()
  },
})
