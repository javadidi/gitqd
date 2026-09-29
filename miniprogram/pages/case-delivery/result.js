const app = getApp()
const { get } = require('../../utils/request')
const { formatDate, deliveryStatusLabel } = require('../../utils/format')

// 病案配送提交成功（PRD 245 行「支付成功 — 申请成功提示」，本页不收钱所以叫"申请已提交"）。
//
// 只带 id 过来、再回读一次详情：这样"提交成功"这句话是库里那一行给的证据，
// 不是提交响应里的内存对象给的（T22 成功页同一条做法）。
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
    this.setData({ id: options.id ? Number(options.id) : null })
    this.loadDetail()
  },

  async loadDetail() {
    if (!this.data.id) {
      wx.showToast({ title: '缺少申请编号', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    try {
      const item = await get(`/user/case-deliveries/${this.data.id}`)
      if (!item) {
        return
      }
      this.setData({
        detail: {
          inpatientName: item.inpatientName || '—',
          inpatientNo: item.inpatientNo || '—',
          recipientName: item.recipientName || '—',
          address: item.address || '—',
          statusLabel: deliveryStatusLabel(item.status),
          timeText: formatDate(item.createdAt),
        },
      })
    } catch (err) {
      // 5001 由 request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onGoList() {
    wx.redirectTo({ url: '/pages/case-delivery/list' })
  },

  onBackHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
