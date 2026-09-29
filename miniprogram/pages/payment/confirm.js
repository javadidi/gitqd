const app = getApp()
const { get, post } = require('../../utils/request')
const { formatMoney } = require('../../utils/format')

// 确认缴费信息（T15 卡片 511 + 512 行 / PRD 115 行「确认缴费信息 — 展示待缴费项目列表及金额」）。
//
// 卡片把「待缴费项目列表」和「确认缴费信息」列成两条功能项，但 PRD §3.3.4（114–116 行）和
// §6.1（514 行「门诊服务-自助缴费 | 确认缴费信息、缴费信息」）都只有两页，
// 且 115 行明写这一页展示的就是「待缴费项目列表」——所以是一页承载两条功能项，
// 不再多造一个只有列表的页面。
//
// 逐单缴费，不做批量：批量没有任何规格出处，而"两单一起缴、余额只够一单"会变成
// 半成功半失败的状态，规格里不存在这个语义。
Page({
  data: {
    loading: false,
    rows: [],
    payingId: null,
  },

  onShow() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadBills()
  },

  async loadBills() {
    this.setData({ loading: true })
    try {
      const list = await get('/user/payments/pending')
      this.setData({
        rows: (list || []).map((item) => ({
          id: item.id,
          orderNo: item.orderNo,
          patientName: item.patientName || '—',
          amountText: formatMoney(item.amountFen),
          items: (item.items || []).map((row) => ({
            name: row.name || '—',
            amountText: formatMoney(row.amountFen),
          })),
          itemCount: (item.items || []).length,
        })),
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onPay(e) {
    if (this.data.payingId) return
    const id = e.currentTarget.dataset.id
    const row = this.data.rows.find((item) => item.id === id)
    if (!row) return
    // 花钱的动作先二次确认（与 T09 绑定住院号、T13 退号同例）。
    // 文案里必须写清"从就诊卡余额扣"，否则患者会以为又调起了微信支付。
    wx.showModal({
      title: '确认缴费',
      content: `${row.patientName} 的 ${row.itemCount} 项费用共 ${row.amountText}，`
        + '将从就诊卡余额中扣除。余额不足会提示先充值。',
      confirmText: '确认缴费',
      cancelText: '再想想',
      success: (res) => {
        if (res.confirm) this.doPay(id)
      },
    })
  },

  async doPay(id) {
    this.setData({ payingId: id })
    try {
      const result = await post(`/user/payments/${id}/pay`, {})
      // 成功页只带 id，明细让它自己去 GET 详情：items 是数组，硬塞进 query 字符串既难读又会超长
      wx.redirectTo({ url: `/pages/payment/pay?id=${result.id}` })
    } catch (err) {
      // 3002（余额不足）/3004（已缴过）/5001（不是你的单）的 message 由 request.js toast，
      // 这里只解除防重，患者充值回来或改点另一单还能再试
      this.setData({ payingId: null })
    }
  },

  onRecords() {
    wx.navigateTo({ url: '/pages/payment/records' })
  },

  onGoRecharge() {
    // 余额不够是这条路最常见的死胡同，而补钱的出口在 T14 那一页
    wx.navigateTo({ url: '/pages/recharge/recharge' })
  },
})
