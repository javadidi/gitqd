const app = getApp()
const { get } = require('../../utils/request')
const { relationLabel } = require('../../utils/format')

// 下一步要带走的排班上下文，从医生信息页一路传过来（选号是它的入口）。
// 放在 data 里而不是散在 onLoad 的参数上，是为了让"确认页需要哪些字段"在这一页就能看全。
Page({
  data: {
    scheduleId: null,
    doctorId: null,
    doctorName: '',
    departmentName: '',
    date: '',
    timeSlot: '',
    slotLabel: '',
    feeFen: null,
    patients: [],
    loading: false,
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.setData({
      scheduleId: options.scheduleId || null,
      doctorId: options.doctorId || null,
      doctorName: decodeURIComponent(options.doctorName || ''),
      departmentName: decodeURIComponent(options.departmentName || ''),
      date: options.date || '',
      timeSlot: options.timeSlot || '',
      slotLabel: decodeURIComponent(options.slotLabel || ''),
      feeFen: options.feeFen ? Number(options.feeFen) : null,
    })
  },

  onShow() {
    // 放在 onShow 而不是 onLoad：从空态去「添加就诊人」再返回时，必须自动看到新加的人，
    // 否则用户会停在一个已经过期的空列表上（T08 的就诊人列表页同此理）。
    this.loadPatients()
  },

  async loadPatients() {
    this.setData({ loading: true })
    try {
      const patients = await get('/user/patients')
      // 后端只回关系码值（SELF/CHILD/…），中文在前端翻译——与 T08 的就诊人列表同一套取舍。
      const picked = (patients || []).map((item) => ({
        id: item.id,
        name: item.name,
        cardNo: item.cardNo,
        phone: item.phone,
        relationLabel: relationLabel(item.relation),
      }))
      this.setData({ patients: picked })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onPick(e) {
    const patient = e.currentTarget.dataset.patient
    const query = [
      `scheduleId=${this.data.scheduleId}`,
      `patientId=${patient.id}`,
      `patientName=${encodeURIComponent(patient.name)}`,
      `relationLabel=${encodeURIComponent(patient.relationLabel || '')}`,
      `doctorId=${this.data.doctorId}`,
      `doctorName=${encodeURIComponent(this.data.doctorName)}`,
      `departmentName=${encodeURIComponent(this.data.departmentName)}`,
      `date=${this.data.date}`,
      `timeSlot=${this.data.timeSlot}`,
      `slotLabel=${encodeURIComponent(this.data.slotLabel)}`,
      `feeFen=${this.data.feeFen == null ? '' : this.data.feeFen}`,
    ].join('&')
    wx.navigateTo({ url: `/pages/appointment/confirm?${query}` })
  },

  onAddPatient() {
    wx.navigateTo({ url: '/pages/patient/edit' })
  },
})
