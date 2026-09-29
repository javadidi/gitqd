const app = getApp()
const { get } = require('../../utils/request')
const {
  formatMoney, formatDate, rechargeStatusLabel, payMethodLabel,
} = require('../../utils/format')

// 充值记录列表（T14 卡片 496 行 / PRD 296 行「充值记录列表 — 展示门诊充值历史」）。
//
// onShow 重拉：从充值成功页 redirectTo 过来时必须看到刚充的那笔，
// 否则患者会以为"充了钱但没有记录"。
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
      const list = await get('/user/recharges')
      const rows = (list || []).map((item) => ({
        id: item.id,
        patientName: item.patientName || '—',
        amountText: formatMoney(item.amountFen),
        status: item.status,
        statusLabel: rechargeStatusLabel(item.status),
        payMethodLabel: payMethodLabel(item.payMethod),
        timeText: item.createdAt ? formatDate(item.createdAt, 'YYYY-MM-DD HH:mm') : '—',
        // 列表行做三色分：到账=绿、待支付=蓝、退款=红（任务卡 92-96 行状态色语义）
        tone: item.status === 'SUCCESS' ? 'ok'
            : item.status === 'PENDING' ? 'pending' : 'refunded',
      }))
      this.setData({ rows })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onOpen(e) {
    wx.navigateTo({ url: `/pages/recharge/detail?id=${e.currentTarget.dataset.id}` })
  },

  onGoRecharge() {
    wx.redirectTo({ url: '/pages/recharge/recharge' })
  },
})
