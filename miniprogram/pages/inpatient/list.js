const app = getApp()
const { get } = require('../../utils/request')

Page({
  data: {
    inpatients: [],
    loading: false,
  },

  // 用 onShow 而不是 onLoad：从绑定页返回时要看到刚绑好的那个人
  onShow() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadInpatients()
  },

  async loadInpatients() {
    this.setData({ loading: true })
    try {
      const list = await get('/user/inpatients')
      // 科室/床号可能整个键都不在响应里（后端 non_null 序列化），wxml 用 || '—' 兜住
      this.setData({ inpatients: list || [] })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast，这里不再重复提示
    } finally {
      this.setData({ loading: false })
    }
  },

  onBind() {
    wx.navigateTo({ url: '/pages/inpatient/bind' })
  },

  onItemTap(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: `/pages/inpatient/detail?id=${id}` })
  },
})
