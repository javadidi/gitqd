const app = getApp()
const { get, post } = require('../../utils/request')

// 病案配送申请信息（T23 卡片 661 行 / PRD 241 行「申请信息 — 填写邮寄申请信息（收件人、地址等）」）。
//
// PRD 243 行还有一页「病历信息 — 确认病历信息」，这里不单独成页而是并成上方那一块：
// case_delivery（V1:328-339）指向病案的唯一方式就是 inpatient_id，
// 表里没有任何指向病历的列，medical_record 又只按 patient_id（就诊人）关联，
// 而住院人与就诊人之间没有任何外键或对应列（V1:41-53）。
// 所以"确认病历信息"在本系统能确认的极限就是"哪位住院人、哪个住院号"，
// 另起一页去列病历内容就是替数据模型编关系。
//
// PRD 242 行「证件上传」与 244 行「支付页面」两页首版不做：
// 前者没有上传通道（后端零 MultipartFile、小程序零 wx.uploadFile），
// 后者没有落点（表无费用列、payment_record 要求 patient_id 而住院人没有就诊人关联）。
// 判断与证据记在 WORK_LOG 的 T23 段。
Page({
  data: {
    loading: false,
    inpatients: [],
    selectedInpatientId: null,
    selectedInpatientName: '',
    selectedInpatientNo: '',
    recipientName: '',
    address: '',
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
        selectedInpatientNo: stillThere ? this.data.selectedInpatientNo : '',
      })
    } catch (err) {
      // request.js 已统一 toast
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
      selectedInpatientNo: found ? found.inpatientNo : '',
    })
  },

  onBindInpatient() {
    wx.navigateTo({ url: '/pages/inpatient/bind' })
  },

  onRecipientInput(e) {
    this.setData({ recipientName: e.detail.value })
  },

  onAddressInput(e) {
    this.setData({ address: e.detail.value })
  },

  onSubmit() {
    if (this.data.submitting) return
    if (!this.data.selectedInpatientId) {
      wx.showToast({ title: '请选择住院人', icon: 'none' })
      return
    }
    const recipientName = (this.data.recipientName || '').trim()
    if (!recipientName) {
      wx.showToast({ title: '请填写收件人', icon: 'none' })
      return
    }
    const address = (this.data.address || '').trim()
    if (!address) {
      wx.showToast({ title: '请填写收件地址', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    post('/user/case-deliveries', {
      inpatientId: this.data.selectedInpatientId,
      recipientName,
      address,
    }).then((result) => {
      // 只带 id 过去，成功页再回读一次详情：这样"申请成功"这句话是库里那一行给的，
      // 不是提交响应里的内存对象给的
      wx.redirectTo({ url: `/pages/case-delivery/result?id=${result.id}` })
    }).catch(() => {
      this.setData({ submitting: false })
    })
  },
})
