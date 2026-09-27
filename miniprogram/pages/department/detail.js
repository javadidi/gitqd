const app = getApp()
const { get } = require('../../utils/request')

Page({
  data: {
    loading: false,
    departmentId: null,
    name: '',
    intro: '',
    location: '',
    doctors: [],
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.setData({ departmentId: options.id })
    this.loadDetail(options.id)
  },

  async loadDetail(id) {
    this.setData({ loading: true })
    try {
      const detail = await get(`/user/departments/${id}`)
      this.setData({
        name: detail.name || '',
        // intro/location 可能整键消失（后端 non_null 序列化），一律兜成空串再由 wxml 显示 '—'
        intro: detail.intro || '',
        location: detail.location || '',
        doctors: detail.doctors || [],
      })
      wx.setNavigationBarTitle({ title: detail.name || '科室详情' })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onDoctorTap(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: `/pages/doctor/detail?id=${id}` })
  },

  onBackToList() {
    wx.navigateBack()
  },
})
