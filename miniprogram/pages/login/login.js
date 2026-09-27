const app = getApp()
const { post } = require('../../utils/request')

function wxLogin() {
  return new Promise((resolve, reject) => {
    wx.login({ success: resolve, fail: reject })
  })
}

Page({
  data: {
    verifying: false,
  },

  async onWechatLogin() {
    if (this.data.verifying) return

    let code
    this.setData({ verifying: true })
    try {
      const res = await wxLogin()
      code = res.code
    } catch (err) {
      this.setData({ verifying: false })
      wx.showToast({ title: '微信登录失败，请重试', icon: 'none' })
      return
    }

    try {
      const result = await post('/auth/wechat-login', { code })
      app.setToken(result.token)
      if (result.hasPhone) {
        wx.switchTab({ url: '/pages/index/index' })
      } else {
        this.guideToBindPhone()
      }
    } catch (err) {
      // 后端错误消息已由 utils/request.js 统一 toast，这里不再重复提示
    } finally {
      this.setData({ verifying: false })
    }
  },

  guideToBindPhone() {
    wx.showModal({
      title: '绑定手机号',
      content: '登录成功。绑定手机号后才能接收预约与就诊提醒，是否现在绑定？',
      confirmText: '去绑定',
      cancelText: '稍后再说',
      success(res) {
        wx.switchTab({ url: res.confirm ? '/pages/mine/mine' : '/pages/index/index' })
      },
      fail() {
        wx.switchTab({ url: '/pages/index/index' })
      },
    })
  },
})
