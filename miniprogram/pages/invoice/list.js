const app = getApp()
const { get } = require('../../utils/request')
const { formatMoney, formatDate, invoiceStatusLabel } = require('../../utils/format')

// 已开具电子发票（T19 卡片 583 行 / PRD §3.3.8 第 151 行「已开具电子发票 — 已开票记录列表」）。
//
// 列表不带 items（开票项目明细）：与 T15 缴费记录、T17 报告列表同一条纪律——
// 列表是清单，正文留给票据详情。
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
    this.loadList()
  },

  async loadList() {
    this.setData({ loading: true })
    try {
      const list = await get('/user/invoices')
      this.setData({
        rows: (list || []).map((item) => ({
          invoiceId: item.invoiceId,
          invoiceNo: item.invoiceNo || '—',
          patientName: item.patientName || '—',
          amountText: formatMoney(item.amountFen),
          statusLabel: invoiceStatusLabel(item.status),
          // 色值语义沿用任务卡 §2.1：已开具=绿（96 行「成功/正常」），开票中=琥珀（93 行）
          tone: item.status === 'ISSUED' ? 'ok' : 'pending',
          timeText: item.issuedAt ? formatDate(item.issuedAt, 'YYYY-MM-DD HH:mm') : '—',
          paymentOrderNo: item.paymentOrderNo || '—',
        })),
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onOpen(e) {
    wx.navigateTo({ url: '/pages/invoice/detail?id=' + e.currentTarget.dataset.id })
  },

  onGoPending() {
    wx.navigateTo({ url: '/pages/invoice/pending' })
  },
})
