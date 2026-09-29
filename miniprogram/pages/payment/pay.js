const app = getApp()
const { get } = require('../../utils/request')
const { formatMoney, formatDate, payMethodLabel } = require('../../utils/format')

// 缴费信息（T15 卡片 513 行的结果页 / PRD 116 行「缴费信息 — 缴费成功页，展示缴费明细」）。
//
// 明细不靠上一页传参，而是拿 id 回来重新 GET 一次：卡片要的是「缴费明细」，
// 明细只有一个真出处——就是那张单据本身。传参版本会在"成功页停了一会儿再看"时
// 显示一份与库里不一致的旧账单。
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
      const item = await get(`/user/payments/${id}`)
      this.setData({
        detail: {
          orderNo: item.orderNo || '—',
          patientName: item.patientName || '—',
          amountText: formatMoney(item.amountFen),
          // 刚扣完钱这一刻，接口回的余额就是扣费后余额；标签只在成功页这么写，
          // 详情页同一个字段标「当前卡内余额」（T14 定下的同一纪律）
          balanceText: formatMoney(item.balanceFen),
          status: item.status,
          payMethodText: payMethodLabel(item.payMethod),
          timeText: item.createdAt ? formatDate(item.createdAt, 'YYYY-MM-DD HH:mm') : '—',
          items: (item.items || []).map((row) => ({
            name: row.name || '—',
            amountText: formatMoney(row.amountFen),
          })),
        },
      })
    } catch (err) {
      // 5001 等消息由 request.js toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onGoRecords() {
    wx.redirectTo({ url: '/pages/payment/records' })
  },

  // 申请电子发票（T19 的入口，出处是 PRD §7.2「门诊缴费流程」第 554 行那条主流程：
  // 「… → 缴费成功 → 查看缴费记录/申请电子发票」。这是规格里唯一给发票指路的一句，
  // 首页八个快捷入口（§3.2）里没有发票，个人中心清单（§6.1 527 行）也没有，
  // 所以不在那里另开一个入口——那会是第二个出处。）
  onGoInvoice() {
    wx.navigateTo({ url: '/pages/invoice/pending' })
  },

  onBackHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
