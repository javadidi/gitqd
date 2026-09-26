const app = getApp()

Page({
  data: {
    isLoggedIn: false,
    userInfo: null,
    menuItems: [
      { group: '就诊服务', items: [
        { label: '就诊人管理', icon: '👤', url: '' },
        { label: '住院人管理', icon: '🏥', url: '' },
      ]},
      { group: '预约记录', items: [
        { label: '预约挂号记录', icon: '📋', url: '' },
        { label: '核酸预约记录', icon: '🧪', url: '' },
        { label: '体检预约记录', icon: '❤️', url: '' },
      ]},
      { group: '缴费记录', items: [
        { label: '门诊缴费记录', icon: '💳', url: '' },
        { label: '门诊充值记录', icon: '💰', url: '' },
        { label: '住院充值记录', icon: '🏦', url: '' },
      ]},
      { group: '其他', items: [
        { label: '病案邮寄记录', icon: '📦', url: '' },
        { label: '问题反馈', icon: '💬', url: '' },
        { label: '消息通知', icon: '🔔', url: '' },
      ]},
    ],
  },

  onShow() {
    const token = app.globalData.token
    this.setData({ isLoggedIn: !!token })
  },

  onMenuTap(e) {
    const { url, label } = e.currentTarget.dataset
    if (!this.data.isLoggedIn) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    if (!url) {
      wx.showToast({ title: `${label}即将开放`, icon: 'none' })
      return
    }
    wx.navigateTo({ url })
  },

  onLoginTap() {
    wx.navigateTo({ url: '/pages/login/login' })
  },
})
