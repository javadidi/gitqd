const app = getApp()
const { get } = require('../../utils/request')
const { formatDate } = require('../../utils/format')

Page({
  data: {
    inpatient: null,
    boundAtText: '—',
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.id = options && options.id
    if (this.id) {
      this.loadDetail()
    }
  },

  async loadDetail() {
    try {
      const detail = await get(`/user/inpatients/${this.id}`)
      this.setData({
        inpatient: detail,
        // 后端回的是 ISO 字符串，展示格式在前端定（与 relation 的中文标签同一套取舍）
        boundAtText: formatDate(detail.boundAt, 'YYYY-MM-DD HH:mm'),
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast（越权/不存在都是 1005）
    }
  },
})
