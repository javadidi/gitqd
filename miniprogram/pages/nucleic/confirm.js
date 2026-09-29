const app = getApp()
const { post } = require('../../utils/request')

// 确认预约信息（T21 卡片 621 行「确认预约信息：确认检测时间、地点等」/ PRD §3.7 第 201 行同一句）。
//
// 这一页只做一件事：把上一页填的东西原样摆出来给人复核，然后提交。
// 提交是这一页发的（卡片把"确认"放在申请之后，POST 放在复核之后才不越序），
// 而 §9.1 第 616 行只给了一个「创建检测预约」，所以确认动作不另开端点。
//
// 「地点」这一项刻意没有：V1:296-307 没有采样点列、PRD 588 行数据字典也没有、
// 全仓 28 张表没有采样点表 —— 编一个"门诊楼前广场"就是凭空造一个不存在的采样点。
// 所以这一页显示的是「检测日期 + 就诊人」，并用一行说明交代地点为什么不在。
Page({
  data: {
    patientId: null,
    patientName: '',
    appointmentDate: '',
    submitting: false,
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.setData({
      patientId: Number(options.patientId),
      patientName: decodeURIComponent(options.patientName || ''),
      appointmentDate: options.appointmentDate || '',
    })
  },

  onSubmit() {
    if (this.data.submitting) return
    if (!this.data.patientId || !this.data.appointmentDate) {
      wx.showToast({ title: '预约信息不完整，请返回重填', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    // 只提交两个字段：状态与报告都不在这里，患者提交不了它们（见 NucleicCreateRequest）
    post('/user/nucleic-appointments', {
      patientId: this.data.patientId,
      appointmentDate: this.data.appointmentDate,
    }).then((result) => {
      wx.redirectTo({ url: '/pages/nucleic/result?id=' + result.appointmentId })
    }).catch(() => {
      // 400（日期是过去的）/5001（就诊人不是自己的）的 message 后端都给了，
      // utils/request.js 已 toast；这里只解除防重，让用户改完能再点
      this.setData({ submitting: false })
    })
  },

  onBackEdit() {
    wx.navigateBack()
  },
})
