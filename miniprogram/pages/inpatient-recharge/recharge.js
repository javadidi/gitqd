const app = getApp()
const { get, post } = require('../../utils/request')

// 住院充值（T23 卡片 657 行「住院充值：选择住院人，输入充值金额，支付。」/
// PRD 224-226 行「选择住院人员 → 住院充值 → 充值页面」）。
//
// 与门诊充值（T14）同一条流水线，三处必须不同的地方：
// 1. 主语是住院人，数据来自 GET /user/inpatients（T09），住院人没有删除入口，
//    所以这里不需要 T14 那句"刚选的人被删了就回退不选中"的兜底；
// 2. 金额输入用元、提交换算成分：Math.round 收口浮点乘法（19.99 * 100 在 IEEE754 里
//    不是 1999，直接发出去患者就少一分钱）——这条与 T14 完全同源；
// 3. 成功页与本页都不显示余额。inpatient 表没有余额列，PRD 也没有一句说住院充值会到账，
//    所以这里明确写"不影响到诊卡余额"，免得患者把它当成门诊那笔钱。
Page({
  data: {
    loading: false,
    inpatients: [],
    selectedInpatientId: null,
    selectedInpatientName: '',
    amountYuan: '',
    submitting: false,
  },

  onShow() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadInpatients()
  },

  async loadInpatients() {
    this.setData({ loading: true })
    try {
      const rows = await get('/user/inpatients')
      const picked = (rows || []).map((item) => ({
        id: item.id,
        name: item.name,
        inpatientNo: item.inpatientNo,
        department: item.department || '—',
        bedNo: item.bedNo || '—',
      }))
      const stillThere = picked.some((row) => row.id === this.data.selectedInpatientId)
      this.setData({
        inpatients: picked,
        selectedInpatientId: stillThere ? this.data.selectedInpatientId : null,
        selectedInpatientName: stillThere ? this.data.selectedInpatientName : '',
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onPickInpatient(e) {
    const id = Number(e.currentTarget.dataset.id)
    const found = this.data.inpatients.find((row) => row.id === id)
    this.setData({
      selectedInpatientId: id,
      selectedInpatientName: found ? found.name : '',
    })
  },

  onAmountInput(e) {
    this.setData({ amountYuan: e.detail.value })
  },

  onBindInpatient() {
    wx.navigateTo({ url: '/pages/inpatient/bind' })
  },

  onGoRecords() {
    wx.navigateTo({ url: '/pages/inpatient-recharge/records' })
  },

  onSubmit() {
    if (this.data.submitting) return
    if (!this.data.selectedInpatientId) {
      wx.showToast({ title: '请选择住院人', icon: 'none' })
      return
    }
    const yuan = parseFloat(this.data.amountYuan)
    if (!isFinite(yuan) || yuan <= 0) {
      wx.showToast({ title: '请输入大于 0 的充值金额', icon: 'none' })
      return
    }
    const amountFen = Math.round(yuan * 100)
    if (amountFen <= 0) {
      wx.showToast({ title: '金额太小，无法充值', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    post('/user/inpatient-recharges', {
      inpatientId: this.data.selectedInpatientId,
      amountFen,
    }).then((result) => {
      wx.redirectTo({
        url: [
          '/pages/inpatient-recharge/result',
          `?id=${result.id}`,
          `&orderNo=${encodeURIComponent(result.orderNo)}`,
          `&inpatientName=${encodeURIComponent(result.inpatientName || this.data.selectedInpatientName)}`,
          `&amountFen=${result.amountFen}`,
        ].join(''),
      })
    }).catch(() => {
      // 400（金额非正）/1005（住院人不是自己的）后端都给了 message，
      // utils/request.js 已 toast；这里只解除防重，让用户改完能再点
      this.setData({ submitting: false })
    })
  },
})
