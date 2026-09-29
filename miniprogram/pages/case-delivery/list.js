const app = getApp()
const { get } = require('../../utils/request')
const { formatDate, deliveryStatusLabel } = require('../../utils/format')

// 病案邮寄申请记录（PRD 314-315 行「病案申请邮寄记录 — 申请记录列表」，
// 个人中心 mine.js 那条「病案邮寄记录」占位入口由本卡接上）。
//
// 这一页同时是整条申请链路的入口：首页八个快捷位是 T06 定下来的，没有"病案配送"这一格，
// 所以「发起申请」放在这里（与 T21 核酸「个人中心 → 记录 → 去预约」同一条做法）。
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
    this.loadRows()
  },

  async loadRows() {
    this.setData({ loading: true })
    try {
      const rows = await get('/user/case-deliveries')
      this.setData({
        rows: (rows || []).map((item) => ({
          id: item.id,
          inpatientName: item.inpatientName || '—',
          inpatientNo: item.inpatientNo || '—',
          recipientName: item.recipientName || '—',
          statusLabel: deliveryStatusLabel(item.status),
          statusTone: item.status === 'DELIVERED' ? 'done' : (item.status === 'SHIPPED' ? 'confirmed' : 'pending'),
          timeText: formatDate(item.createdAt),
        })),
      })
    } catch (err) {
      // request.js 已统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onOpen(e) {
    wx.navigateTo({ url: `/pages/case-delivery/detail?id=${e.currentTarget.dataset.id}` })
  },

  onGoApply() {
    wx.navigateTo({ url: '/pages/case-delivery/notice' })
  },
})
