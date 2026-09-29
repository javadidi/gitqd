const app = getApp()
const { get } = require('../../utils/request')
const { nucleicStatusLabel, nucleicStatusTone } = require('../../utils/format')

// 核酸预约记录（T21；PRD §3.11.7 第 304 行「预约记录列表 — 展示核酸检测预约历史」，
// §6.1 第 527 行把它列成个人中心的一个页面名，入口在 pages/mine/mine.js 第 25 行）。
//
// 这一页是 §9.1 第 615 行没给、却被 PRD 的页面名撑出来的第三个端点的数据来源
// （推导见 NucleicAppointmentController 类注释）。有了它，PRD 203 行那句
// 「核酸检测报告 —— 在个人中心查看检测报告」才真的走得通：
// 个人中心 → 本列表 → 点一行 → 报告页。
//
// 列表按创建时间倒序（最新一次预约在最前），与 T13/T14/T15/T19/T20 的记录列表同口径。
// 一个筛选参数都没有：表里只有日期与状态两列可筛（V1:296-307），而 PRD 没要求按任何条件筛，
// 造一个"按状态筛"就是发明需求（附录 B 第 10 条对本卡 N/A）。
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
      const rows = await get('/user/nucleic-appointments')
      this.setData({
        rows: (rows || []).map((item) => ({
          appointmentId: item.appointmentId,
          orderNo: item.orderNo || '—',
          patientName: item.patientName || '—',
          appointmentDate: item.appointmentDate || '—',
          statusLabel: nucleicStatusLabel(item.status),
          statusTone: nucleicStatusTone(item.status),
        })),
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onOpenReport(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    wx.navigateTo({ url: '/pages/nucleic/report?id=' + id })
  },

  onGoApply() {
    wx.redirectTo({ url: '/pages/nucleic/apply' })
  },
})
