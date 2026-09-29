const app = getApp()
const { get } = require('../../utils/request')
const { nucleicStatusLabel, nucleicStatusTone } = require('../../utils/format')

// 核酸检测报告（T21 卡片 622 行「核酸检测报告：查看检测报告」/ PRD §3.7 第 203 行、
// §3.11.7 第 306 行同一句；接口出处 PRD §9.1 第 616 行「检测报告」）。
//
// 这一页同时承担 PRD §3.11.7 第 305 行的「预约详情」——报告页本来就要显示
// "这是哪一次检测"（单号/就诊人/日期/状态），再开一个详情端点就是给同一份数据造第二个出处，
// 所以 §9.1 给的两个端点在这里合成一页（判法见 NucleicAppointmentController 类注释）。
//
// 关键一条：响应里没有 report 键时，页面显示「报告未出」，
// 绝不写 detail.report || '阴性' 这种拿默认值冒充结论的兜底。
// 首版产品代码不产生任何报告内容（红线 624 行，四条证据见 NucleicReportResponse 类注释），
// 所以这一页在真实数据下必然走"未出"分支 —— 这不是缺陷，是这条流程诚实的样子。
Page({
  data: {
    loading: false,
    id: null,
    detail: null,
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.setData({ id: options.id })
    this.loadDetail(options.id)
  },

  async loadDetail(id) {
    this.setData({ loading: true })
    try {
      const item = await get('/user/nucleic-appointments/' + id + '/report')
      const issued = item.report !== undefined && item.report !== null && item.report !== ''
      this.setData({
        detail: {
          orderNo: item.orderNo || '—',
          patientName: item.patientName || '—',
          appointmentDate: item.appointmentDate || '—',
          statusLabel: nucleicStatusLabel(item.status),
          statusTone: nucleicStatusTone(item.status),
          issued,
          report: issued ? item.report : '',
        },
      })
    } catch (err) {
      // 5001 等消息由 request.js toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onGoList() {
    wx.redirectTo({ url: '/pages/nucleic/list' })
  },

  onBackHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
