const app = getApp()
const { get, post } = require('../../utils/request')
const { formatMoney, formatDate } = require('../../utils/format')

// 待开具电子发票（T19 卡片 581 行「待开具电子发票：展示可开票的缴费记录」/
// PRD §3.3.8 第 149 行同一句，页面名也出现在 §6.1 第 516 行）。
//
// 这一页列的是**缴费单**不是发票：还没开票的发票在库里根本不存在，
// 所以"可开票"只能是"已缴成功且还没被开票的 payment_record"。
Page({
  data: {
    loading: false,
    submitting: false,
    rows: [],
  },

  onShow() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    // onShow 而不是 onLoad：刚在缴费页缴完一笔再进来，这里要能看见新单
    this.loadPending()
  },

  async loadPending() {
    this.setData({ loading: true })
    try {
      const list = await get('/user/invoices/pending')
      this.setData({
        rows: (list || []).map((item) => ({
          paymentId: item.paymentId,
          orderNo: item.orderNo || '—',
          patientName: item.patientName || '—',
          amountText: formatMoney(item.amountFen),
          paidAtText: item.paidAt ? formatDate(item.paidAt, 'YYYY-MM-DD HH:mm') : '—',
        })),
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  // 卡片 582 行「开票申请：提交开票申请」。只发 paymentId——金额由服务端从缴费单抄，
  // 前端连金额都不发，就没有"改个数字去开票"这条路（T15 同一条纪律）。
  async onIssue(e) {
    if (this.data.submitting) {
      return
    }
    const paymentId = e.currentTarget.dataset.id
    this.setData({ submitting: true })
    try {
      const invoice = await post('/user/invoices', { paymentId })
      wx.navigateTo({ url: '/pages/invoice/result?id=' + invoice.invoiceId })
    } catch (err) {
      // 3005（这张单已经开过了）等消息由 request.js toast
    } finally {
      this.setData({ submitting: false })
      // 无论成功还是被拒都重拉一次：既然服务端说了话，列表就该跟它一致，
      // 不该留给患者一张还能再点一次的按钮
      this.loadPending()
    }
  },

  onGoPay() {
    wx.navigateTo({ url: '/pages/payment/confirm' })
  },

  onGoIssued() {
    wx.navigateTo({ url: '/pages/invoice/list' })
  },
})
