const app = getApp()
const { post } = require('../../utils/request')
const { formatMoney } = require('../../utils/format')

// 确认预约信息（PRD 80 行：展示就诊人、科室、医生、时间、费用等，确认提交）。
Page({
  data: {
    scheduleId: null,
    patientId: null,
    patientName: '',
    relationLabel: '',
    doctorName: '',
    departmentName: '',
    date: '',
    slotLabel: '',
    timeSlot: '',
    feeText: '—',
    submitting: false,
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    const feeFen = options.feeFen ? Number(options.feeFen) : null
    this.setData({
      scheduleId: options.scheduleId || null,
      patientId: options.patientId || null,
      patientName: decodeURIComponent(options.patientName || ''),
      relationLabel: decodeURIComponent(options.relationLabel || ''),
      doctorName: decodeURIComponent(options.doctorName || ''),
      departmentName: decodeURIComponent(options.departmentName || ''),
      date: options.date || '',
      timeSlot: options.timeSlot || '',
      slotLabel: decodeURIComponent(options.slotLabel || ''),
      // 这里显示的费用来自只读的排班接口，纯粹给患者看；
      // 真正入账的金额由服务端在创建时独立计算，接口响应回来后以响应为准（见 onSubmit）。
      feeText: formatMoney(feeFen),
    })
  },

  async onSubmit() {
    if (this.data.submitting) return
    this.setData({ submitting: true })
    try {
      const created = await post('/user/appointments', {
        patientId: Number(this.data.patientId),
        scheduleId: Number(this.data.scheduleId),
      })
      // 卡片 A 段第七步"发起微信支付"在真实环境里是 wx.requestPayment(payParams) + 微信异步回调；
      // 首版没有商户凭据（附录 A 二期），所以走服务端那个受控的模拟支付入口：
      // 它要求患者本人的 token 并校验这笔预约归属，内部与回调共用同一套状态推进逻辑，
      // 因此"下单 → 支付 → 已确认"这条链路验的是真代码。
      const paid = await post(`/user/appointments/${created.id}/pay`, {})
      wx.redirectTo({
        url: [
          '/pages/appointment/result',
          `?orderId=${created.id}`,
          `&orderNo=${encodeURIComponent(created.orderNo)}`,
          `&status=${encodeURIComponent(paid.status || created.status)}`,
          `&patientName=${encodeURIComponent(this.data.patientName)}`,
          `&doctorName=${encodeURIComponent(created.doctorName || this.data.doctorName)}`,
          `&departmentName=${encodeURIComponent(created.departmentName || this.data.departmentName)}`,
          `&appointmentTime=${encodeURIComponent(created.appointmentTime || '')}`,
          `&feeFen=${created.feeFen == null ? '' : created.feeFen}`,
        ].join(''),
      })
    } catch (err) {
      // 失败原因（号已满 2003 / 重复预约 2005 / 支付失败 3001）后端 message 里都有，
      // utils/request.js 已经 toast 过；这里只解除按钮的防重状态，让用户能重试或改选。
      this.setData({ submitting: false })
    }
  },

  onChangePatient() {
    wx.navigateBack()
  },
})
