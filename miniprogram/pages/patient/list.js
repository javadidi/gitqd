const app = getApp()
const { get, del } = require('../../utils/request')
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

  onDelete(e) {
    const { id, name } = e.currentTarget.dataset
    wx.showModal({
      title: '删除就诊人',
      // 文案要对得上后端语义：软删（历史单据不受影响）+ 卡号仍归本人（同号可重新添加）
      content: `确定删除「${name}」吗？该就诊人的挂号与缴费记录会保留，卡号仍归你，重新添加同一卡号即可恢复。`,
      confirmText: '删除',
      confirmColor: '#e11d48',
      success: (res) => {
        if (res.confirm) {
          this.doDelete(id)
        }
      },
    })
  },

  async doDelete(id) {
    try {
      await del(`/user/patients/${id}`)
      wx.showToast({ title: '已删除', icon: 'success' })
      this.loadPatients()
    } catch (err) {
      // 后端消息已由 utils/request.js 统一 toast，这里不再重复提示
    }
  },
})

function withRelationLabel(patient) {
  return Object.assign({}, patient, { relationText: relationLabel(patient.relation) })
}
