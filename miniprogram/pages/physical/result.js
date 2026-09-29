const app = getApp()
const { get } = require('../../utils/request')
const { formatMoney, physicalStatusLabel, physicalStatusTone } = require('../../utils/format')

// 预约成功（PRD §3.8 第 215 行「预约成功 — 预约成功提示页」，§6.1 第 522 行也列了这一页；
// 卡片没写这一页，但它是 PRD 流程的收尾一步，与 T21 同判法）。
//
// 与 T19/T20/T21 的成功页同一做法：不接上一页 POST 回的那份数据，而是重新读一次。
// 差别在于本卡没有单条详情端点（§9.1 第 617 行只给四项，PRD 310 行的「预约详情」
// 并进列表那一行，见 PhysicalAppointmentController 类注释），
// 所以这里拉列表、按 appointmentId 找自己那一行——仍然是从库里读出来的当前值，
// 不是提交那一刻的快照。
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
      const rows = await get('/user/physical-appointments')
      const found = (rows || []).find((item) => String(item.appointmentId) === String(id))
      if (!found) {
        // 刚提交完却读不到自己那一行，说明这条记录不属于当前账号——如实显示空态而不是
        // 拿 URL 上带过来的参数凑一份"看起来成功"的页面
        this.setData({ detail: null })
        return
      }
      this.setData({
        detail: {
          orderNo: found.orderNo || '—',
          patientName: found.patientName || '—',
          packageName: found.packageName || '—',
          priceText: found.priceFen === undefined ? '—' : formatMoney(found.priceFen),
          appointmentDate: found.appointmentDate || '—',
          statusLabel: physicalStatusLabel(found.status),
          statusTone: physicalStatusTone(found.status),
        },
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onGoRecords() {
    wx.redirectTo({ url: '/pages/physical/list' })
  },

  onGoReports() {
    wx.navigateTo({ url: '/pages/report/list?type=PHYSICAL' })
  },

  onBackHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
