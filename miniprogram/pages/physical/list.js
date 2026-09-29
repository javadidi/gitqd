const app = getApp()
const { get } = require('../../utils/request')
const { formatMoney, physicalStatusLabel, physicalStatusTone } = require('../../utils/format')

// 体检预约记录（PRD §3.11.8 第 309 行「预约记录列表 — 展示体检预约历史」、
// §6.1 第 527 行个人中心页面名「体检预约记录」；入口在 pages/mine/mine.js 第 26 行）。
//
// 三件事说清楚：
// 1. 这一页是 §9.1 第 617 行没给、被 PRD 的页面名撑出来的第四个端点的数据来源
//    （判法与 T19 待开具、T21 核酸记录列表完全相同）。
// 2. 行本身不可点：PRD 310 行的「预约详情」并进这一行了——
//    一行七个字段（单号/体检人/套餐/费用/日期/状态）就是详情的全部内容，
//    再造一个详情端点等于同一份数据两个出处。
// 3. 「体检报告」是页级入口不是行级入口：PRD 311 行把它列成 §3.11.8 的第三步，
//    而报告数据在 report 表里、按就诊人归属，与预约行没有外键关系
//    （V1:202-215 那张表里没有任何指向 physical_appointment 的列）。
//    所以这里给一个页级按钮跳 T17 的报告列表带 type=PHYSICAL，
//    不在每一行上放"查看本报告"——那会暗示一个不存在的关联。
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
      const rows = await get('/user/physical-appointments')
      this.setData({
        rows: (rows || []).map((item) => ({
          appointmentId: item.appointmentId,
          orderNo: item.orderNo || '—',
          patientName: item.patientName || '—',
          // 套餐被软删时这两个键整个消失（NON_NULL），显示 — 而不是 0 元
          packageName: item.packageName || '—',
          priceText: item.priceFen === undefined ? '—' : formatMoney(item.priceFen),
          appointmentDate: item.appointmentDate || '—',
          statusLabel: physicalStatusLabel(item.status),
          statusTone: physicalStatusTone(item.status),
        })),
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onGoPackages() {
    wx.redirectTo({ url: '/pages/physical/packages' })
  },

  onGoReports() {
    wx.navigateTo({ url: '/pages/report/list?type=PHYSICAL' })
  },
})
