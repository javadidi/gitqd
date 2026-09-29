const app = getApp()
const { get } = require('../../utils/request')
const { relationLabel } = require('../../utils/format')

// 核酸检测申请（T21 卡片 619 行「选择就诊人」+ 620 行「核酸检测申请：填写检测信息」；
// PRD §3.7 第 199–200 行同两步，§6.1 第 521 行把「选择就诊人」「核酸检测申请」列成两个页面名）。
//
// 两处刻意的设计：
// 1. 「检测信息」只有就诊人和检测日期两项 —— 不是偷懒，是这张表只收这两样：
//    V1:296-307 的业务列是 order_no/patient_id/appointment_date/status/report，
//    单号与状态由服务端定，报告由检测侧回填，患者能填的就只有前两项（推导见 NucleicCreateRequest）。
//    加一个「症状描述」或「人群类别」输入框，落库时没有任何一列接得住，那才是假表单。
// 2. 日期用 picker 的 date 模式，start 就是今天 —— 与后端 @FutureOrPresent 同一条下限，
//    上限刻意不设（规格从没给过"最多约几天内"）。
Page({
  data: {
    loadingPatients: false,
    patients: [],
    patientId: null,
    patientName: '',
    appointmentDate: '',
    today: '',
  },

  onLoad() {
    // 给 picker 的 start 用。取本地时区的今天，与后端 LocalDate.now() 同一口径
    // （服务端仍会自己判一次，前端这条只是少让人白填）。
    const now = new Date()
    const month = String(now.getMonth() + 1).padStart(2, '0')
    const day = String(now.getDate()).padStart(2, '0')
    this.setData({ today: now.getFullYear() + '-' + month + '-' + day })
  },

  onShow() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadPatients()
  },

  async loadPatients() {
    this.setData({ loadingPatients: true })
    try {
      const rows = await get('/user/patients')
      const picked = (rows || []).map((item) => ({
        id: item.id,
        name: item.name,
        cardNo: item.cardNo,
        relationLabel: relationLabel(item.relation),
      }))
      const stillThere = picked.some((p) => p.id === this.data.patientId)
      this.setData({
        patients: picked,
        // 刚选的人如果被删了（T08 有删除入口），回退到不选中而不是指向一个不存在的 id
        patientId: stillThere ? this.data.patientId : null,
        patientName: stillThere ? this.data.patientName : '',
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loadingPatients: false })
    }
  },

  onPickPatient(e) {
    const id = Number(e.currentTarget.dataset.id)
    const found = this.data.patients.find((p) => p.id === id)
    this.setData({ patientId: id, patientName: found ? found.name : '' })
  },

  onAddPatient() {
    wx.navigateTo({ url: '/pages/patient/edit' })
  },

  onDateChange(e) {
    this.setData({ appointmentDate: e.detail.value })
  },

  onNext() {
    if (!this.data.patientId) {
      wx.showToast({ title: '请选择就诊人', icon: 'none' })
      return
    }
    if (!this.data.appointmentDate) {
      wx.showToast({ title: '请选择检测日期', icon: 'none' })
      return
    }
    // 确认页只带 id 与日期；名字是展示用的，提交后一律以服务端按 id 解析的结果为准
    const query = [
      `patientId=${this.data.patientId}`,
      `patientName=${encodeURIComponent(this.data.patientName)}`,
      `appointmentDate=${this.data.appointmentDate}`,
    ].join('&')
    wx.navigateTo({ url: `/pages/nucleic/confirm?${query}` })
  },
})
