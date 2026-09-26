App({
  globalData: {
    userInfo: null,
    token: '',
    baseUrl: 'http://localhost:8080/api',
  },

  onLaunch() {
    const token = wx.getStorageSync('token')
    if (token) {
      this.globalData.token = token
    }
  },

  setToken(token) {
    this.globalData.token = token
    wx.setStorageSync('token', token)
  },

  clearToken() {
    this.globalData.token = ''
    wx.removeStorageSync('token')
  },
})
