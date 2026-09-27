const app = getApp()
const { post } = require('../../utils/request')

Page({
  data: {
    name: '',
    inpatientNo: '',
    department: '',
    bedNo: '',
    submitting: false,
  },

  onLoad() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
    }
  },

  onNameInput(e) {
    this.setData({ name: e.detail.value })
  },

  onInpatientNoInput(e) {
    this.setData({ inpatientNo: e.detail.value })
  },

  onDepartmentInput(e) {
    this.setData({ department: e.detail.value })
  },

  onBedNoInput(e) {
    this.setData({ bedNo: e.detail.value })
  },

  onSubmit() {
    if (this.data.submitting) return

    const name = (this.data.name || '').trim()
    const inpatientNo = (this.data.inpatientNo || '').trim()
    const department = (this.data.department || '').trim()
    const bedNo = (this.data.bedNo || '').trim()

    if (!name) {
      return wx.showToast({ title: '请填写姓名', icon: 'none' })
    }
    if (!inpatientNo) {
      return wx.showToast({ title: '请填写住院号', icon: 'none' })
    }

    // PRD §3.11.2 的「确认住院人信息」：住院号一旦绑定就改不了（本卡没有编辑/解绑入口），
    // 所以提交前必须让用户核对一遍，而不是点一下就直接落库
    wx.showModal({
      title: '确认住院人信息',
      content: `姓名：${name}\n住院号：${inpatientNo}\n科室：${department || '未填写'}\n床号：${bedNo || '未填写'}`,
      confirmText: '确认绑定',
      success: (res) => {
        if (res.confirm) {
          this.doBind({ name, inpatientNo, department, bedNo })
        }
      },
    })
  },

  async doBind(payload) {
    this.setData({ submitting: true })
    try {
      await post('/user/inpatients', payload)
      wx.showToast({ title: '绑定成功', icon: 'success' })
      // 稍等一下让 toast 看得见，再回列表页（列表页 onShow 会重新拉数据）
      setTimeout(() => wx.navigateBack(), 600)
    } catch (err) {
      // 后端错误消息（含"住院号已存在"）已由 utils/request.js 统一 toast
    } finally {
      this.setData({ submitting: false })
    }
  },
})
