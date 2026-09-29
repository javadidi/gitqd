const app = getApp()
const { get, post, put } = require('../../utils/request')

const PHONE_REG = /^1[3-9]\d{9}$/
const CODE_REG = /^\d{6}$/
// 与后端 SmsCodeService.RESEND_INTERVAL 保持一致
const RESEND_INTERVAL = 60

Page({
  data: {
    isLoggedIn: false,
    profile: null,
    phone: '',
    smsCode: '',
    countdown: 0,
    sending: false,
    binding: false,
    menuItems: [
      { group: '就诊服务', items: [
        { label: '就诊人管理', icon: '👤', url: '/pages/patient/list' },
        { label: '住院人管理', icon: '🏥', url: '/pages/inpatient/list' },
      ]},
      { group: '预约记录', items: [
        { label: '预约挂号记录', icon: '📋', url: '/pages/appointment/records' },
        // key 只为验收服务：这一组十一行共用 .menu-item 一个类名，
        // 单类名选择器永远只命中第一行，补一个唯一类才能真点击（同 T12 补 .res-btn-home 的做法）。
        { label: '核酸预约记录', icon: '🧪', url: '/pages/nucleic/list', key: 'nucleic' },
        { label: '体检预约记录', icon: '❤️', url: '/pages/physical/list', key: 'physical' },
      ]},
      { group: '缴费记录', items: [
        { label: '门诊缴费记录', icon: '💳', url: '/pages/payment/records' },
        { label: '门诊充值记录', icon: '💰', url: '/pages/recharge/records' },
        { label: '住院充值记录', icon: '🏦', url: '/pages/inpatient-recharge/records', key: 'inpatientRecharge' },
      ]},
      { group: '其他', items: [
        { label: '病案邮寄记录', icon: '📦', url: '/pages/case-delivery/list', key: 'caseDelivery' },
        { label: '问题反馈', icon: '💬', url: '' },
        { label: '消息通知', icon: '🔔', url: '' },
      ]},
    ],
  },

  onShow() {
    const token = app.globalData.token
    this.setData({ isLoggedIn: !!token })
    if (token) {
      this.loadProfile()
    } else {
      this.stopCountdown()
      this.setData({ profile: null, phone: '', smsCode: '', countdown: 0 })
    }
  },

  onHide() {
    this.stopCountdown()
  },

  onUnload() {
    this.stopCountdown()
  },

  async loadProfile() {
    try {
      const profile = await get('/user/profile')
      this.setData({ profile })
    } catch (err) {
      // 401 时 request.js 已清 token 并跳登录页，其余错误消息也已 toast
    }
  },

  onEditNickname() {
    const current = (this.data.profile && this.data.profile.nickname) || ''
    wx.showModal({
      title: '修改昵称',
      editable: true,
      content: current,
      placeholderText: '请输入昵称（1-64 字）',
      success: (res) => {
        if (!res.confirm) return
        const nickname = (res.content || '').trim()
        if (!nickname || nickname.length > 64) {
          wx.showToast({ title: '昵称需为 1-64 字', icon: 'none' })
          return
        }
        this.submitNickname(nickname)
      },
    })
  },

  async submitNickname(nickname) {
    try {
      const profile = await put('/user/profile', { nickname })
      this.setData({ profile })
      wx.showToast({ title: '昵称已更新', icon: 'success' })
    } catch (err) {
      // 错误消息已由 request.js toast
    }
  },

  onPhoneInput(e) {
    this.setData({ phone: e.detail.value })
  },

  onCodeInput(e) {
    this.setData({ smsCode: e.detail.value })
  },

  async onSendCode() {
    if (this.data.sending || this.data.countdown > 0) return
    const phone = (this.data.phone || '').trim()
    if (!PHONE_REG.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return
    }
    this.setData({ sending: true })
    try {
      await post('/user/sms-code', { phone })
      wx.showToast({ title: '验证码已发送', icon: 'success' })
      this.startCountdown()
    } catch (err) {
      // 后端限频（4006）等错误消息已由 request.js toast
    } finally {
      this.setData({ sending: false })
    }
  },

  async onBindPhone() {
    if (this.data.binding) return
    const phone = (this.data.phone || '').trim()
    const code = (this.data.smsCode || '').trim()
    if (!PHONE_REG.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return
    }
    if (!CODE_REG.test(code)) {
      wx.showToast({ title: '请输入 6 位验证码', icon: 'none' })
      return
    }
    this.setData({ binding: true })
    try {
      const profile = await post('/user/phone', { phone, code })
      this.stopCountdown()
      this.setData({ profile, phone: '', smsCode: '', countdown: 0 })
      wx.showToast({ title: '绑定成功', icon: 'success' })
    } catch (err) {
      // 错误消息已由 request.js toast
    } finally {
      this.setData({ binding: false })
    }
  },

  startCountdown() {
    this.stopCountdown()
    this.setData({ countdown: RESEND_INTERVAL })
    this.timer = setInterval(() => {
      const next = this.data.countdown - 1
      if (next <= 0) {
        this.stopCountdown()
        this.setData({ countdown: 0 })
      } else {
        this.setData({ countdown: next })
      }
    }, 1000)
  },

  stopCountdown() {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
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
