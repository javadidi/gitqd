const app = getApp()
const { get } = require('../../utils/request')
const { formatMoney, formatDate, invoiceStatusLabel } = require('../../utils/format')

// 开票成功（PRD §3.3.8 第 150 行「开票成功 — 开票成功提示页」，§6.1 第 516 行也列了这一页）。
//
// 与 T15 的成功页同一做法：不接上一页传下来的数据，而是拿 invoiceId 重新 GET 一次。
// 申请接口确实把整张票回出来了，但"成功页停一会儿再看"时应当显示与库里一致的当前值。
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
      const item = await get('/user/invoices/' + id)
      this.setData({
        detail: {
          invoiceNo: item.invoiceNo || '—',
          invoiceCode: item.invoiceCode || '—',
          amountText: formatMoney(item.amountFen),
          patientName: item.patientName || '—',
          paymentOrderNo: item.paymentOrderNo || '—',
          statusLabel: invoiceStatusLabel(item.status),
          timeText: item.issuedAt ? formatDate(item.issuedAt, 'YYYY-MM-DD HH:mm:ss') : '—',
        },
      })
    } catch (err) {
      // 5001 等消息由 request.js toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onGoDetail() {
    wx.redirectTo({ url: '/pages/invoice/detail?id=' + this.data.id })
  },

  onGoList() {
    wx.redirectTo({ url: '/pages/invoice/list' })
  },

  onBackHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
