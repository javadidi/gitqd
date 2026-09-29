const app = getApp()
const { post } = require('../../utils/request')

// 体检须知（T22 卡片 641 行「体检须知：展示体检注意事项」/ PRD §3.8 第 214 行同一句），
// 也是整条流程真正提交的地方（PRD 把须知排在确认之后、成功之前，所以 POST 落在这一步）。
//
// 这一页最难的是"注意事项"的内容从哪来。逐字找过一遍，全仓没有任何出处：
// 库里没有须知表（announcement 的 type 只有 NOTICE/ACTIVITY，V1:348，是公告表不是须知表），
// PRD 575–596 行数据字典没有"须知"这一项，§4 后台也没有"体检须知管理"页
// （只有 §4.5.3 套餐管理与 §4.5.4 项目管理，406–413 行）。
//
// 所以本页只写三类有出处的句子，一条医学建议都不编：
// ① 本应用自己的导航事实（报告在哪看、状态是什么）；
// ② 红线本身（卡片 644 行「不做真实体检（二期做）；首版仅模拟流程」）；
// ③ 一句话说明"注意事项条款暂无出处、由医院维护后展示"。
// "体检前需空腹 8 小时""请携带身份证"这类看着像常识的条款，写上去就是编造医疗指引
// ——与 T12 预约须知页同一条纪律（见 pages/appointment/notice.js 的注释）。
Page({
  data: {
    packageId: null,
    patientId: null,
    patientName: '',
    packageName: '',
    priceText: '',
    appointmentDate: '',
    submitting: false,
    rules: [
      { title: '首版为预约流程演示，不含实际体检', desc: '提交后不会安排真实体检，也不产生扣款' },
      { title: '提交后本单状态为「待确认」', desc: '当前版本状态不会自动变化，可在预约记录里查看' },
      { title: '体检报告在个人中心查看', desc: '「我的 - 体检预约记录」内可进入体检报告列表' },
      { title: '体检注意事项', desc: '具体条款由医院维护，本版本暂无可展示内容' },
    ],
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.setData({
      packageId: options.packageId,
      patientId: options.patientId,
      patientName: decodeURIComponent(options.patientName || ''),
      packageName: decodeURIComponent(options.packageName || ''),
      priceText: decodeURIComponent(options.priceText || ''),
      appointmentDate: options.appointmentDate || '',
    })
  },

  onSubmit() {
    if (this.data.submitting) return
    if (!this.data.patientId || !this.data.packageId || !this.data.appointmentDate) {
      wx.showToast({ title: '预约信息不完整，请返回重填', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    // 只提交三个字段：费用与状态都不在这里，患者提交不了它们（见 PhysicalAppointmentCreateRequest）
    post('/user/physical-appointments', {
      patientId: Number(this.data.patientId),
      packageId: Number(this.data.packageId),
      appointmentDate: this.data.appointmentDate,
    }).then((result) => {
      wx.redirectTo({ url: '/pages/physical/result?id=' + result.appointmentId })
    }).catch(() => {
      // 400（日期是过去的）/5001（体检人或套餐不对）的 message 后端都给了，
      // utils/request.js 已 toast；这里只解除防重，让用户改完能再点
      this.setData({ submitting: false })
    })
  },

  onBackEdit() {
    wx.navigateBack()
  },
})
