const app = getApp()
const { get } = require('../../utils/request')
const {
  formatMoney, formatDate, rechargeStatusLabel, payMethodLabel,
} = require('../../utils/format')

// 住院充值的账单详情（PRD 301 行「账单详情 — 查看单笔充值明细」）。
//
// 明细能给的只有这一行单据本身：recharge_record 没有项目分解列（V1:140-151 只有
// 单号/两个主语 id/金额/方式/状态/流水号/时间戳），"明细"在这个数据模型里
// 不是一个数组。所以这一页把单据要素列全，不去造一个假的"费用项目列表"。
Page({
  data: {
    loading: false,
    detail: null,
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.id = options.id ? Number(options.id) : null
    this.loadDetail()
  },

  async loadDetail() {
    if (!this.id) {
      wx.showToast({ title: '缺少账单编号', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    try {
      const item = await get(`/user/inpatient-recharges/${this.id}`)
      if (!item) {
        return
      }
      this.setData({
        detail: {
          orderNo: item.orderNo || '—',
          inpatientName: item.inpatientName || '—',
          inpatientNo: item.inpatientNo || '—',
          amountText: formatMoney(item.amountFen),
          payMethodLabel: payMethodLabel(item.payMethod),
          statusLabel: rechargeStatusLabel(item.status),
          tradeNo: item.tradeNo || '—',
          timeText: formatDate(item.createdAt),
        },
      })
    } catch (err) {
      // 5001（不是你的单 / 那是门诊单）由 request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onBackRecords() {
    wx.navigateBack({
      fail: () => wx.redirectTo({ url: '/pages/inpatient-recharge/records' }),
    })
  },
})
