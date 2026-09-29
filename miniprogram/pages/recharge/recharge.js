const app = getApp()
const { get, post } = require('../../utils/request')
const { formatMoney, relationLabel } = require('../../utils/format')

// 门诊充值（T14 卡片 494 行 / PRD 94 行「选择就诊人，输入充值金额，选择支付方式（微信支付）」）。
//
// 三处刻意的设计：
// 1. 金额输入用「元」，提交换算成「分」——后端 amountFen 只收整数分，
//    元转分是浮点乘法，必须 Math.round 收口，否则 19.99 * 100 = 1998.9999999…
//    会被截成 1998，患者充 19.99 元到账 19.98，这种账平不了。
// 2. 支付方式不是选择器而是一行固定文案「微信支付」——卡片括号写死了微信支付，
//    后端也不接受 payMethod 入参（RechargeCreateRequest 注释里有推导）；
//    做一个只有一个选项的假选择器，等于骗一次点击。
// 3. 就诊人列表用 onShow 拉而不是 onLoad——从「添加就诊人」回来要立刻看到新人（T08 起的惯例）。
Page({
  data: {
    loading: false,
    patients: [],
    selectedPatientId: null,
    selectedPatientName: '',
    amountYuan: '',
    submitting: false,
  },

  onShow() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadPatients()
  },

  async loadPatients() {
    this.setData({ loading: true })
    try {
      const patients = await get('/user/patients')
      const picked = (patients || []).map((item) => ({
        id: item.id,
        name: item.name,
        cardNo: item.cardNo,
        relationLabel: relationLabel(item.relation),
      }))
      const stillThere = picked.some((p) => p.id === this.data.selectedPatientId)
      this.setData({
        patients: picked,
        // 刚选的人如果被删了（T08 有删除入口），回退到不选中而不是指向一个不存在的 id
        selectedPatientId: stillThere ? this.data.selectedPatientId : null,
        selectedPatientName: stillThere ? this.data.selectedPatientName : '',
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onPickPatient(e) {
    const id = Number(e.currentTarget.dataset.id)
    const found = this.data.patients.find((p) => p.id === id)
    this.setData({
      selectedPatientId: id,
      selectedPatientName: found ? found.name : '',
    })
  },

  onAmountInput(e) {
    this.setData({ amountYuan: e.detail.value })
  },

  onAddPatient() {
    wx.navigateTo({ url: '/pages/patient/edit' })
  },

  onSubmit() {
    if (this.data.submitting) return
    if (!this.data.selectedPatientId) {
      wx.showToast({ title: '请选择就诊人', icon: 'none' })
      return
    }
    const yuan = parseFloat(this.data.amountYuan)
    if (!isFinite(yuan) || yuan <= 0) {
      wx.showToast({ title: '请输入大于 0 的充值金额', icon: 'none' })
      return
    }
    // 元 → 分：浮点乘法必须收口。19.99 * 100 在 IEEE754 里不是 1999，
    // 直接发出去后端收到的就是 1998（分），患者看起来少了一分钱。
    const amountFen = Math.round(yuan * 100)
    if (amountFen <= 0) {
      wx.showToast({ title: '金额太小，无法充值', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    post('/user/recharges', {
      patientId: this.data.selectedPatientId,
      amountFen,
    }).then((result) => {
      wx.redirectTo({
        url: [
          '/pages/recharge/result',
          `?orderNo=${encodeURIComponent(result.orderNo)}`,
          `&patientName=${encodeURIComponent(result.patientName || this.data.selectedPatientName)}`,
          `&amountFen=${result.amountFen}`,
          `&balanceFen=${result.balanceFen == null ? '' : result.balanceFen}`,
        ].join(''),
      })
    }).catch(() => {
      // 400（金额非正）/1003（就诊人不是自己的）的 message 后端都给了，
      // utils/request.js 已 toast；这里只解除防重，让用户改完能再点
      this.setData({ submitting: false })
    })
  },
})
