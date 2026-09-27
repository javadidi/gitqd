const app = getApp()
const { get } = require('../../utils/request')
const { relationLabel } = require('../../utils/format')

Page({
  data: {
    patients: [],
    loading: false,
  },

  // 用 onShow 而不是 onLoad：从编辑页返回时要看到刚改完的结果
  onShow() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadPatients()
  },

  async loadPatients() {
    this.setData({ loading: true })
    try {
      const list = await get('/user/patients')
      // 后端只回关系码，中文标签在这里映射；身份证/手机号后端已打码，前端不再处理
      this.setData({ patients: (list || []).map(withRelationLabel) })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast，这里不再重复提示
    } finally {
      this.setData({ loading: false })
    }
  },

  onAdd() {
    wx.navigateTo({ url: '/pages/patient/edit' })
  },

  onItemTap(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: `/pages/patient/edit?id=${id}` })
  },
})

function withRelationLabel(patient) {
  return Object.assign({}, patient, { relationText: relationLabel(patient.relation) })
}
