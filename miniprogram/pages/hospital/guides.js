const app = getApp()
const { get } = require('../../utils/request')
const { formatDate } = require('../../utils/format')

// 就医指南列表（T24 卡片 680 行 / PRD 261–262 行）。
// PRD 目前只点名一篇「预约流程」，但生产侧是 CRUD（卡片 742 行），所以按列表读、点进去看详情。
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
      const rows = await get('/user/guides')
      this.setData({
        rows: (rows || []).map((item) => ({
          id: item.id,
          title: item.title || '—',
          timeText: formatDate(item.updatedAt),
        })),
      })
    } catch (err) {
      // request.js 已统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onOpen(e) {
    wx.navigateTo({ url: `/pages/hospital/article?id=${e.currentTarget.dataset.id}&kind=guide` })
  },
})
