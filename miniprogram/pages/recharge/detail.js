const app = getApp()
const { get } = require('../../utils/request')
const {
  formatMoney, formatDate, rechargeStatusLabel, payMethodLabel,
} = require('../../utils/format')

// 账单详情（T14 卡片 496 行 / PRD 297 行「账单详情 — 查看单笔充值明细」）。
//
// 详情走 GET /user/recharges/{id} 而不是把列表行的数据传进来：
// 列表是缓存，详情要的是"此刻这一笔的准确状态"，退款/待支付单笔可能已变。
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
      const item = await get(`/user/recharges/${id}`)
      this.setData({
        detail: {
          orderNo: item.orderNo || '—',
          patientName: item.patientName || '—',
          amountText: formatMoney(item.amountFen),
          // 标签是「当前卡内余额」不是「到账后余额」：后端返回的 balanceFen 取自 patient 表实时值，
          // 看历史单据时它已含之后的充值与缴费，只有成功页那一刻才等于"到账后余额"。
          balanceText: formatMoney(item.balanceFen),
          status: item.status,
          statusLabel: rechargeStatusLabel(item.status),
          tone: item.status === 'SUCCESS' ? 'ok'
              : item.status === 'PENDING' ? 'pending' : 'refunded',
          payMethodLabel: payMethodLabel(item.payMethod),
          timeText: item.createdAt ? formatDate(item.createdAt, 'YYYY-MM-DD HH:mm:ss') : '—',
          tradeNo: item.tradeNo || '—',
        },
      })
    } catch (err) {
      // 5001（不属于本人）等消息由 request.js toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onBack() {
    wx.navigateBack()
  },
})
