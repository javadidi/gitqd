const app = getApp()
const { REPORT_QUERY_TYPES } = require('../../utils/format')

// 选择报告类型（T17 卡片 548 行「选择报告类型：检验报告/检查报告」/
// PRD §3.4.1 第 161 行同名的流程第一步 / §6.1 第 518 行把它单列成一个页面名）。
//
// 两类是规格写死的两个词（PRD 161 行），不是接口回来的数据，所以这里用常量而不是拉列表。
// 也正因为如此，这一页不请求后端——它唯一的真实动作是把类型带进下一跳的 URL，
// 让附录 B 第 10 条「列表筛选是否进 URL」成立（列表页可以脱离这一页直接打开）。
//
// 未登录守卫照 T16 候诊页的做法放在 onLoad：这一页本身不出患者数据，
// 但它是患者数据的入口，三页统一一条规矩比"这页刚好不用"更好读。
Page({
  data: {
    types: REPORT_QUERY_TYPES,
  },

  onLoad() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
    }
  },

  onPick(e) {
    wx.navigateTo({ url: '/pages/report/list?type=' + e.currentTarget.dataset.type })
  },
})
