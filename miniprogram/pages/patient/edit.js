const app = getApp()
const { get, post, put } = require('../../utils/request')
const { RELATION_LABELS } = require('../../utils/format')

// 与后端 PatientCreateRequest 的两个正则保持一致；后端才是最终裁判，
// 这里拦一道只是为了不让用户提交完才看到错误
const PHONE_REG = /^1[3-9]\d{9}$/
const ID_CARD_REG = /^\d{17}[\dXx]$/

const RELATION_CODES = Object.keys(RELATION_LABELS)
const RELATION_NAMES = RELATION_CODES.map((code) => RELATION_LABELS[code])

Page({
  data: {
    id: null,
    isEdit: false,
    name: '',
    cardNo: '',
    idCard: '',
    phone: '',
    relationCodes: RELATION_CODES,
    relationNames: RELATION_NAMES,
    relationIndex: 0,
    // 编辑态展示用：后端只回打码值，明文拿不到，所以输入框留空表示不改
    idCardMasked: '',
    phoneMasked: '',
    submitting: false,
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    if (options && options.id) {
      wx.setNavigationBarTitle({ title: '编辑就诊人' })
      this.setData({ id: Number(options.id), isEdit: true })
      this.loadPatient(options.id)
    } else {
      wx.setNavigationBarTitle({ title: '添加就诊人' })
    }
  },

  async loadPatient(id) {
    try {
      const detail = await get(`/user/patients/${id}`)
      const index = RELATION_CODES.indexOf(detail.relation)
      this.setData({
        name: detail.name || '',
        cardNo: detail.cardNo || '',
        relationIndex: index < 0 ? 0 : index,
        idCardMasked: detail.idCard || '',
        phoneMasked: detail.phone || '',
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    }
  },

  onNameInput(e) {
    this.setData({ name: e.detail.value })
  },

  onCardNoInput(e) {
    this.setData({ cardNo: e.detail.value })
  },

  onIdCardInput(e) {
    this.setData({ idCard: e.detail.value })
  },

  onPhoneInput(e) {
    this.setData({ phone: e.detail.value })
  },

  onRelationChange(e) {
    this.setData({ relationIndex: Number(e.detail.value) })
  },

  onSubmit() {
    if (this.data.submitting) return

    const { isEdit, id, name, cardNo, idCard, phone, relationCodes, relationIndex } = this.data
    const trimmedName = (name || '').trim()
    const trimmedCardNo = (cardNo || '').trim()

    if (!trimmedName) {
      return wx.showToast({ title: '请填写姓名', icon: 'none' })
    }
    if (!trimmedCardNo) {
      return wx.showToast({ title: '请填写就诊卡号', icon: 'none' })
    }
    // 新增时身份证/手机号必填；编辑时留空表示不改（后端只回打码值，前端拿不到明文预填）
    if (!isEdit && !(idCard || '').trim()) {
      return wx.showToast({ title: '请填写身份证号', icon: 'none' })
    }
    if (!isEdit && !(phone || '').trim()) {
      return wx.showToast({ title: '请填写手机号', icon: 'none' })
    }
    if ((idCard || '').trim() && !ID_CARD_REG.test(idCard.trim())) {
      return wx.showToast({ title: '身份证号格式不正确', icon: 'none' })
    }
    if ((phone || '').trim() && !PHONE_REG.test(phone.trim())) {
      return wx.showToast({ title: '手机号格式不正确', icon: 'none' })
    }

    const payload = {
      name: trimmedName,
      cardNo: trimmedCardNo,
      idCard: (idCard || '').trim(),
      phone: (phone || '').trim(),
      relation: relationCodes[relationIndex],
    }

    this.setData({ submitting: true })
    const request = isEdit ? put(`/user/patients/${id}`, payload) : post('/user/patients', payload)
    request
      .then(() => {
        wx.showToast({ title: isEdit ? '已保存' : '已添加', icon: 'success' })
        // 稍等一下让 toast 看得见，再回列表页（列表页 onShow 会重新拉数据）
        setTimeout(() => wx.navigateBack(), 600)
      })
      .catch(() => {
        // 后端错误消息（含"就诊卡号已存在"）已由 utils/request.js 统一 toast
      })
      .then(() => {
        this.setData({ submitting: false })
      })
  },
})
