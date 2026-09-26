const app = getApp()
const { post } = require('../../utils/request')

Page({
  data: {
    phoneNumber: '',
    verifying: false,
  },

  onPhoneInput(e) {
    this.setData({ phoneNumber: e.detail.value })
  },

  async onWechatLogin() {
    this.setData({ verifying: true })
    try {
      const loginRes = await new Promise((resolve, reject) => {
        wx.login({
          success: resolve,
          fail: reject,
        })
      })
      const result = await post('/auth/wechat-login', {
        code: loginRes.code,
      })
      app.setToken(result.token)
      wx.switchTab({ url: '/pages/index/index' })
    } catch (err) {
      wx.showToast({ title: '登录失败，请重试', icon: 'none' })
    } finally {
      this.setData({ verifying: false })
    }
  },

  onPhoneLogin() {
    const { phoneNumber } = this.data
    if (!phoneNumber || phoneNumber.length !== 11) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return
    }
    wx.navigateTo({
      url: `/pages/login/login?phone=${phoneNumber}`,
    })
  },
})
