const app = getApp()
const { get } = require('../../utils/request')
const { formatDate } = require('../../utils/format')

// 停诊通知列表（T24 卡片 682 行 / PRD 268–269 行）。
//
// 首版这一页注定是空的：announcement 表零行，而 T25–T28 四张后台卡里没有任何一条
// 是"发布停诊通知"（T25 卡片 701 行的「临时停诊/调班」管的是排班本身，不是公告）。
// 所以这里显示空态，而不是替医院编一条"某医生周几停诊"。
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
      const rows = await get('/user/stop-notices')
      this.setData({
        rows: (rows || []).map((item) => ({
          id: item.id,
          title: item.title || '—',
          content: item.content || '',
          timeText: item.publishTime ? formatDate(item.publishTime) : '',
        })),
      })
    } catch (err) {
      // request.js 已统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },
})
