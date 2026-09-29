const app = getApp()
const { formatMoney, rechargeStatusLabel } = require('../../utils/format')

// 住院充值支付成功（T23 卡片 657 行的终点 / PRD 227 行「支付成功 — 充值成功提示」）。
//
// 与 T14 的门诊成功页差一栏：**这里没有"到账后余额"**。
// 门诊那张页显示余额是因为 PRD 98 行写了「实时到账就诊卡余额」，患者要看见钱进了卡；
// 住院侧没有任何到账规格，inpatient 表也没有余额列（V1:41-53），
// 所以这一页只回"哪笔钱、充给谁、单据什么状态"，余额那一栏整个不做——
// 放一个 0 或者拿就诊卡余额来充数，都是给患者看假账。
Page({
  data: {
    id: null,
    orderNo: '',
    inpatientName: '',
    amountText: '—',
    statusLabel: '—',
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    const amountFen = options.amountFen ? Number(options.amountFen) : null
    this.setData({
      id: options.id ? Number(options.id) : null,
      orderNo: decodeURIComponent(options.orderNo || ''),
      inpatientName: decodeURIComponent(options.inpatientName || ''),
      amountText: formatMoney(amountFen),
      // 状态码与门诊充值共用同一列（V1:147 的 PENDING/SUCCESS/REFUNDED），标签表也共用
      statusLabel: rechargeStatusLabel('SUCCESS'),
    })
  },

  onGoDetail() {
    if (!this.data.id) {
      wx.showToast({ title: '缺少账单编号', icon: 'none' })
      return
    }
    wx.navigateTo({ url: `/pages/inpatient-recharge/detail?id=${this.data.id}` })
  },

  onGoRecords() {
    wx.redirectTo({ url: '/pages/inpatient-recharge/records' })
  },

  onBackHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
