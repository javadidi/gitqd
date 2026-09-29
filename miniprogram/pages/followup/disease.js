const app = getApp()
const { post } = require('../../utils/request')

// 选择疾病（T20 卡片 603 行「选择疾病：选择/填写疾病信息」/ PRD §3.6 第 190 行同一句）。
//
// 「选择」和「填写」两个动词都要有落点，而这一页最难的是"选择"的候选从哪来：
// 库里没有疾病字典表（V1 的 28 张表里没有任何一张存病种），PRD 575–596 行数据字典
// 也没有"疾病"这一行，seed 里更没有病名清单。所以候选项唯一的出处是
// 医生的「擅长」列（V1:89，seed.sql:60-64 形如「胃炎、胃食管反流、消化道息肉」）——
// 它是数据库里真实存在、且本来就是"这位医生看哪些病"的文字，不是我编的一份常用病清单。
// 按「、」切开当快捷项，点一下填进输入框，患者仍可自由改写（[[no-speculative-additions]]：
// 造一份"高血压/糖尿病/感冒"的假字典才是发明需求）。
//
// 上限 256 直接抄 V1:317 的 VARCHAR(256)：超了不截断（后端 @Size 也是当场拦下），
// 因为一条被悄悄改写的病史比拦下来更坏。
Page({
  data: {
    patientId: null,
    departmentId: null,
    doctorId: null,
    patientName: '',
    departmentName: '',
    doctorName: '',
    suggestions: [],
    disease: '',
    diseaseLength: 0,
    submitting: false,
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.setData({
      patientId: Number(options.patientId),
      departmentId: Number(options.departmentId),
      doctorId: Number(options.doctorId),
      patientName: decodeURIComponent(options.patientName || ''),
      departmentName: decodeURIComponent(options.departmentName || ''),
      doctorName: decodeURIComponent(options.doctorName || ''),
      suggestions: this.splitSpecialty(options.doctorSpecialty),
    })
  },

  splitSpecialty(raw) {
    const text = decodeURIComponent(raw || '')
    if (!text) return []
    // 只在「、」和逗号上切，不清洗、不排序、不造候选：
    // 切出来是什么，就是 seed 里这位医生的「擅长」写的是什么（seed.sql:60-64 用「、」分隔）。
    return text
      .split(/[、,，]/)
      .map((item) => item.trim())
      .filter((item) => item.length > 0)
      .slice(0, 6)
  },

  onPickSuggestion(e) {
    const name = e.currentTarget.dataset.name
    this.setData({ disease: name, diseaseLength: name.length })
  },

  onDiseaseInput(e) {
    const value = e.detail.value
    this.setData({ disease: value, diseaseLength: value.length })
  },

  onSubmit() {
    if (this.data.submitting) return
    const disease = (this.data.disease || '').trim()
    if (!disease) {
      wx.showToast({ title: '请填写复诊疾病信息', icon: 'none' })
      return
    }
    if (disease.length > 256) {
      wx.showToast({ title: '疾病信息不能超过 256 字', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    // 只提交四个字段：状态与"配了什么药"都不在这里，患者提交不了它们（见 FollowUpCreateRequest）
    post('/user/follow-ups', {
      patientId: this.data.patientId,
      departmentId: this.data.departmentId,
      doctorId: this.data.doctorId,
      disease,
    }).then((result) => {
      // 用 redirect 而不是 navigate：这条流程走完了，成功页不该再退回到申请表单
      wx.redirectTo({ url: '/pages/followup/result?id=' + result.followUpId })
    }).catch(() => {
      // 400（科室与医生不匹配）/5001（就诊人不是你的）的 message 后端都给了，
      // utils/request.js 已 toast；这里只解除防重，让用户改完能再点
      this.setData({ submitting: false })
    })
  },
})
