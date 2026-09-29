const app = getApp()
const { formatMoney } = require('../../utils/format')

// 支付成功（T14 卡片 495 行 / PRD 95 行「支付成功 — 展示充值成功信息」）。
//
// 到账后的余额必须在这里显示（PRD 98 行「充值金额实时到账就诊卡余额」）：
// 只写"充值成功"三个字，患者无从判断钱有没有真的进卡——
// 金额 + 到账后余额放在一起，才是"这笔账对了"的证据。
Page({
  data: {
    orderNo: '',
    patientName: '',
    amountText: '—',
    balanceText: '—',
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    const amountFen = options.amountFen ? Number(options.amountFen) : null
    const balanceFen = options.balanceFen ? Number(options.balanceFen) : null
    this.setData({
      orderNo: decodeURIComponent(options.orderNo || ''),
      patientName: decodeURIComponent(options.patientName || ''),
      amountText: formatMoney(amountFen),
      balanceText: formatMoney(balanceFen),
    })
  },

  onGoRecords() {
    // 成功页是流程终点，跳记录列表用 redirectTo 替换本页，
    // 返回时不该再回到一张"已经充过的"成功页
    wx.redirectTo({ url: '/pages/recharge/records' })
  },

  onBackHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
