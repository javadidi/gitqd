const app = getApp()
const { get } = require('../../utils/request')
const { formatDate } = require('../../utils/format')

// 健康百科文章列表（T24 卡片 681 行 / J54「健康百科 → 文章列表正确」/ PRD 265 行）。
//
// 列表只显示标题与发布时间：V6 没有摘要列，PRD 265 行也只写「展示健康科普文章列表」，
// 造一个"文章摘要"就是替规格发明字段（同 T18 的「医嘱」不加列）。
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
      const rows = await get('/user/health-articles')
      this.setData({
        rows: (rows || []).map((item) => ({
          id: item.id,
          title: item.title || '—',
          timeText: item.publishTime ? formatDate(item.publishTime) : '',
        })),
      })
    } catch (err) {
      // request.js 已统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onOpen(e) {
    wx.navigateTo({ url: `/pages/hospital/article?id=${e.currentTarget.dataset.id}` })
  },
})
