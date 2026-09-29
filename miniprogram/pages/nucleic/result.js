const app = getApp()
const { get } = require('../../utils/request')
const { nucleicStatusLabel, nucleicStatusTone } = require('../../utils/format')

// 预约成功（PRD §3.7 第 202 行「预约成功 — 预约成功提示页」，§6.1 第 521 行也列了这一页）。
//
// 与 T19/T20 的成功页同一做法：不接上一页 POST 回的那份数据，而是拿 appointmentId 重新 GET 一次。
// 这一条对本卡比对前两卡更有实际意义：报告页与列表页都可能出现"状态已经变了"的情况
// （二期接了检测侧就会变），成功页冻在提交那一刻的快照上就是假信息。
//
// 这一页同时给了两条出路：直接看报告，或者去个人中心的「核酸预约记录」——
// 后者是 PRD §3.7 第 203 行那句「在个人中心查看检测报告」的落点。
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
      this.setData({
        detail: {
          orderNo: item.orderNo || '—',
          patientName: item.patientName || '—',
          appointmentDate: item.appointmentDate || '—',
          statusLabel: nucleicStatusLabel(item.status),
          statusTone: nucleicStatusTone(item.status),
        },
      })
    } catch (err) {
      // 5001 等消息由 request.js toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onGoReport() {
    wx.redirectTo({ url: '/pages/nucleic/report?id=' + this.data.id })
  },

  onGoList() {
    wx.redirectTo({ url: '/pages/nucleic/list' })
  },

  onBackHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
