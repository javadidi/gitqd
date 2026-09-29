Page({
  data: {
    banners: [
      { id: 1, title: '预约挂号', image: '' },
    ],
    quickEntries: [
      { id: 'appointment', label: '预约挂号', icon: 'calendar', url: '/pages/appointment/appointment' },
      { id: 'recharge', label: '门诊充值', icon: 'wallet', url: '/pages/recharge/recharge' },
      { id: 'queue', label: '候诊查询', icon: 'clock', url: '' },
      { id: 'payment', label: '自助缴费', icon: 'credit-card', url: '' },
      { id: 'report', label: '报告查询', icon: 'file-text', url: '' },
      { id: 'record', label: '病历查询', icon: 'book', url: '' },
      { id: 'followup', label: '复诊配药', icon: 'pill', url: '' },
      { id: 'physical', label: '体检服务', icon: 'heart', url: '' },
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
    // T07+ will implement real API calls
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
