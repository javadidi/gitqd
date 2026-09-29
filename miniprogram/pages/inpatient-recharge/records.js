const app = getApp()
const { get } = require('../../utils/request')
const {
  formatMoney, formatDate, rechargeStatusLabel, payMethodLabel,
} = require('../../utils/format')

// 住院充值记录（T23 卡片 657 行的「查看充值记录」/ PRD 300 行「充值记录列表」
// + PRD 232-233 行「选择住院人员 → 住院记录」）。
//
// 这一页同时服务两条入口，靠 URL 上的 inpatientId 区分（附录 B「列表筛选是否进 URL」）：
// 1. 不带参数 = 个人中心「住院充值记录」，看本人全部住院人的流水；
// 2. 带 ?inpatientId= = 从住院服务进来，只看这一个住院人，并在顶部显示他的住院信息。
//
// 卡片 658 行的「住院记录查询」落在这一页，而不是新造一页去显示不存在的数据：
// schema 里没有住院记录表（seed.sql:93 的注释原话），患者能查到的"住院历史"
// 就是「这个住院人的基本信息 + 他名下的充值流水」。
// 659/660 行的「费用详情 / 住院日清单」不做，页面标题也不写"费用"两个字。
Page({
  data: {
    loading: false,
    inpatientId: null,
    inpatient: null,
    rows: [],
  },

  onLoad(options) {
    this.setData({ inpatientId: options.inpatientId ? Number(options.inpatientId) : null })
  },

  onShow() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadRecords()
  },

  async loadRecords() {
    this.setData({ loading: true })
    const inpatientId = this.data.inpatientId
    try {
      const rows = inpatientId
        ? await get(`/user/inpatient-recharges?inpatientId=${inpatientId}`)
        : await get('/user/inpatient-recharges')
      this.setData({
        rows: (rows || []).map((item) => ({
          id: item.id,
          orderNo: item.orderNo,
          inpatientName: item.inpatientName || '—',
          inpatientNo: item.inpatientNo || '—',
          amountText: formatMoney(item.amountFen),
          statusLabel: rechargeStatusLabel(item.status),
          payMethodLabel: payMethodLabel(item.payMethod),
          dateText: formatDate(item.createdAt),
        })),
      })
      if (inpatientId) {
        await this.loadInpatient(inpatientId)
      }
    } catch (err) {
      // 1005（这个住院人不是你的）/ 5001 的 message 后端已给，request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  async loadInpatient(inpatientId) {
    const detail = await get(`/user/inpatients/${inpatientId}`)
    if (!detail) {
      return
    }
    this.setData({
      inpatient: {
        name: detail.name || '—',
        inpatientNo: detail.inpatientNo || '—',
        department: detail.department || '—',
        bedNo: detail.bedNo || '—',
      },
    })
  },

  onOpen(e) {
    wx.navigateTo({ url: `/pages/inpatient-recharge/detail?id=${e.currentTarget.dataset.id}` })
  },

  onGoRecharge() {
    wx.navigateTo({ url: '/pages/inpatient-recharge/recharge' })
  },
})
