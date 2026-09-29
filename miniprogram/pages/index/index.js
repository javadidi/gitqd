const app = getApp()
const { get } = require('../../utils/request')
const { formatDate } = require('../../utils/format')

Page({
  data: {
    banners: [
      { id: 1, title: '预约挂号', image: '' },
    ],
    quickEntries: [
      { id: 'appointment', label: '预约挂号', icon: 'calendar', url: '/pages/appointment/appointment' },
      { id: 'recharge', label: '门诊充值', icon: 'wallet', url: '/pages/recharge/recharge' },
      { id: 'queue', label: '候诊查询', icon: 'clock', url: '/pages/queue/queue' },
      { id: 'payment', label: '自助缴费', icon: 'credit-card', url: '/pages/payment/confirm' },
      { id: 'report', label: '报告查询', icon: 'file-text', url: '/pages/report/type' },
      { id: 'record', label: '病历查询', icon: 'book', url: '/pages/record/list' },
      { id: 'followup', label: '复诊配药', icon: 'pill', url: '/pages/followup/apply' },
      { id: 'physical', label: '体检服务', icon: 'heart', url: '/pages/physical/packages' },
      { id: 'hospital', label: '医院服务', icon: 'hospital', url: '/pages/hospital/service' },
    ],
    notices: [],
    healthArticles: [],
  },

  onLoad() {
    this.loadData()
  },

  onPullDownRefresh() {
    this.loadData().then(() => {
      wx.stopPullDownRefresh()
    })
  },

  async loadData() {
    // 首页这两块内容（PRD 62 行「展示医院公告/停诊通知」、63 行「展示健康百科推荐内容」）
    // 读的是 T24 的只读端点。未登录时直接不发请求：这些端点按判断留在 /user/** 下
    // （没有为纯展示内容新开 permitAll，理由写在 HospitalServiceController 的类注释里），
    // 匿名去拿只会得到 401 并被 utils/request.js toast 成一条患者看得见的报错。
    if (!app.globalData.token) {
      return
    }
    try {
      const [notices, articles] = await Promise.all([
        get('/user/stop-notices'),
        get('/user/health-articles'),
      ])
      this.setData({
        notices: (notices || []).slice(0, 3).map((item) => ({
          id: item.id,
          title: item.title,
          time: item.publishTime ? formatDate(item.publishTime) : '',
        })),
        // 「推荐内容」在这里就是"最新两条"：V6 没有 is_recommend 这样的列，
        // PRD 也没定义推荐规则，所以不替它编一个排序口径。
        healthArticles: (articles || []).slice(0, 2).map((item) => ({
          id: item.id,
          title: item.title,
          timeText: item.publishTime ? formatDate(item.publishTime) : '',
        })),
      })
    } catch (err) {
      // 拉不到就保持空块：首页两块内容都是装饰性入口，不该拦住患者用主功能
    }
  },

  onMoreArticles() {
    if (!app.globalData.token) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    wx.navigateTo({ url: '/pages/hospital/articles' })
  },

  onQuickEntryTap(e) {
    const { url, label } = e.currentTarget.dataset
    if (!url) {
      wx.showToast({ title: `${label}即将开放`, icon: 'none' })
      return
    }
    wx.navigateTo({ url })
  },
})
